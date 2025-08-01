package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.OfferEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class MyOfferDetailDto {

    // 화물 정보
    private Long cargoId;
    private String itemName; // [✅ 이 줄을 추가해주세요]
    private Double cbm;

    // 화주(요청자) 정보
    private String requesterCompanyName;

    // 요청 정보
    private String deadline;

    // 내 제안 정보
    private BigDecimal myOfferPrice;
    private String myOfferCurrency;
    private String containerId;

    // Entity를 DTO로 변환하는 정적 팩토리 메서드
    public static MyOfferDetailDto fromEntity(OfferEntity offer) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        return MyOfferDetailDto.builder()
                .cargoId(offer.getRequest().getCargo().getCargoId())
                .itemName(offer.getRequest().getCargo().getItemName())
                .cbm(offer.getRequest().getCargo().getTotalCbm())
                .requesterCompanyName(offer.getRequest().getRequester().getCompanyName())
                .deadline(offer.getRequest().getDeadline().format(formatter))
                .myOfferPrice(offer.getPrice())
                .myOfferCurrency(offer.getCurrency())
                .containerId(offer.getContainer().getContainerId())
                .build();
    }
}