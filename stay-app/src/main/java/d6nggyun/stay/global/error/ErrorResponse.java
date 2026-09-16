package d6nggyun.stay.global.error;

/**
 * 오류 응답 공통 형식. 코드로 오류 종류를, 메시지로 사람이 읽을 사유를 전달한다.
 * (부분·전체 실패는 오류가 아니라 검색 응답의 공급사 상태로 표현하므로 이 형식을 쓰지 않는다.)
 */
public record ErrorResponse(String code, String message) {
}
