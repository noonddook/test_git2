package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.TransactionHistoryDto;
import net.dima.project.entity.*;
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;   
import org.springframework.data.domain.Pageable; 

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionHistoryService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final OfferRepository offerRepository;

    public List<TransactionHistoryDto> getTransactionHistory(String currentUserId) {
        List<TransactionHistoryDto> sales = getSalesHistory(currentUserId);
        List<TransactionHistoryDto> purchases = getPurchaseHistory(currentUserId);

        // 판매내역과 구매내역을 합친 후 날짜순으로 정렬
        return Stream.concat(sales.stream(), purchases.stream())
                .sorted(Comparator.comparing(TransactionHistoryDto::getTransactionDate).reversed())
                .collect(Collectors.toList());
    }

    public List<TransactionHistoryDto> getSalesHistory(String currentUserId) {
        UserEntity user = userRepository.findByUserId(currentUserId);
        List<RequestEntity> myResaleRequests = requestRepository.findByRequesterAndStatusAndSourceOfferIsNotNull(user, RequestStatus.CLOSED);

        return myResaleRequests.stream().map(req -> {
            // [✅ 핵심 수정] ResaleService와 동일한 로직으로 낙찰된 제안을 찾습니다.
            OfferEntity winningOffer = offerRepository.findAllByRequest(req)
                    .stream()
                    .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                    .findFirst()
                    .orElse(null); // 낙찰 제안이 없으면 null

            if (winningOffer == null) return null;

            return TransactionHistoryDto.builder()
                    .transactionDate(winningOffer.getCreatedAt())
                    .type("판매")
                    .itemName(req.getCargo().getItemName())
                    .partnerName(winningOffer.getForwarder().getCompanyName())
                    .price(winningOffer.getPrice())
                    .currency(winningOffer.getCurrency())
                    .status(winningOffer.getContainer().getStatus().name())
                    .build();
        }).filter(dto -> dto != null).collect(Collectors.toList());
    }

    public List<TransactionHistoryDto> getPurchaseHistory(String currentUserId) {
        UserEntity user = userRepository.findByUserId(currentUserId);
        // 내가 제안자(forwarder)이고, 최종적으로 거래가 성사된(ACCEPTED 이상) 제안들을 찾음
        List<OfferStatus> completedStatuses = List.of(
        		OfferStatus.ACCEPTED
        		,OfferStatus.CONFIRMED
        		,OfferStatus.RESOLD
        		,OfferStatus.SHIPPED
        		,OfferStatus.COMPLETED);
        List<OfferEntity> myPurchases = offerRepository.findByForwarderAndStatusIn(user, completedStatuses);

        return myPurchases.stream().map(offer -> 
        TransactionHistoryDto.builder()
                .transactionDate(offer.getCreatedAt())
                .type("구매")
                .itemName(offer.getRequest().getCargo().getItemName())
                .partnerName(offer.getRequest().getRequester().getCompanyName())
                .price(offer.getPrice())
                .currency(offer.getCurrency())
                .status(offer.getContainer().getStatus().name())
                .build()
        		).collect(Collectors.toList());
    }
    
    public Page<TransactionHistoryDto> getShipperHistory(String currentUserId, Pageable pageable) {
        UserEntity shipper = userRepository.findByUserId(currentUserId);

        // 1. 화주가 올린 원본 요청 중, 마감된 요청만 모두 가져옵니다.
        List<RequestEntity> closedRequests = requestRepository.findByRequesterAndSourceOfferIsNullAndStatus(shipper, RequestStatus.CLOSED);

        // 2. 각 요청의 최종 낙찰 정보를 찾아 DTO로 변환합니다.
        List<TransactionHistoryDto> historyList = closedRequests.stream()
                .map(req -> {
                    Optional<OfferEntity> finalOfferOpt = findFinalOffer(req); // 최종 운송 제안 추적
                    
                    return finalOfferOpt.map(finalOffer -> TransactionHistoryDto.builder()
                            .transactionDate(finalOffer.getCreatedAt())
                            .type("요청") // 화주 입장에서는 '판매'가 아닌 '요청'이 더 적합
                            .itemName(req.getCargo().getItemName())
                            .partnerName(finalOffer.getForwarder().getCompanyName()) // 최종 담당 포워더
                            .price(finalOffer.getPrice())
                            .currency(finalOffer.getCurrency())
                            .status(finalOffer.getContainer().getStatus().name())
                            .build()
                    ).orElse(null); // 최종 낙찰자가 없는 경우는 제외
                })
                .filter(dto -> dto != null)
                .sorted(Comparator.comparing(TransactionHistoryDto::getTransactionDate).reversed())
                .collect(Collectors.toList());

        // 3. 수동으로 페이지네이션 객체를 생성합니다.
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), historyList.size());
        List<TransactionHistoryDto> pageContent = (start > end) ? List.of() : historyList.subList(start, end);

        return new PageImpl<>(pageContent, pageable, historyList.size());
    }

    /**
     * [신규] 재판매 체인을 추적하여 최종 운송 Offer를 찾는 헬퍼 메서드
     */
    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(request).stream()
                .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                .findFirst();

        if (winningOfferOpt.isPresent()) {
            OfferEntity winningOffer = winningOfferOpt.get();
            if (winningOffer.getStatus() == OfferStatus.RESOLD) {
                return requestRepository.findBySourceOffer(winningOffer)
                        .flatMap(this::findFinalOffer);
            } else {
                return winningOfferOpt;
            }
        }
        return Optional.empty();
    }
}