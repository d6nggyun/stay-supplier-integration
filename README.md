# stay-supplier-integration

서로 다른 외부 숙박 상품 공급사(Supplier A·B)의 API를 하나의 표준 숙박 상품 모델로 흡수해, 단일 통합 검색 API로 제공하는 백엔드입니다. 고객은 상품이 어느 공급사에서 왔는지와 무관하게 동일한 형태의 검색 결과를 받습니다.

## 기술 스택

| 항목 | 선택 | 근거 |
| --- | --- | --- |
| Language | Java 21 | 코드 전반을 직접 설명할 수 있어야 하므로 익숙한 언어를 택했습니다. |
| Framework | Spring Boot 3.5.16 | 3.5 기반으로 재시도 정책(Resilience4j 등)을 검토한 경험이 있어 확장 범위까지 이어가기 좋고, 레퍼런스가 많아 검증 비용이 낮습니다. |
| 실행 모델 | MVC + WebClient | WebFlux 전면 도입 없이 서버는 서블릿을 유지하고, 공급사 호출 구간만 리액티브로 둡니다. |
| DB | H2 (file 모드) + JPA | 저장 대상이 매핑 테이블 2개뿐이라 DB 종류가 설계에 영향을 주지 않고, 추가 설치 없이 실행할 수 있습니다. 저장 구조가 단순해 다른 RDBMS로도 큰 변경 없이 교체할 수 있습니다. |
| Mock | 별도 모듈, 포트 9090 | 같은 포트를 쓰면 자기 자신을 호출해 스레드가 묶이므로 분리합니다. |
| 테스트 | JUnit 5 + MockWebServer | 타임아웃·지연 시나리오 재현이 간단합니다. |
| API 문서 | SpringDoc + Swagger UI | 응답 스키마 자체가 설계 결정이므로 문서로 노출해 호출로 검증합니다. |
| 견고성 | Resilience4j (Reactor 연산자) | 재시도·서킷 브레이커를 청크 호출 체인에 얹어 일시적 장애를 흡수하고 지속 장애 공급사를 차단합니다. |

## 모듈 구조

멀티모듈 Gradle 프로젝트입니다. 루트는 빌드 조율만 담당하고, 실제 코드는 두 서브프로젝트에 있습니다.

```
stay/ (루트 — 집계자)
├── stay-app/       본 애플리케이션 (통합 검색)
└── mock-supplier/  Supplier A·B를 흉내 내는 Mock (포트 9090)
```

## 빌드·실행

Java 21 툴체인이 필요합니다. 별도 설치 없이 Gradle 래퍼로 빌드합니다.

```bash
./gradlew build
```

애플리케이션 실행:

```bash
./gradlew :stay-app:bootRun
```

Mock 공급사 실행 (포트 `9090`):

```bash
./gradlew :mock-supplier:bootRun
```

Mock은 별도 모듈로 분리해 애플리케이션이 외부 공급사로 호출합니다. 실제 외부 상용 API는 호출하지 않습니다.

애플리케이션이 뜨면 다음을 사용할 수 있습니다.

| 용도 | URL |
| --- | --- |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI 스펙 | http://localhost:8080/v3/api-docs |
| H2 콘솔(개발용) | http://localhost:8080/h2-console |

## 동작 테스트 (Swagger UI)

로컬에서 Swagger UI로 직접 호출해 확인하는 순서입니다.

1. **Mock과 앱을 실행**합니다(둘 다 떠 있어야 함).
   - Mock: `./gradlew :mock-supplier:bootRun` (포트 9090)
   - App: `./gradlew :stay-app:bootRun` (포트 8080)
   - 앱 기동 시 Mock이 떠 있으면 초기 동기화가 자동으로 돌아 매핑이 적재됩니다.
