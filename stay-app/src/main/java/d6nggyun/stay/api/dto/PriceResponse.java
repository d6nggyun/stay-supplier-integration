package d6nggyun.stay.api.dto;

import d6nggyun.stay.domain.Price;

/**
 * 요금 응답. 통화(ISO 4217), 세금 포함 총액, 1박 평균가(총액 ÷ 박수, 내림)를 담는다.
 */
public record PriceResponse(String currency, long grossTotalAmount, long averageNightlyAmount) {

    public static PriceResponse from(Price price) {
        return new PriceResponse(price.currency(), price.grossTotalAmount(), price.averageNightlyAmount());
    }
}
