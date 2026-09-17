package d6nggyun.stay.adapter;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class TransportErrorClassifierTest {

    @Test
    void 직접_타임아웃이면_참이다() {
        assertThat(TransportErrorClassifier.isTimeout(new TimeoutException("timed out"))).isTrue();
    }

    @Test
    void 다른_예외로_감싸인_타임아웃도_참이다() {
        Throwable wrapped = new RuntimeException("call failed", new TimeoutException("read timeout"));
        assertThat(TransportErrorClassifier.isTimeout(wrapped)).isTrue();
    }

    @Test
    void 연결_타임아웃도_타임아웃으로_본다() {
        Throwable connectTimeout = new io.netty.channel.ConnectTimeoutException("connection timed out");
        assertThat(TransportErrorClassifier.isTimeout(connectTimeout)).isTrue();
        // 연결 타임아웃은 타임아웃으로만 분류하고 연결 실패로는 세지 않는다(이중 분류 방지).
        assertThat(TransportErrorClassifier.isConnectionFailure(connectTimeout)).isFalse();
    }

    @Test
    void 연결_실패는_연결_실패로_본다() {
        Throwable connectionRefused = new RuntimeException("wrapped",
                new ConnectException("Connection refused"));
        assertThat(TransportErrorClassifier.isConnectionFailure(connectionRefused)).isTrue();
        assertThat(TransportErrorClassifier.isTimeout(connectionRefused)).isFalse();
    }

    @Test
    void 타임아웃도_연결실패도_아니면_거짓이다() {
        Throwable other = new RuntimeException("boom");
        assertThat(TransportErrorClassifier.isTimeout(other)).isFalse();
        assertThat(TransportErrorClassifier.isConnectionFailure(other)).isFalse();
    }

    @Test
    void 원인이_자기_자신이어도_무한루프_없이_판정한다() {
        // getCause()가 자신을 가리키는 예외에서도 사슬 순회가 끝나고 false를 반환한다.
        Throwable selfReferencing = new RuntimeException("loop") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertThat(TransportErrorClassifier.isTimeout(selfReferencing)).isFalse();
    }
}
