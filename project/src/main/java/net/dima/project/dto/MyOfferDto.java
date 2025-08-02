package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.OfferStatus;

import java.time.LocalDateTime; // LocalDateTime 임포트
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class MyOfferDto {
	private Long offerId; 
    private String requestIdLabel; // REQ-397 형식
    private String incoterms;
    private String departurePort;
    private String arrivalPort;
    private String departureDate; // 컨테이너 출발일
    private Double cbm;
    private String status; // PENDING, ACCEPTED, REJECTED 등
    private String statusText; // 진행중, 수락, 거절 등
    private LocalDateTime deadline;
    private boolean isFullySettled; // [✅ 추가]

    public static MyOfferDto fromEntity(OfferEntity entity) {
        OfferStatus status = entity.getStatus();
        String statusText;
        
        // [✅ 핵심 수정] '진행중' 상태일 때 마감 시간을 확인하는 로직 추가
        if (status == OfferStatus.PENDING && LocalDateTime.now().isAfter(entity.getRequest().getDeadline())) {
            status = OfferStatus.REJECTED; // 화면에 보여줄 상태를 '거절'로 변경
            statusText = "거절";
        } else if (status == OfferStatus.FOR_SALE && LocalDateTime.now().isAfter(entity.getRequest().getDeadline())) {
            statusText = "확정 (재판매 마감)";
            status = OfferStatus.ACCEPTED;
        } else {
            // 그 외의 경우는 기존 로직을 따름
            switch (status) {
                case PENDING: statusText = "진행중"; break;
                case ACCEPTED: statusText = "수락"; break;
                case REJECTED: statusText = "거절"; break;
                case FOR_SALE: statusText = "재판매중"; break;
                case RESOLD: statusText = "재판매 완료"; break;
                case CONFIRMED: statusText = "확정"; break;
                default: statusText = status.name();
            }
        }

        return MyOfferDto.builder()
                .offerId(entity.getOfferId())
                .requestIdLabel("REQ-" + entity.getRequest().getRequestId())
                .incoterms(entity.getRequest().getCargo().getIncoterms())
                .departurePort(entity.getRequest().getDeparturePort())
                .arrivalPort(entity.getRequest().getArrivalPort())
                .departureDate(entity.getContainer().getEtd().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .cbm(entity.getRequest().getCargo().getTotalCbm())
                .status(status.name()) // 변경된 상태를 사용
                .statusText(statusText)
                .deadline(entity.getRequest().getDeadline())
                .build();
    }
}