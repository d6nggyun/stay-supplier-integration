package d6nggyun.stay.domain;

import d6nggyun.stay.global.exception.InvalidRequestException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 고객의 검색 조건. 날짜 경계는 체크아웃일을 숙박일에 포함하지 않는다.
 * 예: 2026-09-01 ~ 2026-09-04 는 3박.
 */
public record SearchCriteria(LocalDate checkIn, LocalDate checkOut, int adults, int children) {

    public SearchCriteria {
        // 클라이언트 입력 검증이므로 InvalidRequestException(→ 400)으로 던진다.
        if (checkIn == null || checkOut == null) {
            throw new InvalidRequestException("checkIn과 checkOut은 필수입니다.");
        }
        if (!checkIn.isBefore(checkOut)) {
            throw new InvalidRequestException("checkIn은 checkOut보다 이전이어야 합니다.");
        }
        if (adults <= 0) {
            throw new InvalidRequestException("adults는 0보다 커야 합니다.");
        }
        if (children < 0) {
            throw new InvalidRequestException("children은 0 이상이어야 합니다.");
        }
    }

    public static SearchCriteria of(LocalDate checkIn, LocalDate checkOut, int adults, int children) {
        return new SearchCriteria(checkIn, checkOut, adults, children);
    }

    /** 숙박일수(박). 체크아웃일은 포함하지 않는다. */
    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /** 재고·요금 계산 대상이 되는 숙박일 목록. 체크인일부터 체크아웃 전날까지. */
    public List<LocalDate> stayDates() {
        return checkIn.datesUntil(checkOut).toList();
    }
}
