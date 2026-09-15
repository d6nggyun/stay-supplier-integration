package d6nggyun.stay.global.exception;

/**
 * 애플리케이션 공통 예외 베이스. 도메인·연동 등에서 발생하는 커스텀 예외는 이 타입을 상속한다.
 * 예외 처리를 한 곳(예: @RestControllerAdvice)에서 일괄로 다루기 위한 기준점이다.
 */
public class StayException extends RuntimeException {

    public StayException(String message) {
        super(message);
    }

    public StayException(String message, Throwable cause) {
        super(message, cause);
    }
}