2. **Swagger UI**를 엽니다: http://localhost:8080/swagger-ui.html
3. **매핑 적재(선택)** — `관리 · POST /api/v1/admin/catalog-sync` 실행 → `200`. 즉시 재적재용이며, 자동 동기화가 이미 돌았다면 생략해도 됩니다.
4. **정상 검색** — `검색 · GET /api/v1/stays/search`에 `checkIn=2026-09-01`, `checkOut=2026-09-04`, `adults=2`, `children=0` → `200`. `results`에 A·B 상품이 병합되고 `suppliers`가 모두 `SUCCESS`.
5. **부분 실패·타임아웃 재현** — Mock 모드를 바꿔 다시 검색합니다(Mock은 Swagger가 없어 터미널에서 전환).
   - 장애: `curl -X POST "http://localhost:9090/control/b/mode?value=error"` → 재검색 시 B `PROTOCOL_ERROR`, A `SUCCESS`, HTTP `200`
   - 무응답: `curl -X POST "http://localhost:9090/control/b/mode?value=no-response"` → 약 3초 후 B `TIMEOUT`, A `SUCCESS`, HTTP `200`
   - 복귀: `curl -X POST "http://localhost:9090/control/b/mode?value=normal"`
6. **전체 실패 응답 확인**
   - 두 공급사 모두 장애(`a`·`b` 모두 `error`) → 검색 `502`, 본문에 공급사별 상태
   - 매핑이 하나도 없을 때(데이터 초기화 후 Mock 없이 앱만 기동) → 검색 `503`, 모든 공급사 `SKIPPED`

> H2는 file 모드라 매핑이 재시작 후에도 남습니다. DB 파일은 `:stay-app:bootRun`의 작업 디렉터리 기준이라 **`stay-app/data/stay.mv.db`** 에 생성됩니다(루트가 아님). 매핑 없는 상태(503)를 보려면 **앱을 먼저 종료**하고 `stay-app/data`를 지운 뒤 **Mock 없이 앱만** 기동하세요. 매핑이 남아 있고 Mock이 없으면 공급사 호출이 전부 실패해 `502`가 납니다.

## 핵심 설계 의사결정

각 결정의 상세 근거는 [docs/](docs/) 문서에 있습니다. 아래는 요약입니다.

### 통합 숙박 상품 모델 · [docs/domain-model.md](docs/domain-model.md)

- **요금 기준은 세금 포함 총액(gross)** 입니다. 한 공급사는 세액을 분리해 주지 않으므로, 두 공급사가 모두 만들 수 있는 유일한 공통 분모가 세금 포함 총액입니다. 비교를 위한 1박 평균가(총액 ÷ 박수, 내림)를 함께 제공합니다.
- **예약 가능 객실 수는 날짜별 재고의 최솟값**입니다. 연박 전체를 예약할 수 있어야 하므로 하루라도 0이면 0이며, 예약 불가 상품도 제거하지 않고 `0`으로 노출합니다.
- **조식 포함 여부는 별도 필드**로 둡니다. 같은 객실이라도 공급사마다 달라 가격 비교의 전제가 됩니다.
- **통화는 환산하지 않고** 공급사가 준 ISO 4217 코드를 그대로 사용합니다.
- **표시 메타데이터(이름·최대 수용 인원)의 출처는 재고·요금 실시간 응답**입니다. 매핑에 저장하는 값은 참고용 스냅샷이며, 동기화가 밀려도 표시 값이 stale되지 않도록 하기 위함입니다.

### 코드 ↔ 내부 식별자 매핑 · [docs/mapping.md](docs/mapping.md)

- 숙소 매핑 키는 `(supplier, stayCode)`, 객실 타입 매핑 키는 `(supplier, stayCode, roomTypeCode)`입니다. 객실 타입 코드는 숙소 내부에서만 유일하기 때문입니다.
- 내부 식별자는 DB 자동 증가(`IDENTITY`)로 발급하고, `(supplier, code)` 유니크 제약 + upsert로 "같은 공급사 상품은 항상 같은 내부 식별자"를 보장합니다. 대량 배치가 필요한 규모가 되면 `SEQUENCE` + 배치로 전환하는 경로를 남겼습니다.
- 서로 다른 공급사의 동일 상품 추정 병합은 공통 키가 없어 오병합 위험이 있으므로 **후순위**(확장 범위)로 둡니다.
- 숙소 목록은 정적이고 재고·요금은 동적이므로 같은 주기로 처리하지 않습니다. 동기화는 주기 스케줄러(첫 실행이 기동 직후 초기 적재를 겸함)와 수동 트리거로 하며, 실패해도 기존 매핑으로 서비스하고 다음 주기에 복구합니다.

### 공급사 어댑터 · [docs/supplier-adapter.md](docs/supplier-adapter.md)

