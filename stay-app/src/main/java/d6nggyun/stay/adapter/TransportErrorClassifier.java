package d6nggyun.stay.adapter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * 어댑터에서 발생한 예외가 어떤 전송 계층 실패인지 판정한다.
 * 연결·읽기 타임아웃과 연결 실패(connection refused 등)를 구분해, 실패 종류·재시도 여부를 통일하는 데 쓴다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TransportErrorClassifier {

    /** 연결·읽기 타임아웃 계열인지. */
    public static boolean isTimeout(Throwable ex) {
        return hasCauseMatching(ex, cause ->
                cause instanceof TimeoutException
                        || cause instanceof io.netty.handler.timeout.TimeoutException
                        || cause instanceof io.netty.channel.ConnectTimeoutException);
    }

    /** 연결 자체가 맺어지지 않은 전송 실패(connection refused 등)인지. 타임아웃과는 별개로 판정한다. */
    public static boolean isConnectionFailure(Throwable ex) {
        // ConnectTimeoutException은 ConnectException의 하위지만 타임아웃으로 이미 분류하므로 여기서는 제외한다.
        return hasCauseMatching(ex, cause ->
                cause instanceof ConnectException && !(cause instanceof io.netty.channel.ConnectTimeoutException));
    }

    /**
     * 원인 사슬을 끝까지 훑어 조건에 맞는 예외가 있는지 본다.
     * 타임아웃·연결 실패는 보통 다른 예외로 감싸여 올라오므로 맨 바깥만 보지 않는다.
     * 자기 자신을 cause로 가리키는 예외에서도 무한 루프에 빠지지 않게 멈춘다.
     */
    private static boolean hasCauseMatching(Throwable ex, java.util.function.Predicate<Throwable> predicate) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (predicate.test(cause)) {
                return true;
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return false;
    }
}
