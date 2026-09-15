package d6nggyun.stay.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceTest {

    @Test
    void 총액을_숙박일수로_나눈_1박_평균가를_계산한다() {
        Price price = Price.of("KRW", 429_000, 3);

        assertThat(price.grossTotalAmount()).isEqualTo(429_000);
        assertThat(price.averageNightlyAmount()).isEqualTo(143_000);
        assertThat(price.currency()).isEqualTo("KRW");
    }

    @Test
    void 나누어떨어지지_않으면_1박_평균가는_내림한다() {
        // 452,000 / 3 = 150,666.67 -> 150,666
        Price price = Price.of("KRW", 452_000, 3);

        assertThat(price.averageNightlyAmount()).isEqualTo(150_666);
    }

    @Test
    void 숙박일수가_0_이하면_예외다() {
        assertThatThrownBy(() -> Price.of("KRW", 429_000, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