- 공급사별 요청/응답 형식과 실패 표현은 어댑터 안에서만 다루고 도메인으로 새지 않게 합니다.
- **실패 판정 통일**: 한 공급사는 HTTP 4xx/5xx로, 다른 공급사는 HTTP 200 + 본문 `resultCode`로 실패를 표현합니다. 어댑터가 이를 동일한 연동 실패로 변환합니다.
- 신규 공급사는 전용 DTO·어댑터 구현·설정·테스트 추가만으로 붙고, 도메인과 검색 API는 수정하지 않습니다.

### 통합 검색 · [docs/search-flow.md](docs/search-flow.md)

- 공급사 호출은 WebClient로 **병렬 처리**하고, 50개 초과 시 청크로 분할해 동시성 상한을 둡니다.
- 리액티브 경계는 서비스 내부로 한정하고, 서비스 끝에서 `block()`으로 받아 컨트롤러는 동기 반환합니다.

### 연동 견고성 · [docs/failure-handling.md](docs/failure-handling.md)

- **타임아웃은 개별 호출과 전체 요청 예산 두 층위**로 둡니다.
- **부분 실패는 HTTP 200 + 공급사별 상태 객체**로 표현합니다. 쓸 수 있는 결과가 하나도 없으면 5xx(전부 실패 502 / 전부 `SKIPPED` 503)로 응답하되, 본문에 상태를 담아 원인을 전달합니다.
- 매핑이 없어 호출하지 않은 공급사는 `SKIPPED`로 표기합니다.
- **재시도·서킷 브레이커**는 확장으로 구현했습니다. 일시적 실패(타임아웃·연결 실패·5xx)만 재시도하고, 지속 실패 공급사는 서킷을 열어 차단(`CIRCUIT_OPEN`)합니다. (상세 · [docs/resilience.md](docs/resilience.md))

## API

통합 검색 API의 요청·응답과 필드는 [docs/api.md](docs/api.md)에 정의되어 있습니다.

```
GET /api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0
```

응답은 내부 식별자 기준의 상품 목록(`results`)과 공급사별 처리 상태(`suppliers`)로 구성되며, 공급사 원본 코드는 노출하지 않습니다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | 아키텍처 · 계층 구조 · 실행 모델 · 핵심 흐름 |
| [docs/domain-model.md](docs/domain-model.md) | 통합 숙박 상품 모델 (요금 · 재고 · 조식 · 메타데이터 출처) |
| [docs/mapping.md](docs/mapping.md) | 코드 ↔ 내부 식별자 매핑 · 동기화 |
| [docs/supplier-adapter.md](docs/supplier-adapter.md) | 공급사 어댑터 · 실패 판정 통일 · 신규 공급사 |
| [docs/search-flow.md](docs/search-flow.md) | 통합 검색 흐름 · 병렬 · 청크 · 타임아웃 |
| [docs/failure-handling.md](docs/failure-handling.md) | 부분 실패 처리 · 공급사별 상태 객체 |
| [docs/resilience.md](docs/resilience.md) | 재시도 · 서킷 브레이커 |
| [docs/mock-supplier.md](docs/mock-supplier.md) | Mock 공급사 (정상 · 장애 · 무응답) |
| [docs/testing.md](docs/testing.md) | 테스트 전략 |
| [docs/api.md](docs/api.md) | 통합 검색 API 명세 |
| [JOURNAL.md](JOURNAL.md) | 일자별 의사결정 · 근거 · AI 활용 기록 |

프로젝트 작업 지침과 확정 설계 정책은 [CLAUDE.md](CLAUDE.md)에 정리되어 있습니다.

## 구현 범위

- **필수**: 통합 모델, 코드↔식별자 매핑, 공급사 어댑터, 통합 검색 API, 타임아웃·부분 실패·실패 판정 통일, Mock 공급사, 설계 근거 문서
- **확장(구현함)**: 재시도, 서킷 브레이커 ([docs/resilience.md](docs/resilience.md))
- **확장(향후)**: 캐시, 정규화 실패 격리, 중복 상품 병합, 다중 통화, 예약 대행
- **범위 밖**: 인증·인가, 결제, 관리자 기능, 프론트엔드, 실제 외부 API 호출, 지역·키워드 검색 필터, 정렬·페이징
