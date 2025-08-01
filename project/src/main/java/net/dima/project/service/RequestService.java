package net.dima.project.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import net.dima.project.dto.MyPostedRequestDto;
import net.dima.project.dto.MyRequestStatusDto;
import net.dima.project.dto.RequestCardDto;
import net.dima.project.entity.CargoEntity;
import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.OfferStatus;
import net.dima.project.entity.RequestEntity;
import net.dima.project.entity.RequestStatus;
import net.dima.project.entity.UserEntity;
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.UserRepository;
import net.dima.project.dto.NewRequestDto;      // import 추가
import net.dima.project.repository.CargoRepository; // import 추가
import net.dima.project.repository.ContainerCargoRepository; // import 추가
import net.dima.project.entity.ContainerCargoEntity; // import 추가
import org.springframework.data.domain.PageImpl;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestService {

    private final RequestRepository requestRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository; // [✅ 이 줄을 추가해주세요]
    private final CargoRepository cargoRepository;
    private final ContainerCargoRepository containerCargoRepository;

    // [✅ 이 메서드로 완전히 교체해주세요]
 // [✅ 이 메서드 전체를 아래 코드로 교체해주세요]
 // [✅ 이 메서드 전체를 아래 코드로 교체해주세요]
 // [✅ 이 메서드 전체를 아래 코드로 교체해주세요]
    public Page<RequestCardDto> getRequests(
            boolean excludeClosed,
            String tradeType, String transportType,
            String departurePort, String arrivalPort,
            String itemName, Pageable pageable, String currentUserId) {

        Specification<RequestEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ★★★ 핵심 수정: 마감 시간(deadline)을 기준으로 필터링하도록 변경 ★★★
            LocalDateTime now = LocalDateTime.now();
            if (excludeClosed) {
                // '마감 제안 제외'가 켜져 있으면, OPEN 상태이면서 마감 시간이 지나지 않은 것만 조회
                predicates.add(cb.equal(root.get("status"), RequestStatus.OPEN));
                predicates.add(cb.greaterThan(root.get("deadline"), now));
            }

            // 다른 필터 조건들은 그대로 유지
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
    
    /**
     * 화주(CUS)의 '나의요청조회'를 위한 데이터를 조회하는 메서드
     */
    @Transactional(readOnly = true)
    public Page<MyPostedRequestDto> getRequestsForShipper(String currentUserId, String status, Pageable pageable) {
        UserEntity shipper = userRepository.findByUserId(currentUserId);

        // 1. DB에서는 정렬만 적용하여 화주의 모든 원본 요청을 가져옵니다.
        List<RequestEntity> allMyRequests = requestRepository.findByRequesterAndSourceOfferIsNull(shipper, pageable.getSort());

        // 2. 각 요청의 최종 상태를 추적하여 DTO 리스트로 변환합니다.
        List<MyPostedRequestDto> dtoList = allMyRequests.stream().map(req -> {
            Optional<OfferEntity> finalOfferOpt = findFinalOffer(req); // 최종 운송자 추적
            if (req.getStatus() == RequestStatus.OPEN) {
                long bidderCount = offerRepository.countByRequest(req);
                return MyPostedRequestDto.fromEntity(req, bidderCount);
            } else {
                return MyPostedRequestDto.fromEntity(req, finalOfferOpt);
            }
        }).collect(Collectors.toList());

        // 3. 상태(status) 필터가 있다면, 자바 스트림으로 직접 필터링합니다.
        List<MyPostedRequestDto> filteredList;
        if (status != null && !status.isEmpty()) {
            filteredList = dtoList.stream()
                    .filter(dto -> {
                        if ("OPEN".equalsIgnoreCase(status)) {
                            // '제안받는중'은 Request의 상태를 직접 비교
                            return "OPEN".equals(dto.getStatus());
                        }
                        // 그 외 상태는 최종 Offer의 상세 상태와 비교
                        return dto.getDetailedStatus() != null && status.equalsIgnoreCase(dto.getDetailedStatus());
                    })
                    .collect(Collectors.toList());
        } else {
            filteredList = dtoList; // 필터가 없으면 전체 목록 사용
        }

        // 4. 필터링된 최종 목록으로 수동 페이지네이션 객체를 만듭니다.
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredList.size());
        List<MyPostedRequestDto> pageContent = (start > end) ? List.of() : filteredList.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, filteredList.size());
    }
    
    /**
     * 재판매 체인을 끝까지 추적하여 최종 운송 Offer를 찾는 재귀적 로직
     * @param request 시작 요청
     * @return 최종 운송을 담당하는 OfferEntity Optional
     */
    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        // 1. 현재 요청의 낙찰자를 찾음 (거절/보류 제외)
        Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(request)
                .stream()
                .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                .findFirst();

        if (winningOfferOpt.isPresent()) {
            OfferEntity winningOffer = winningOfferOpt.get();
            // 2. 만약 낙찰된 제안이 '재판매' 상태라면, 다음 거래를 추적
            if (winningOffer.getStatus() == OfferStatus.RESOLD) {
                // 이 제안(Offer)을 근원(sourceOffer)으로 하는 다음 요청(Request)을 찾음
                return requestRepository.findBySourceOffer(winningOffer)
                        .flatMap(this::findFinalOffer); // 재귀 호출로 최종 낙찰자를 찾을 때까지 반복
            } else {
                // 3. '재판매'가 아니면, 이 제안이 최종 운송자이므로 반환
                return winningOfferOpt;
            }
        }
        return Optional.empty(); // 낙찰자가 없는 경우
    }
    
    
    @Transactional
    public void createNewRequest(NewRequestDto dto, String currentUserId) {
        UserEntity requester = userRepository.findByUserId(currentUserId);

        // 1. 화물(Cargo) 정보 생성 및 저장
        CargoEntity newCargo = CargoEntity.builder()
                .owner(requester) // 화물의 원 소유자는 요청자
                .itemName(dto.getItemName())
                .incoterms(dto.getIncoterms())
                .totalCbm(dto.getTotalCbm())
                .isDangerous(dto.getIsDangerous())
                .build();
        cargoRepository.save(newCargo);

        // 2. 요청(Request) 정보 생성 및 저장
        RequestEntity newRequest = RequestEntity.builder()
                .cargo(newCargo)
                .requester(requester)
                .departurePort(dto.getDeparturePort())
                .arrivalPort(dto.getArrivalPort())
                .deadline(dto.getDeadline())
                .tradeType(dto.getTradeType())
                .transportType(dto.getTransportType())
                .status(RequestStatus.OPEN) // 최초 생성 시 항상 OPEN
                .sourceOffer(null) // 화주가 올린 요청은 재판매가 아니므로 null
                .build();
        requestRepository.save(newRequest);
    }

    /**
     * [신규] 특정 화주가 올린 요청 목록만 페이징하여 조회합니다.
     */
    public Page<MyPostedRequestDto> getRequestsForShipper(String currentUserId, Pageable pageable) {
        UserEntity shipper = userRepository.findByUserId(currentUserId);
        
        // 재판매 요청(sourceOffer가 있는)을 제외하고, 내가 요청한 것만 조회
        Page<RequestEntity> requestPage = requestRepository.findByRequesterAndSourceOfferIsNull(shipper, pageable);

        // 기존 DTO를 재활용하여 Page 객체로 변환
        return requestPage.map(req -> {
            if (req.getStatus() == RequestStatus.OPEN) {
                long bidderCount = offerRepository.countByRequest(req);
                return MyPostedRequestDto.fromEntity(req, bidderCount);
            } else {
                Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(req)
                        .stream()
                        .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                        .findFirst();
                return MyPostedRequestDto.fromEntity(req, winningOfferOpt);
            }
        });
    }
    
    /**
     * 화주가 포워더의 제안을 최종 확정합니다.
     */
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
        
        // 1. 상태 변경: 낙찰 제안은 ACCEPTED, 나머지는 REJECTED, 요청은 CLOSED
        allOffers.forEach(offer -> {
            offer.setStatus(offer.equals(winningOffer) ? OfferStatus.ACCEPTED : OfferStatus.REJECTED);
        });
        request.setStatus(RequestStatus.CLOSED);

        // 2. (핵심) 낙찰된 포워더의 컨테이너에 화물을 등록합니다.
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