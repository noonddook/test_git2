// [✅ RequestService.java 파일 전체를 이 코드로 교체해주세요]
package net.dima.project.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        // 1. [핵심] DB에서 처음부터 페이징을 적용하여 딱 필요한 만큼의 데이터만 가져옵니다.
        Specification<RequestEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("requester"), shipper));
            predicates.add(cb.isNull(root.get("sourceOffer")));

            if (status != null && !status.isEmpty()) {
                if ("CLOSED".equalsIgnoreCase(status)) {
                    predicates.add(cb.equal(root.get("status"), RequestStatus.CLOSED));
                } else if ("OPEN".equalsIgnoreCase(status)) {
                    predicates.add(cb.equal(root.get("status"), RequestStatus.OPEN));
                    if (excludeClosed) {
                        predicates.add(cb.greaterThan(root.get("deadline"), now));
                    }
                }
            }

            if (itemName != null && !itemName.isBlank()) {
                root.fetch("cargo", JoinType.LEFT);
                predicates.add(cb.like(root.get("cargo").get("itemName"), "%" + itemName + "%"));
            }
            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<RequestEntity> requestPage = requestRepository.findAll(spec, pageable);
        List<RequestEntity> requestsOnPage = requestPage.getContent();

        if (requestsOnPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2. N+1 문제 해결: 현재 페이지에 필요한 모든 추가 정보를 '일괄 조회'합니다.
        List<RequestEntity> openRequests = requestsOnPage.stream().filter(r -> r.getStatus() == RequestStatus.OPEN).collect(Collectors.toList());
        Map<Long, Long> offerCounts = offerRepository.countOffersByRequestIn(openRequests).stream()
                .collect(Collectors.toMap(arr -> (Long) arr[0], arr -> (Long) arr[1]));

        List<RequestEntity> closedRequests = requestsOnPage.stream().filter(r -> r.getStatus() == RequestStatus.CLOSED).collect(Collectors.toList());
        Map<Long, Optional<OfferEntity>> directOffersMap = offerRepository.findWinningOffersForRequests(closedRequests).stream()
                .collect(Collectors.toMap(o -> o.getRequest().getRequestId(), Optional::ofNullable));
        Map<Long, Optional<OfferEntity>> finalOffersMap = closedRequests.stream()
                .collect(Collectors.toMap(RequestEntity::getRequestId, this::findFinalOffer));

        // 3. 메모리에서 DTO를 만들면서 '정산완료' 건을 최종적으로 필터링합니다.
        List<MyPostedRequestDto> dtoList = requestsOnPage.stream()
            .map(req -> {
                if (req.getStatus() == RequestStatus.OPEN) {
                    return MyPostedRequestDto.fromEntity(req, offerCounts.getOrDefault(req.getRequestId(), 0L));
                } else { // CLOSED
                    Optional<OfferEntity> finalOfferOpt = finalOffersMap.getOrDefault(req.getRequestId(), Optional.empty());
                    if (finalOfferOpt.isPresent() && finalOfferOpt.get().getContainer().getStatus() == ContainerStatus.SETTLED) {
                        return null; // 정산완료 건은 목록에서 제외
                    }
                    Optional<OfferEntity> directOfferOpt = directOffersMap.getOrDefault(req.getRequestId(), Optional.empty());
                    return MyPostedRequestDto.fromEntity(req, directOfferOpt, finalOfferOpt);
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, requestPage.getTotalElements());
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