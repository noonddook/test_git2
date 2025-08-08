// [✅ RequestService.java 파일 전체를 이 코드로 교체해주세요]
package net.dima.project.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import net.dima.project.dto.MyPostedRequestDto;
import net.dima.project.dto.NewRequestDto;
import net.dima.project.dto.RequestCardDto;
import net.dima.project.entity.*;
import net.dima.project.repository.CargoRepository;
import net.dima.project.repository.ContainerCargoRepository;
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestService {

    private final RequestRepository requestRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final CargoRepository cargoRepository;
    private final ContainerCargoRepository containerCargoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatService chatService;

    public Page<RequestCardDto> getRequests(
            boolean excludeClosed,
            String tradeType, String transportType,
            String departurePort, String arrivalPort,
            String itemName, Pageable pageable, String currentUserId) {

        Specification<RequestEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            if (excludeClosed) {
                predicates.add(cb.equal(root.get("status"), RequestStatus.OPEN));
                predicates.add(cb.greaterThan(root.get("deadline"), now));
            }
            if (tradeType != null && !tradeType.isEmpty()) {
                predicates.add(cb.equal(root.get("tradeType"), tradeType));
            }
            if (transportType != null && !transportType.isEmpty()) {
                predicates.add(cb.equal(root.get("transportType"), transportType));
            }
            if (departurePort != null && !departurePort.isEmpty()) {
                predicates.add(cb.equal(root.get("departurePort"), departurePort));
            }
            if (arrivalPort != null && !arrivalPort.isEmpty()) {
                predicates.add(cb.equal(root.get("arrivalPort"), arrivalPort));
            }
            if (itemName != null && !itemName.isBlank()) {
                Join<RequestEntity, CargoEntity> cargoJoin = root.join("cargo");
                predicates.add(cb.like(cargoJoin.get("itemName"), "%" + itemName + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RequestEntity> requestPage = requestRepository.findAll(spec, pageable);
        Set<Long> offeredRequestIds = offerRepository.findOfferedRequestIdsByUserId(currentUserId);
        return requestPage.map(entity -> {
            boolean hasMyOffer = offeredRequestIds.contains(entity.getRequestId());
            return RequestCardDto.fromEntity(entity, hasMyOffer);
        });
    }

    // ★★★ 핵심: 중복 메서드를 하나로 합치고, excludeClosed 파라미터를 추가했습니다 ★★★
    // ▼▼▼ getRequestsForShipper 메서드 전체를 이 코드로 교체해주세요 ▼▼▼
    // ▼▼▼ getRequestsForShipper 메서드 전체를 이 코드로 교체해주세요 ▼▼▼
    @Transactional(readOnly = true)
    public Page<MyPostedRequestDto> getRequestsForShipper(String currentUserId, String status, boolean excludeClosed, String itemName, Pageable pageable) {
        UserEntity shipper = userRepository.findByUserId(currentUserId);
        LocalDateTime now = LocalDateTime.now();

        // 1. DB에서 필터링할 초기 후보군을 조회합니다. (여기서는 페이징을 적용하지 않습니다)
        Specification<RequestEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("requester"), shipper));
            predicates.add(cb.isNull(root.get("sourceOffer")));
            if (itemName != null && !itemName.isBlank()) {
                Join<RequestEntity, CargoEntity> cargoJoin = root.join("cargo");
                predicates.add(cb.like(cargoJoin.get("itemName"), "%" + itemName + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<RequestEntity> allRequests = requestRepository.findAll(spec, pageable.getSort());

        // 2. Stream을 사용하여 메모리에서 상세 필터링을 수행합니다.
        Stream<RequestEntity> requestStream = allRequests.stream();

        // 2-1. '운송중인 화물' 탭인 경우 (status="CLOSED" 또는 status=null)
        if (status == null || "CLOSED".equalsIgnoreCase(status)) {
            requestStream = requestStream.filter(req -> {
                if (req.getStatus() == RequestStatus.CLOSED) {
                    // 최종 운송 상태를 확인
                    Optional<OfferEntity> finalOfferOpt = findFinalOffer(req);
                    // 최종 제안이 존재하고, 그 컨테이너 상태가 '정산완료'가 아닐 때만 목록에 포함
                    return finalOfferOpt.map(o -> o.getContainer().getStatus() != ContainerStatus.SETTLED).orElse(true);
                }
                return false; // OPEN 상태의 요청은 이 탭에 표시하지 않음
            });
        }
        // 2-2. '나의요청 관리' 탭인 경우 (status="OPEN")
        else if ("OPEN".equalsIgnoreCase(status)) {
            requestStream = requestStream.filter(req -> req.getStatus() == RequestStatus.OPEN);
            if (excludeClosed) {
                requestStream = requestStream.filter(req -> req.getDeadline().isAfter(now));
            }
        }
        
        List<MyPostedRequestDto> dtoList = requestStream.map(req -> {
            if (req.getStatus() == RequestStatus.OPEN) {
                long bidderCount = offerRepository.countByRequest(req);
                return MyPostedRequestDto.fromEntity(req, bidderCount);
            } else { // CLOSED
                Optional<OfferEntity> directWinningOfferOpt = offerRepository.findWinningOfferForRequest(req);
                Optional<OfferEntity> finalOfferInChainOpt = findFinalOffer(req);
                return MyPostedRequestDto.fromEntity(req, directWinningOfferOpt, finalOfferInChainOpt);
            }
        }).collect(Collectors.toList());
        
        // 3. 필터링된 최종 리스트를 기준으로 수동으로 페이징 처리합니다.
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), dtoList.size());
        List<MyPostedRequestDto> pageContent = (start >= dtoList.size()) ? Collections.emptyList() : dtoList.subList(start, end);

        return new PageImpl<>(pageContent, pageable, dtoList.size());
    }
    
    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        RequestEntity currentRequest = request;
        
        // 재판매 체인의 끝을 찾을 때까지 무한 반복합니다.
        while (true) {
            // 현재 요청(currentRequest)에 대한 낙찰자를 찾습니다.
            Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(currentRequest)
                    .stream()
                    .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                    .findFirst();

            if (winningOfferOpt.isPresent()) {
                OfferEntity winningOffer = winningOfferOpt.get();
                // 만약 낙찰된 제안의 상태가 '재판매 완료(RESOLD)'라면, 아직 최종 운송사가 아니라는 의미입니다.
                if (winningOffer.getStatus() == OfferStatus.RESOLD) {
                    // 이 '재판매 완료' 제안(winningOffer)을 통해 생성된 다음 재판매 요청을 찾습니다.
                    List<RequestEntity> nextRequests = requestRepository.findBySourceOfferOrderedByCreatedAtDesc(winningOffer);
                    if (!nextRequests.isEmpty()) {
                        // 다음 재판매 요청을 새로운 currentRequest로 설정하고 루프를 계속합니다.
                        currentRequest = nextRequests.get(0);
                    } else {
                        // 다음 재판매 요청을 찾을 수 없다면, 현재까지 찾은 낙찰자가 마지막이므로 반환합니다.
                        return winningOfferOpt;
                    }
                } else {
                    // 상태가 'RESOLD'가 아니라면(예: ACCEPTED, CONFIRMED 등), 이 제안이 최종 운송사이므로 반환합니다.
                    return winningOfferOpt;
                }
            } else {
                // 현재 요청에 대한 낙찰자가 없으면, 추적을 중단하고 빈 결과를 반환합니다.
                return Optional.empty();
            }
        }
    }
    
    @Transactional
    public void createNewRequest(NewRequestDto dto, String currentUserId) {
        UserEntity requester = userRepository.findByUserId(currentUserId);
        CargoEntity newCargo = CargoEntity.builder()
                .owner(requester)
                .itemName(dto.getItemName())
                .incoterms(dto.getIncoterms())
                .totalCbm(dto.getTotalCbm())
                .isDangerous(dto.getIsDangerous())
                .build();
        cargoRepository.save(newCargo);

        RequestEntity newRequest = RequestEntity.builder()
                .cargo(newCargo)
                .requester(requester)
                .departurePort(dto.getDeparturePort())
                .arrivalPort(dto.getArrivalPort())
                .deadline(dto.getDeadline())
                .desiredArrivalDate(dto.getDesiredArrivalDate()) 
                .tradeType(dto.getTradeType())
                .transportType(dto.getTransportType())
                .status(RequestStatus.OPEN)
                .sourceOffer(null)
                .build();
        requestRepository.save(newRequest);
    }
    
    @Transactional
    public void confirmShipperOffer(Long requestId, Long winningOfferId, String currentUserId) {
        RequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다."));

        if (!request.getRequester().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신의 요청에 대해서만 확정할 수 있습니다.");
        }
        if (request.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("이미 마감된 요청입니다.");
        }

        List<OfferEntity> allOffers = offerRepository.findAllByRequest(request);
        OfferEntity winningOffer = allOffers.stream()
                .filter(o -> o.getOfferId().equals(winningOfferId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 제안입니다."));
        
        allOffers.forEach(offer -> {
            offer.setStatus(offer.equals(winningOffer) ? OfferStatus.ACCEPTED : OfferStatus.REJECTED);
        });
        request.setStatus(RequestStatus.CLOSED);
        
        // [✅ 아래 코드 추가]
        // 모든 제안의 상태가 변경된 후, 이벤트를 발행합니다.
        eventPublisher.publishEvent(new NotificationEvents.OfferConfirmedEvent(this, allOffers, winningOffer));
//        chatService.createChatRoomForOffer(winningOffer);

        if (!containerCargoRepository.existsByOffer_OfferId(winningOfferId)) {
            ContainerCargoEntity cargoInContainer = ContainerCargoEntity.builder()
                    .container(winningOffer.getContainer())
                    .offer(winningOffer)
                    .cbmLoaded(request.getCargo().getTotalCbm())
                    .isExternal(false)
                    .freightCost(winningOffer.getPrice())
                    .freightCurrency(winningOffer.getCurrency())
                    .build();
            containerCargoRepository.save(cargoInContainer);
        }
        
    }
}