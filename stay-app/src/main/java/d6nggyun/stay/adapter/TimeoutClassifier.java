package d6nggyun.stay.adapter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeoutException;

/**
 * 어댑터에서 발생한 예외가 타임아웃 계열인지 판정한다.
 * 연결 타임아웃(커넥터)·읽기 타임아웃 등 전송 계층 타임아웃을 실패 종류(TIMEOUT)로 통일하기 위한 공통 판정이다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TimeoutClassifier {

    public static boolean isTimeout(Throwable ex) {
        // 타임아웃은 보통 다른 예외로 감싸여 올라오므로(예: ...WebClientException ← ReadTimeoutException),
        // 맨 바깥만 보지 않고 원인 사슬을 끝까지 훑어 타임아웃 계열이 섞였는지 확인한다.
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            // 세 타입을 함께 본다: 표준 타임아웃, Netty 읽기 타임아웃(ReadTimeoutException의 상위),
            // 그리고 커넥터 연결 타임아웃(CONNECT_TIMEOUT_MILLIS 초과). 이 경로가 이 판정의 주 대상이다.
            if (cause instanceof TimeoutException
                    || cause instanceof io.netty.handler.timeout.TimeoutException
                    || cause instanceof io.netty.channel.ConnectTimeoutException) {
                return true;
            }
            // 자기 자신을 cause로 가리키는 예외가 있으면 무한 루프가 되므로, 더 내려갈 곳이 없다고 보고 멈춘다.
            if (cause == cause.getCause()) {
                break;
            }
        }
        return false;
    }
}
