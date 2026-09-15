package d6nggyun.stay.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCriteriaTest {

    @Test
    void 숙박일수는_체크아웃일을_제외한_박수다() {
        SearchCriteria criteria = SearchCriteria.of(
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04"), 2, 0);

        assertThat(criteria.nights()).isEqualTo(3);
    }

    @Test
    void 숙박일_목록은_체크인부터_체크아웃_전날까지다() {
        SearchCriteria criteria = SearchCriteria.of(
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04"), 2, 0);

        assertThat(criteria.stayDates()).containsExactly(
                LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-02"),
                LocalDate.parse("2026-09-03"));
    }

    @Test
    void 체크인이_체크아웃과_같거나_이후면_예외다() {
        assertThatThrownBy(() -> SearchCriteria.of(
                LocalDate.parse("2026-09-04"), LocalDate.parse("2026-09-04"), 2, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> SearchCriteria.of(
                LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-04"), 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 성인_인원이_0_이하면_예외다() {
        assertThatThrownBy(() -> SearchCriteria.of(
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04"), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 아동_인원이_음수면_예외다() {
        assertThatThrownBy(() -> SearchCriteria.of(
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04"), 2, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
