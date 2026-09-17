package d6nggyun.stay.application.search;

/**
 * 검색 시 공급사별 처리 상태. 공급사마다 실패 표현이 달라도 오케스트레이터 바깥에서는 이 값으로 통일한다.
 * 상태 판정 규칙은 docs/failure-handling.md 참고.
 */
public enum SupplierSearchStatus {

    /** 정상 응답. 결과 0건도 포함하며 건수는 resultCount로 구분한다. */
    SUCCESS,

    /** 여러 청크 중 일부만 성공. (청크 분할은 #9에서 도입) */
    PARTIAL_SUCCESS,

    /** 연결·응답 타임아웃. (#9에서 도입) */
    TIMEOUT,

    /** 서킷 브레이커가 열려 호출을 차단함. 지속 실패 공급사에 매달리지 않기 위한 상태다. */
    CIRCUIT_OPEN,

    /** Supplier A의 HTTP 4xx·5xx 등 전송 계층 오류. */
    HTTP_ERROR,

    /** Supplier B의 resultCode != "0000" 또는 응답 역직렬화·형식 오류. */
    PROTOCOL_ERROR,

    /** 정상 응답인데 조회 대상 자체가 0건인 경우에만 한정해 사용한다. */
    NO_DATA,

    /** 매핑이 없어 호출 대상에서 빠진 경우. 왜 결과에 없는지 응답만으로 드러내기 위한 상태다. */
    SKIPPED
}
