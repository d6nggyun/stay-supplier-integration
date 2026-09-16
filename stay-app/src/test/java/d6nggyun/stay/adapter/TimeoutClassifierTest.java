package d6nggyun.stay.adapter;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class TimeoutClassifierTest {

    @Test
    void 직접_타임아웃이면_참이다() {
        assertThat(TimeoutClassifier.isTimeout(new TimeoutException("timed out"))).isTrue();
    }

    @Test
    void 다른_예외로_감싸인_타임아웃도_참이다() {
        Throwable wrapped = new RuntimeException("call failed", new TimeoutException("read timeout"));
        assertThat(TimeoutClassifier.isTimeout(wrapped)).isTrue();
    }

    @Test
    void 연결_타임아웃도_참이다() {
        Throwable connectTimeout = new io.netty.channel.ConnectTimeoutException("connection timed out");
        assertThat(TimeoutClassifier.isTimeout(connectTimeout)).isTrue();
    }

    @Test
    void 타임아웃이_아니면_거짓이다() {
        assertThat(TimeoutClassifier.isTimeout(new RuntimeException("boom"))).isFalse();
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
        assertThat(TimeoutClassifier.isTimeout(selfReferencing)).isFalse();
    }
}
