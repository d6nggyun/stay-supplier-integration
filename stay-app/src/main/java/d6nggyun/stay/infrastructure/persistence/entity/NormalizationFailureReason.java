package d6nggyun.stay.infrastructure.persistence.entity;

/**
 * 정규화 실패의 사유. 격리 레코드를 사유별로 집계해 재발 패턴을 분석하기 위한 분류다.
 * 지금은 매핑 미존재만 다루며, 역직렬화·형식 오류 등은 필요 시 추가한다.
 */
public enum NormalizationFailureReason {

    /** 공급사 코드에 대응하는 내부 식별자 매핑이 없어 치환할 수 없음. */
    MAPPING_NOT_FOUND
}
