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

import java.time.LocalDate;
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

    // ★★★ 1. 이 메서드가 아래 두 메서드에 파라미터를 전달합니다 ★★★
    public List<TransactionHistoryDto> getTransactionHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
        List<TransactionHistoryDto> sales = getSalesHistory(currentUserId, startDate, endDate, keyword);
        List<TransactionHistoryDto> purchases = getPurchaseHistory(currentUserId, startDate, endDate, keyword);

        return Stream.concat(sales.stream(), purchases.stream())
                .sorted(Comparator.comparing(TransactionHistoryDto::getTransactionDate).reversed())
                .collect(Collectors.toList());
    }

    // ★★★ 2. getSalesHistory 메서드가 모든 파라미터를 받도록 수정 ★★★
    public List<TransactionHistoryDto> getSalesHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
        UserEntity user = userRepository.findByUserId(currentUserId);
        List<RequestEntity> myResaleRequests = requestRepository.findByRequesterAndStatusAndSourceOfferIsNotNull(user, RequestStatus.CLOSED);

        return myResaleRequests.stream().map(req -> {
            OfferEntity winningOffer = offerRepository.findAllByRequest(req)
                    .stream()
                    .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                    .findFirst()
                    .orElse(null);

            if (winningOffer == null || winningOffer.getContainer().getStatus() != ContainerStatus.SETTLED) {
                return null;
            }

            return TransactionHistoryDto.builder()
                    .transactionDate(winningOffer.getCreatedAt())
                    .type("판매")
                    .itemName(req.getCargo().getItemName())
                    .departurePort(req.getDeparturePort())
                    .arrivalPort(req.getArrivalPort())
                    .partnerName(winningOffer.getForwarder().getCompanyName())
                    .price(winningOffer.getPrice())
                    .currency(winningOffer.getCurrency())
                    .status("정산완료")
                    .build();
        })
        .filter(dto -> dto != null)
        .filter(dto -> (startDate == null || !dto.getTransactionDate().toLocalDate().isBefore(startDate)))
        .filter(dto -> (endDate == null || !dto.getTransactionDate().toLocalDate().isAfter(endDate)))
        .filter(dto -> (keyword == null || keyword.isBlank() ||
                (dto.getItemName() != null && dto.getItemName().toLowerCase().contains(keyword.toLowerCase())) ||
                (dto.getPartnerName() != null && dto.getPartnerName().toLowerCase().contains(keyword.toLowerCase()))))
        .collect(Collectors.toList());
    }

    // ★★★ 3. getPurchaseHistory 메서드가 모든 파라미터를 받도록 수정 ★★★
    public List<TransactionHistoryDto> getPurchaseHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
        UserEntity user = userRepository.findByUserId(currentUserId);
        List<OfferStatus> completedStatuses = List.of(
                OfferStatus.ACCEPTED, OfferStatus.CONFIRMED, OfferStatus.RESOLD,
                OfferStatus.SHIPPED, OfferStatus.COMPLETED);
        List<OfferEntity> myPurchases = offerRepository.findByForwarderAndStatusIn(user, completedStatuses);

        return myPurchases.stream()
                .filter(offer -> offer.getContainer() != null && offer.getContainer().getStatus() == ContainerStatus.SETTLED)
                .map(offer ->
                        TransactionHistoryDto.builder()
                                .transactionDate(offer.getCreatedAt())
                                .type("구매")
                                .itemName(offer.getRequest().getCargo().getItemName())
                                .departurePort(offer.getRequest().getDeparturePort())
                                .arrivalPort(offer.getRequest().getArrivalPort())
                                .partnerName(offer.getRequest().getRequester().getCompanyName())
                                .price(offer.getPrice())
                                .currency(offer.getCurrency())
                                .status("정산완료")
                                .build()
                )
                .filter(dto -> (startDate == null || !dto.getTransactionDate().toLocalDate().isBefore(startDate)))
                .filter(dto -> (endDate == null || !dto.getTransactionDate().toLocalDate().isAfter(endDate)))
                .filter(dto -> (keyword == null || keyword.isBlank() ||
                        (dto.getItemName() != null && dto.getItemName().toLowerCase().contains(keyword.toLowerCase())) ||
                        (dto.getPartnerName() != null && dto.getPartnerName().toLowerCase().contains(keyword.toLowerCase()))))
                .collect(Collectors.toList());
    }

    public Page<TransactionHistoryDto> getShipperHistory(String currentUserId, Pageable pageable) {
        UserEntity shipper = userRepository.findByUserId(currentUserId);
        List<RequestEntity> closedRequests = requestRepository.findByRequesterAndSourceOfferIsNullAndStatus(shipper, RequestStatus.CLOSED);

        List<TransactionHistoryDto> historyList = closedRequests.stream()
                .map(req -> {
                    Optional<OfferEntity> finalOfferOpt = findFinalOffer(req);
                    return finalOfferOpt.map(finalOffer -> TransactionHistoryDto.builder()
                            .transactionDate(finalOffer.getCreatedAt())
                            .type("요청")
                            .itemName(req.getCargo().getItemName())
                            .partnerName(finalOffer.getForwarder().getCompanyName())
                            .price(finalOffer.getPrice())
                            .currency(finalOffer.getCurrency())
                            .status(finalOffer.getContainer().getStatus().name())
                            .build()
                    ).orElse(null);
                })
                .filter(dto -> dto != null)
                .sorted(Comparator.comparing(TransactionHistoryDto::getTransactionDate).reversed())
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), historyList.size());
        List<TransactionHistoryDto> pageContent = (start > end) ? List.of() : historyList.subList(start, end);

        return new PageImpl<>(pageContent, pageable, historyList.size());
    }

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