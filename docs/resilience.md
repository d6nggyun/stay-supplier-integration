# 재시도 · 서킷 브레이커

공급사 호출의 일시적 장애를 흡수하고(재시도), 지속 장애 공급사에는 매달리지 않도록(서킷 브레이커) 합니다. 필수 기능(타임아웃·부분 실패) 위에 얹는 확장이며, 방침은 "일시적(transient) 실패만 재시도, 서킷은 공급사별"입니다.

## 1. 라이브러리·적용 방식

Resilience4j를 **프로그래밍 방식(Reactor 연산자)**으로 적용합니다. 애노테이션(`@Retry`/`@CircuitBreaker`, AOP)이 아니라 `RetryOperator`·`CircuitBreakerOperator`를 오케스트레이터의 청크 호출 체인에 `transformDeferred`로 끼웁니다.

- 이유: 재시도·서킷을 **청크 호출·공급사 단위**로 정밀 제어해야 하는데, 애노테이션은 public 빈 메서드 경계에만 걸려 리액티브 체인 중간·인스턴스 선택과 맞지 않습니다.
- 의존성: `resilience4j-reactor`, `resilience4j-retry`, `resilience4j-circuitbreaker` (스프링 스타터 대신 필요한 모듈만).

## 2. 파이프라인에서의 위치

청크 호출(`callChunk`)에 안쪽부터 다음 순서로 얹습니다.

```
adapter.search(chunk)
  .timeout(responseTimeout)               // ① 시도 1회당 응답 타임아웃
  .transformDeferred(RetryOperator)       // ② 일시적 실패면 백오프 후 재시도(call+timeout 통째)
  .transformDeferred(CircuitBreakerOperator) // ③ 공급사 CB: open이면 즉시 차단
  .map(ChunkOutcome::success)
  .onErrorResume(ex -> ChunkOutcome.failure(classify(ex), ...))  // 에러를 상태로 흡수(맨 끝)
```

- **에러 흡수(`onErrorResume`)는 반드시 CB·retry 뒤**에 둡니다. 그래야 CB·retry가 실제 예외를 보고 판정합니다(성공으로 바꾼 뒤면 못 봄).
- **순서 근거**: CB가 가장 바깥이라 open이면 호출·재시도 자체를 건너뜁니다. 재시도는 CB 안쪽이라, "call+재시도"의 최종 결과 하나를 CB가 성공/실패로 기록합니다. 즉 일시적 blip은 재시도가 흡수하고, 그걸 거쳐도 지속 실패면 CB가 엽니다.
- 공급사 전체는 기존 `.timeout(budget)`으로 감싸, 재시도가 전체 예산을 넘지 못하게 상한을 유지합니다.

## 3. 재시도 대상 (일시적 실패만)

| 실패 | 재시도 | 이유 |
| --- | --- | --- |
| 응답/연결 타임아웃 | O | 일시적일 수 있음 |
| 연결 실패(connection refused 등 전송 오류) | O | 순간적 네트워크·기동 지연일 수 있음 |
| HTTP 5xx | O | 공급사 일시 장애일 수 있음 |
| HTTP 4xx | X | 요청 자체 문제라 재시도해도 동일 |
| Supplier B `resultCode` 실패 | X | 결정적 업무 실패 |
| 응답 역직렬화·형식 오류 | X | 결정적 |

이를 위해 `SupplierIntegrationException`에 **`retryable` 플래그**를 둡니다(사용자 노출 상태와 무관한 내부 판정용). 어댑터가 실패를 변환할 때 위 표대로 설정합니다.

- 재시도 술어: `TimeoutException 이거나 (SupplierIntegrationException && retryable)`
- 실패 종류(`SupplierFailureKind`)와 상태 매핑은 유지하되, **전송/HTTP 계층 실패(연결 실패·4xx·5xx)는 `HTTP_ERROR`로 묶고** retryable로 5xx·연결(true) vs 4xx(false)를 구분합니다. 응답을 받았으나 내용이 규약 위반이면 `PROTOCOL_ERROR`(비재시도)입니다.

## 4. 서킷 브레이커

- **공급사별 독립 인스턴스**(A·B 각각). `CircuitBreakerRegistry`에서 공급사명으로 인스턴스를 받습니다.
- open이면 청크 호출이 `CallNotPermittedException`으로 즉시 실패 → 오케스트레이터가 **`CIRCUIT_OPEN`** 상태로 분류합니다(응답만으로 "차단되어 호출 안 함"이 드러남). 차단이므로 `latencyMs`는 0에 가깝습니다.
- 전부 실패 시 대표 상태 우선순위: `CIRCUIT_OPEN` > `TIMEOUT` > `HTTP_ERROR` > `PROTOCOL_ERROR`.

## 5. 설정 (`supplier.resilience.*`)

```yaml
supplier:
  resilience:
    retry:
      max-attempts: 3          # 최초 1회 + 재시도 2회
      wait-duration-ms: 200    # 지수 백오프 기준 간격
      backoff-multiplier: 2.0
    circuit-breaker:
      failure-rate-threshold: 50       # % 이상이면 open
      sliding-window-size: 20
      minimum-number-of-calls: 10      # 이 횟수 이상 쌓여야 판정
      wait-duration-in-open-ms: 10000  # open 유지 시간
```

## 6. 기존 구조와의 관계

- 청크·부분 실패·상태 통일·전체 예산 로직은 그대로 재사용하고, 재시도·CB는 청크 호출에만 얹습니다.
- 값은 잠정이며 Mock 장애·무응답 시나리오로 검증해 운영에서 튜닝합니다.
