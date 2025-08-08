package net.dima.project.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.domain.PageImpl; // [✅ import 추가]


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.BidderDto;         // import 추가
import net.dima.project.dto.MyPostedRequestDto; // import 추가
import net.dima.project.entity.ContainerCargoEntity;
import net.dima.project.entity.ContainerStatus;
import net.dima.project.entity.NotificationEvents;
import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.OfferStatus;
import net.dima.project.entity.RequestEntity;
import net.dima.project.entity.RequestStatus;
import net.dima.project.entity.UserEntity;
import net.dima.project.repository.ContainerCargoRepository; // import 추가
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.UserRepository;

import java.util.ArrayList; // [✅ import 추가]
import jakarta.persistence.criteria.Predicate; // [✅ import 추가]
import jakarta.persistence.criteria.Root; // [✅ import 추가]
import org.springframework.context.ApplicationEventPublisher; 

@Service
@RequiredArgsConstructor
@Transactional
public class ResaleService {

    private final OfferRepository offerRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerCargoRepository containerCargoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatService chatService;

    @Transactional
    public void createResaleRequest(Long offerId, String currentUserId) {
        OfferEntity originalOffer = offerRepository.findByIdWithDetails(offerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 제안입니다."));
        UserEntity forwarder = userRepository.findByUserId(currentUserId);

        if (originalOffer.getStatus() != OfferStatus.ACCEPTED) {
            throw new IllegalStateException("'수락(ACCEPTED)' 상태의 제안만 재판매할 수 있습니다.");
        }
        if (originalOffer.getContainer().getStatus() != ContainerStatus.SCHEDULED) {
            throw new IllegalStateException("컨테이너가 이미 확정 또는 운송 시작되어 재판매할 수 없습니다.");
        }
        if (!originalOffer.getForwarder().getUserSeq().equals(forwarder.getUserSeq())) {
            throw new SecurityException("자신의 제안만 재판매할 수 있습니다.");
        }

        originalOffer.setStatus(OfferStatus.FOR_SALE); // 원본 제안 상태 변경

        RequestEntity resaleRequest = RequestEntity.builder()
                .cargo(originalOffer.getRequest().getCargo())
                .requester(forwarder)
                .departurePort(originalOffer.getRequest().getDeparturePort())
                .arrivalPort(originalOffer.getRequest().getArrivalPort())
                .deadline(originalOffer.getRequest().getDeadline())
                .desiredArrivalDate(originalOffer.getRequest().getDesiredArrivalDate())
                .tradeType(originalOffer.getRequest().getTradeType())
                .transportType(originalOffer.getRequest().getTransportType())
                .status(RequestStatus.OPEN)
                .sourceOffer(originalOffer)
                .build();
        requestRepository.save(resaleRequest);
    }
    
    /**
     * [✅ 핵심 수정] 재판매 요청을 수동으로 '취소'하는 기능입니다.
     * 내부적으로 revertResaleRequest 공통 로직을 호출합니다.
     */
    @Transactional
    public void cancelResaleRequest(Long requestId, String currentUserId) {
        RequestEntity resaleRequest = requestRepository.findRequestWithDetailsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재판매 요청입니다: " + requestId));

        if (!resaleRequest.getRequester().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신이 등록한 재판매 요청만 취소할 수 있습니다.");
        }
        if (resaleRequest.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("진행 중인 재판매 요청만 취소할 수 있습니다.");
        }

        revertResaleRequest(resaleRequest); // 아래에 정의된 공통 취소/마감 로직 호출
    }

    
    /**
     * [✅ 핵심 추가] 재판매를 되돌리는 공통 로직입니다. (취소 또는 자동 마감 시 사용)
     * 1. 원본 제안 상태를 'ACCEPTED'로 복원
     * 2. 재판매 요청에 달린 모든 입찰을 'REJECTED' 처리
     * 3. 재판매 요청 자체를 'CLOSED' 처리
     */
    public void revertResaleRequest(RequestEntity resaleRequest) {
        OfferEntity originalOffer = resaleRequest.getSourceOffer();
        if (originalOffer == null) {
            // 이럴 가능성은 거의 없지만, 방어 코드를 추가합니다.
            throw new IllegalStateException("원본 제안이 없는 재판매 요청입니다.");
        }
        
        // 1. 나의 원본 제안 상태를 'ACCEPTED'(수락)로 되돌립니다.
        originalOffer.setStatus(OfferStatus.ACCEPTED);

        // 2. 이 재판매 요청에 달린 모든 입찰(Offer)들의 상태를 'REJECTED'(거절)로 변경합니다.
        List<OfferEntity> bidsToCancel = offerRepository.findAllByRequest(resaleRequest);
        bidsToCancel.forEach(bid -> bid.setStatus(OfferStatus.REJECTED));

        // 3. 재판매 요청 자체의 상태를 'CLOSED'로 변경하여 목록에서 숨깁니다.
        resaleRequest.setStatus(RequestStatus.CLOSED);
    }
    
    
    // [✅ 추가] 나의 재판매 요청 목록 조회
    // [✅ 이 메서드 전체를 아래 코드로 교체해주세요]
    /**
     * [✅ 핵심 수정] '나의요청조회(재판매 관리)' 페이지의 목록 조회 로직
     * 'OPEN' 상태인 재판매 요청과, 'CLOSED'이지만 최종 운송 책임이 나에게 있는 건들을 함께 조회합니다.
     */
    @Transactional(readOnly = true)
    public Page<MyPostedRequestDto> getMyPostedRequests(String currentUserId, String status, Pageable pageable) {
        UserEntity requester = userRepository.findByUserId(currentUserId);
        
        // 1. DB에서 내가 올린 모든 재판매 요청을 가져옵니다. (정렬 적용)
        List<RequestEntity> allMyResaleRequests = requestRepository.findAllByRequesterAndSourceOfferIsNotNull(requester, pageable.getSort());

        // 2. DTO로 변환합니다.
        List<MyPostedRequestDto> dtoList = allMyResaleRequests.stream()
            .map(req -> {
                if (req.getStatus() == RequestStatus.OPEN) {
                    // 재판매 입찰이 진행 중인 경우
                    long bidderCount = offerRepository.countByRequest(req);
                    return MyPostedRequestDto.fromEntity(req, bidderCount);
                } else { // CLOSED 상태인 경우
                    Optional<OfferEntity> winningOfferOpt = offerRepository.findWinningOfferForRequest(req);
                    
                    // ★★★ 핵심: 낙찰자가 없는 마감된 재판매 건은 DTO로 변환하지 않고 건너뜁니다.
                    if (winningOfferOpt.isEmpty()) {
                        return null; 
                    }

                    MyPostedRequestDto dto = MyPostedRequestDto.fromEntity(req, winningOfferOpt);
                    findFinalOffer(req).ifPresent(finalOffer -> dto.setImoNumber(finalOffer.getContainer().getImoNumber()));
                    return dto;
                }
            })
            // ★★★ 핵심: 변환 결과가 null인 (낙찰자 없는 마감 건) 항목을 리스트에서 최종 제거합니다.
            .filter(dto -> dto != null)
            .collect(Collectors.toList());

        // 3. 상태(status) 탭 필터링 로직 (기존과 동일)
        List<MyPostedRequestDto> filteredList;
        if (status != null && !status.isEmpty()) {
            filteredList = dtoList.stream()
                    .filter(dto -> {
                        if ("OPEN".equalsIgnoreCase(status)) {
                            return "OPEN".equals(dto.getStatus());
                        }
                        return dto.getDetailedStatus() != null && status.equalsIgnoreCase(dto.getDetailedStatus());
                    })
                    .collect(Collectors.toList());
        } else {
            filteredList = dtoList;
        }

        // 4. 최종 목록으로 페이지네이션 객체를 만듭니다. (기존과 동일)
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredList.size());
        List<MyPostedRequestDto> pageContent = (start > end) ? List.of() : filteredList.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, filteredList.size());
    }


    // [✅ 추가] 특정 재판매 요청에 대한 입찰자 목록 조회
    @Transactional(readOnly = true)
    public List<BidderDto> getBiddersForRequest(Long requestId, String currentUserId) {
        RequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다."));
        if (!request.getRequester().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신의 요청에 대한 입찰자만 조회할 수 있습니다.");
        }
        return offerRepository.findAllByRequest(request).stream()
                .map(BidderDto::fromEntity)
                .collect(Collectors.toList());
    }

    // [✅ 추가] 입찰 확정 (가장 핵심적인 로직)
    @Transactional
    public void confirmBid(Long requestId, Long winningOfferId, String currentUserId) {
        // 1~4단계: 상태 변경 (기존 로직과 동일)
        RequestEntity resaleRequest = requestRepository.findRequestWithDetailsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다."));
        if (!resaleRequest.getRequester().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신의 요청에 대해서만 확정할 수 있습니다.");
        }
        if (resaleRequest.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("이미 마감된 요청입니다.");
        }

        List<OfferEntity> allBids = offerRepository.findAllByRequest(resaleRequest);
        OfferEntity winningOffer = allBids.stream()
                .filter(o -> o.getOfferId().equals(winningOfferId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("선택한 입찰이 존재하지 않습니다."));

        allBids.forEach(bid -> bid.setStatus(bid.equals(winningOffer) ? OfferStatus.ACCEPTED : OfferStatus.REJECTED));
        resaleRequest.setStatus(RequestStatus.CLOSED);

        OfferEntity originalOffer = resaleRequest.getSourceOffer();
        originalOffer.setStatus(OfferStatus.RESOLD);
        
        // [✅ 아래 코드 추가]
        eventPublisher.publishEvent(new NotificationEvents.OfferConfirmedEvent(this, allBids, winningOffer));
        
        chatService.createChatRoomForOffer(winningOffer);

        // [✅ 5. 핵심 수정] 나의 컨테이너에서 화물(CBM) 제거 로직 보강
        // findByOfferOfferId로 데이터를 찾되, 만약 없더라도 오류를 발생시키지 않고 넘어갑니다.=
        // [✅ 수정] 나의 컨테이너에서 화물(CBM) 제거 로직 보강
        containerCargoRepository.findByOfferOfferId(originalOffer.getOfferId())
                .ifPresent(containerCargoRepository::delete);
        
        // [✅ 6. 핵심 수정] 낙찰자 컨테이너에 화물(CBM) 추가 로직 강화
        // 혹시 모를 중복 생성을 방지하기 위해, 먼저 데이터가 있는지 확인합니다.
        // [✅ 수정] 낙찰자 컨테이너에 화물(CBM) 추가 로직 강화
        containerCargoRepository.findByOfferOfferId(winningOffer.getOfferId()).orElseGet(() -> {
            ContainerCargoEntity newCargo = ContainerCargoEntity.builder()
                    .container(winningOffer.getContainer())
                    .offer(winningOffer)
                    .cbmLoaded(resaleRequest.getCargo().getTotalCbm())
                    .isExternal(false)
                    .build();
            return containerCargoRepository.save(newCargo);
        });
    }
    
    
 // ResaleService.java 파일 맨 아래에 이 메서드를 추가해주세요. (imo때매 추가)
    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(request).stream()
                .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                .findFirst();

        if (winningOfferOpt.isPresent()) {
            OfferEntity winningOffer = winningOfferOpt.get();
            if (winningOffer.getStatus() == OfferStatus.RESOLD) {
                List<RequestEntity> nextRequests = requestRepository.findBySourceOfferOrderedByCreatedAtDesc(winningOffer);
                if (!nextRequests.isEmpty()) {
                    return findFinalOffer(nextRequests.get(0));
                }
            } else {
                return winningOfferOpt;
            }
        }
        return Optional.empty();
    }
}