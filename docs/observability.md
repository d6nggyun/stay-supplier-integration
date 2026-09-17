# 연동 지표 · 모니터링

공급사 연동의 건강도를 수치로 관측합니다. 검색 오케스트레이터가 이미 공급사별 `status`·`latencyMs`를 만들고 있어(부분 실패 상태 객체), 그 지점을 지표로 계측합니다.

## 1. 무엇을 측정하나 (공급사별)

| 지표 | 의미 | 수집 |
| --- | --- | --- |
| 성공률 | `SUCCESS`·`PARTIAL_SUCCESS` / 전체 호출 | 상태별 카운터로 유도 |
| 응답 지연 | `latencyMs` 분포(avg·p95·p99 등) | Timer |
| 타임아웃 비율 | `TIMEOUT` / 전체 | 상태별 카운터로 유도 |
| 상태 분포 | `HTTP_ERROR`·`PROTOCOL_ERROR`·`CIRCUIT_OPEN`·`SKIPPED` 건수 | 상태별 카운터 |
| 서킷 상태·전이 | open/half-open/closed, 실패율 | Resilience4j → Micrometer 자동 |
| 재시도 | 재시도 횟수·성공/실패 | Resilience4j → Micrometer 자동 |

## 2. 어떻게 수집하나

- **Micrometer(계측 파사드) + Spring Boot Actuator**. 벤더 중립이라 수집 백엔드(Prometheus 등)를 교체해도 계측 코드는 그대로입니다.
- 오케스트레이터가 검색 종료 시 각 `SupplierResult`를 지표로 기록합니다.
  - `supplier.search.calls{supplier, status}` — Counter (호출 1건마다 +1)
  - `supplier.search.latency{supplier}` — Timer (`latencyMs` 기록, 실제 호출이 아닌 `SKIPPED`는 제외)
- Resilience4j의 `CircuitBreakerRegistry`·`RetryRegistry`를 Micrometer 바인더로 연결해 서킷·재시도 지표를 자동 등록합니다.

성공률·타임아웃 비율은 별도 게이지로 저장하지 않고, 상태별 카운터에서 **집계(쿼리) 시 유도**합니다. 예: 성공률 = `calls{status="SUCCESS"}` / `sum(calls)`. 원자료(카운터)만 남기고 파생 지표는 대시보드에서 계산하는 편이 유연합니다.

> **재시도 지표 읽는 법 주의**: 재시도가 전체 예산에 걸려 취소되면(무응답 등 각 시도가 응답 타임아웃을 다 쓰는 경우) Reactor의 취소는 실패가 아니라서 `resilience4j_retry_calls_total`에 집계되지 않습니다. 이는 "대기 상한을 재시도 완주보다 우선"한 의도된 동작입니다([resilience.md](resilience.md) 참고). 그 공급사의 타임아웃은 **`supplier.search.calls{status="TIMEOUT"}`** 로 확인합니다. 즉 "타임아웃 발생"은 공급사별 상태 지표로, "예산 안에 완주한 재시도"는 재시도 지표로 봅니다.

## 3. 어디로 노출

- Actuator 엔드포인트: `/actuator/metrics`(개별 조회), `/actuator/prometheus`(스크레이프 포맷)
- Prometheus가 `/actuator/prometheus`를 주기 스크레이프 → Grafana 대시보드·알림
- 노출 엔드포인트는 최소화합니다: `health`, `info`, `metrics`, `prometheus`. (운영에서는 별도 관리 포트·인증으로 보호 — 인증은 이 프로젝트 범위 밖)

## 4. 대시보드·알림 (예시 기준)

| 관측 | 알림 기준(예) |
| --- | --- |
| 공급사 성공률 | 5분 이동 성공률 < 95% |
| 타임아웃 비율 | `TIMEOUT` 비율 > 5% |
| 서킷 | `CIRCUIT_OPEN` 상태 진입 |
| 응답 지연 | p95 latency > 응답 타임아웃의 80% |

임계값은 잠정이며 운영 데이터로 튜닝합니다.

## 5. 범위

- 계측·노출까지 경량으로 구현합니다. Prometheus 서버·Grafana 대시보드 구성과 알림 룰은 인프라 영역이라 이 저장소 범위 밖으로 두고 방향만 남깁니다.
- 분산 추적(Tracing)·로그 상관관계(correlation id)는 향후 확장으로 둡니다.
