package d6nggyun.stay.application.search;

import d6nggyun.stay.infrastructure.persistence.entity.NormalizationFailure;
import d6nggyun.stay.infrastructure.persistence.repository.NormalizationFailureRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 정규화 실패 격리 레코드를 저장한다. best-effort로, 저장 실패가 검색을 막지 않는다.
 * 리액티브 구간에서 수집한 항목을 검색 종료 후(블로킹 이후) 서블릿 스레드에서 한 번에 저장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NormalizationFailureRecorder {

    private final NormalizationFailureRepository repository;
    private final MeterRegistry meterRegistry;

    public void recordAll(List<NormalizationFailure> failures) {
        if (failures.isEmpty()) {
            return;
        }
        try {
            repository.saveAll(failures);
            meterRegistry.counter("supplier.search.normalization.failures").increment(failures.size());
        } catch (Exception e) {
            // 격리 기록 실패는 검색 결과에 영향을 주지 않는다(부분 성공은 이미 반환됨).
            log.warn("정규화 실패 격리 기록에 실패했습니다(무시). count={}", failures.size(), e);
        }
    }
}
