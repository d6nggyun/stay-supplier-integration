package d6nggyun.stay.global.error;

import d6nggyun.stay.global.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외를 한 곳에서 HTTP 응답으로 변환한다.
 * 잘못된 요청(날짜 범위·인원, 누락·형식 오류)은 400, 예기치 못한 오류는 500으로 응답한다.
 * (공급사 부분·전체 실패는 오류가 아니라 검색 응답의 상태/HTTP 코드로 다루므로 여기서 처리하지 않는다.)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 클라이언트 입력 검증 실패(SearchCriteria 등). 광범위한 IllegalArgumentException은 잡지 않는다. */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        return badRequest("INVALID_REQUEST", ex.getMessage());
    }

    /** 파라미터 타입 불일치(예: 날짜 형식 오류). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("INVALID_PARAMETER", "'" + ex.getName() + "' 파라미터 값이 올바르지 않습니다.");
    }

    /** 필수 파라미터 누락. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequest("MISSING_PARAMETER", "'" + ex.getParameterName() + "' 파라미터는 필수입니다.");
    }

    /** 매핑되지 않은 경로. 프레임워크의 404를 500으로 뭉개지 않도록 별도로 처리한다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "요청 경로를 찾을 수 없습니다."));
    }

    /** 그 밖의 예기치 못한 오류. 내부 메시지는 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("처리되지 않은 예외", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "요청을 처리하지 못했습니다."));
    }

    private ResponseEntity<ErrorResponse> badRequest(String code, String message) {
        return ResponseEntity.badRequest().body(new ErrorResponse(code, message));
    }
}
