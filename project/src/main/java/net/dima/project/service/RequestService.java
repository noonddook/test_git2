// [✅ RequestService.java 파일 전체를 이 코드로 교체해주세요]
package net.dima.project.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestService {

    private final RequestRepository requestRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final CargoRepository cargoRepository;
    private final ContainerCargoRepository containerCargoRepository;

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
    @Transactional(readOnly = true)
    public Page<MyPostedRequestDto> getRequestsForShipper(String currentUserId, String status, boolean excludeClosed, Pageable pageable) {
        UserEntity shipper = userRepository.findByUserId(currentUserId);
        List<RequestEntity> allMyRequests = requestRepository.findByRequesterAndSourceOfferIsNull(shipper, pageable.getSort());

        List<MyPostedRequestDto> dtoList = allMyRequests.stream()
            .filter(req -> {
                // '정산완료'된 요청은 목록에서 제외하는 필터
                if (req.getStatus() == RequestStatus.OPEN) return true;
                Optional<OfferEntity> finalOfferOpt = findFinalOffer(req);
                return finalOfferOpt.map(o -> o.getContainer().getStatus() != ContainerStatus.SETTLED).orElse(true);
            })
            .map(req -> {
                // DTO로 변환
                Optional<OfferEntity> finalOfferOpt = findFinalOffer(req);
                if (req.getStatus() == RequestStatus.OPEN) {
                    long bidderCount = offerRepository.countByRequest(req);
                    return MyPostedRequestDto.fromEntity(req, bidderCount);
                } else {
                    return MyPostedRequestDto.fromEntity(req, finalOfferOpt);
                }
            })
            .collect(Collectors.toList());
        
        Stream<MyPostedRequestDto> stream = dtoList.stream();

        // '마감 제외' 필터 적용
        if (excludeClosed) {
            stream = stream.filter(dto -> {
                // 낙찰자가 정해졌거나 (detailedStatus가 NONE이 아님) 아직 진행중(OPEN)인 건만 표시
                boolean hasWinner = dto.getDetailedStatus() != null && !"NONE".equals(dto.getDetailedStatus());
                return "OPEN".equals(dto.getStatus()) || hasWinner;
            });
        }
        
        // '상태별' 필터 적용
        if (status != null && !status.isEmpty()) {
            stream = stream.filter(dto -> {
                if ("OPEN".equalsIgnoreCase(status)) {
                    return "OPEN".equals(dto.getStatus());
                }
                return dto.getDetailedStatus() != null && status.equalsIgnoreCase(dto.getDetailedStatus());
            });
        }

        List<MyPostedRequestDto> filteredList = stream.collect(Collectors.toList());
        
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredList.size());
        List<MyPostedRequestDto> pageContent = (start > end) ? List.of() : filteredList.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, filteredList.size());
    }
    
    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(request)
                .stream()
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