package d6nggyun.stay.global.exception;

/**
 * 클라이언트가 준 값이 규칙에 어긋날 때의 예외(예: 잘못된 검색 조건).
 * 전역 핸들러가 이 타입만 400으로 변환한다. 공급사 데이터·내부 계산의 불변식 위반은 이 타입이 아니라
 * IllegalArgumentException으로 두어, 클라이언트 오류(400)와 서버/연동 문제(500·연동 상태)를 구분한다.
 */
public class InvalidRequestException extends StayException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
