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

    public static MyOfferDto fromEntity(OfferEntity entity) {
        OfferStatus status = entity.getStatus(); // [✅ 1. 수정] 타입을 String -> OfferStatus로 변경
        String statusText;
        
        if (status == OfferStatus.FOR_SALE && LocalDateTime.now().isAfter(entity.getRequest().getDeadline())) {
            statusText = "확정 (재판매 마감)";
            status = OfferStatus.ACCEPTED; // 화면 표시용으로 임시 변경
        } else {
            // [✅ 2. 수정] switch문이 Enum을 직접 비교하도록 변경
            // [✅ 수정] switch문에 CONFIRMED 케이스 추가
            switch (status) {
                case PENDING: statusText = "진행중"; break;
                case ACCEPTED: statusText = "수락"; break;
                case REJECTED: statusText = "거절"; break;
                case FOR_SALE: statusText = "재판매중"; break;
                case RESOLD: statusText = "재판매 완료"; break;
                case CONFIRMED: statusText = "확정"; break; // [✅ 추가]
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
                .status(status.name()) // [✅ 3. 수정] Enum을 String으로 변환 (.name() 사용)
                .statusText(statusText)
                .deadline(entity.getRequest().getDeadline())
                .build();
    }
}