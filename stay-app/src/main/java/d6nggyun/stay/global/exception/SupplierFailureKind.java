package d6nggyun.stay.global.exception;

/**
 * 공급사 연동 실패의 종류. 공급사마다 실패 표현이 달라도(A: HTTP 4xx/5xx, B: 본문 resultCode)
 * 어댑터가 이 종류로 구분해 두면, 오케스트레이터가 공급사별 상태(HTTP_ERROR/PROTOCOL_ERROR)로 통일해 판정한다.
 */
public enum SupplierFailureKind {

    /** HTTP 4xx/5xx 등 전송 계층에서 드러난 실패. (A의 오류 상태, B의 실제 HTTP 오류) */
    HTTP_ERROR,

    /** HTTP는 정상이지만 본문 규약 위반(B의 resultCode != "0000")이나 역직렬화·형식 오류. */
    PROTOCOL_ERROR
}
