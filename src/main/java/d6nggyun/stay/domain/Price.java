package d6nggyun.stay.domain;

/**
 * 표준 요금 모델. 공통 기준은 세금 포함 총액(gross)이며,
 * 1박 평균가는 총액을 숙박일수로 나눈 표시용 파생 값이다.
 * 금액은 통화의 최소 단위 정수로 다룬다.
 */
public record Price(String currency, long grossTotalAmount, long averageNightlyAmount) {

    public Price {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency는 필수입니다.");
        }
        if (grossTotalAmount < 0) {
            throw new IllegalArgumentException("grossTotalAmount는 0 이상이어야 합니다.");
        }
    }

    /**
     * 세금 포함 총액과 숙박일수로 요금을 구성한다.
     * 1박 평균가는 총액 ÷ 숙박일수이며, 나누어떨어지지 않으면 내림한다.
     * 총액이 원본 값이고 평균가는 표시용 파생 값이므로, 평균가 × 박수 ≠ 총액이 될 수 있음을 허용한다.
     */
    public static Price of(String currency, long grossTotalAmount, long nights) {
        if (nights <= 0) {
            throw new IllegalArgumentException("nights는 0보다 커야 합니다.");
        }
        long averageNightlyAmount = grossTotalAmount / nights; // 음수가 아니므로 정수 나눗셈이 곧 내림
        return new Price(currency, grossTotalAmount, averageNightlyAmount);
    }
}
