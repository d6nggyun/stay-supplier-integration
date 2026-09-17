# Process Journal

## [Day 1]

### 수행 내용

1. 요구사항 분석
2. 정책 정리
3. 스택 선정

## 1. 요구사항 분석

### 1.1. 문제 정의

외부 숙박 상품 공급사 2곳의 API를 연동해, 서로 다른 표현을 하나의 표준 숙박 상품 모델로 흡수하고 단일 검색 API로 제공합니다. 고객은 상품이 어느 공급사에서 왔는지와 무관하게 동일한 형태의 결과를 봅니다.

### 1.2. 공급사 스펙 차이

| 항목 | Supplier A | Supplier B | 흡수 방식 |
| --- | --- | --- | --- |
| 요금 단위 | 날짜별 1박 단가 | 숙박 기간 전체 총액 | B는 날짜별로 쪼갤 수 없으므로 총액을 공통 기준으로 사용합니다. |
| 세금 | 별도 + 세액 제공 | 포함, 세액 미제공 | A는 합산 가능하지만 B는 분리할 수 없으므로 포함으로 기준을 통일합니다. |
| 실패 표현 | HTTP 4xx/5xx | 항상 HTTP 200 + 본문 `resultCode` | 어댑터에서 동일한 예외 타입으로 변환합니다. |
| 재고 | 날짜별 잔여 객실 수 | 날짜별 잔여 객실 수 | 동일하므로 변환하지 않습니다. |
| 조식 | 객실별 boolean | 객실별 boolean | 동일하지만 같은 객실이라도 값이 다를 수 있습니다. |
| 식별자 | `hotelCode` / `roomTypeCode` | `propertyId` / `roomId` | 이름만 다르고 유일성 범위는 동일합니다. |

두 공급사의 공통 규약은 다음과 같습니다.

- 숙소 > 객실 타입의 2단계 구조입니다.
- 객실 타입 코드는 숙소 내에서만 유일합니다.
- 재고·요금 조회는 숙소 코드 목록을 받아 bulk 처리하며, 최대 50개까지 조회합니다.
- 지역 검색은 지원하지 않습니다.

### 1.3. 조회가 2단계인 이유

숙소 목록 API는 조건 없이 전체 목록만 제공하며 정적이고 변동이 적습니다. 재고·요금 API는 숙소 코드 목록을 받아야 응답하며 매번 변동합니다.

따라서 어떤 숙소를 조회할지 먼저 알고 있어야 하며, 이것이 공급사 코드와 내부 식별자의 매핑을 DB에 저장하는 이유입니다.

### 1.4. 필수 범위 체크리스트

| 번호 | 항목 | 비고 |
| --- | --- | --- |
| 1 | 통합 숙박 상품 모델 설계 | 무엇을 버릴지 판단하고 근거를 명시합니다. |
| 2 | 공급사 코드 ↔ 내부 식별자 매핑 DB 저장 | 숙소 단위와 객실 타입 단위의 2개 매핑을 저장합니다. |
| 3 | Supplier 연동 어댑터 (`WebClient`) | 공급사 DTO가 도메인으로 새지 않도록 합니다. |
| 4 | 통합 검색 API | 병렬 조회 → 정규화 → 병합 순서로 처리합니다. |
| 5 | 타임아웃 (연결·응답) | 값의 근거를 문서화합니다. |
| 6 | 부분 실패 허용 | 한쪽 공급사가 실패해도 나머지 결과로 응답합니다. |
| 7 | 실패 판정 통일 | Supplier B의 `resultCode`를 Supplier A의 4xx/5xx와 동일하게 취급합니다. |
| 8 | Mock Supplier | 정상·장애·무응답의 3가지 상황을 재현합니다. |
| 9 | 설계 근거 문서 | 문서 내용이 코드에 반영되어야 합니다. |

### 1.5. 범위 밖

인증·인가, 결제, 관리자 기능, 프론트엔드, 실제 외부 API 호출, 지역·키워드 필터, 정렬·페이징은 범위에서 제외합니다.

### 1.6. 확장 범위

재시도 정책, 서킷 브레이커, 캐시 전략, 정규화 실패 격리, 중복 상품 병합, 다중 통화, 예약 대행 흐름은 확장 범위로 분류합니다.

## 2. 정책 정리

구현 전에 확정해야 하는 판단들을 정리합니다. 잠정 표시는 아직 확정되지 않은 항목을 의미합니다.

### 2.1. 도메인 정책

## 3. 스택 선정

## 4. AI 활용 기록

AI는 선택지 생성기로 활용하고, 최종 판단은 직접 내렸습니다. AI가 제시한 근거는 대부분 다시 검토한 뒤 기록에 반영했습니다.

기록 문서의 구조를 정리할 때에도 같은 방식으로 활용했습니다.

### 4.1. 작업 방식 (모델 운용)

큰 그림, 즉 아키텍처·통합 모델·구현 순서(§11) 같은 뼈대와 이정표는 상위 모델로 먼저 정리해 문서로 고정했습니다. 이후 실제 구현·테스트·리팩터링은 이 고정된 이정표를 기준선으로 삼아 진행했습니다. 이렇게 한 이유는 두 가지입니다.

- 드리프트 방지: 매 단계가 이미 합의된 틀 안에서 움직이므로 설계에서 벗어나지 않습니다.
- 비용 효율: 판단 밀도가 높은 설계는 상위 모델로 한 번에 확정하고, 반복 작업은 가벼운 실행으로 처리해 불필요한 재논의·토큰 소모를 줄였습니다.

문서(`JOURNAL`·`docs`·`CLAUDE.md`)가 이 이정표의 단일 출처 역할을 합니다.

### 4.2. 어떻게 물었는가

목적에 따라 질문 형태를 달리했습니다.

| 유형 | 실제 프롬프트 | 의도 |
| --- | --- | --- |
| 선택지 요청 | "이 항목의 대안을 3~4개 제시하고, 각각 얻는 것과 잃는 것, 구현 비용을 표로 정리해줘" | 답 하나가 아니라 후보군을 받아 비교 대상으로 삼기 위함입니다. 처음부터 정답을 요구하면 먼저 떠오른 안을 근거까지 붙여 밀어붙일 가능성이 있기 때문입니다. |
| 근거 검증 | "이 근거에 대한 타당성을 점검하고, 더 강한 근거가 있으면 제시해줘. 검증 안 된 주장은 표시해줘" | AI가 제시한 근거를 그대로 받아들이지 않고 재검토하기 위함입니다. 이 질문에서 근거가 바뀐 경우가 가장 많았습니다. |
| 원문 대조·재검토 | "요구사항에는 정확히 어떻게 쓰여 있는지 다시 확인해보자. 이 항목을 구현해야 하는지, 설계만으로 충분한지도 같이 검토해줘." | AI의 요약과 판단을 원문과 대조한 뒤, 요구사항의 범위와 기능의 성격을 재검토하기 위함입니다. |

개별 질문 외에 대화 전체에 적용되는 사전 지시도 설정했습니다.

- 확실하지 않은 내용은 단정하지 말고 "추측"이라고 명시할 것
- 버전·설정값처럼 사실 확인이 필요한 항목은 근거를 확인하고 답할 것. 확인하지 못했으면 그렇다고 말할 것
- 첨부한 문서에 없는 내용을 있는 것처럼 채워 넣지 말 것
- 요청 범위 밖의 작업을 독자적으로 진행하지 말 것

### 4.3. 수용·수정·거부 기록

| 대상 | AI가 제시한 것 | 판단 | 이유 |
| --- | --- | --- | --- |
| 문서 구조 | 기록 문서를 요구사항 분석 / 정책 정리 / 스택 선정 3부로 나누고, 정책은 항목·선택지·결정·근거 4열 표로 정리하는 구조를 제안 | 수용 / 항목 조정 | 표 형태는 결정과 근거를 나란히 대조할 수 있어 그대로 썼습니다. 다만 일정 계획 표와 패키지 구조는 이 문서의 역할과 맞지 않아 뺐고, 소제목 번호 체계는 직접 지정했습니다. |
| Spring Boot 버전 | 3.5 채택. 근거로 4.x의 Jackson 3 마이그레이션 리스크, Resilience4j·SpringDoc 호환 미검증을 제시 | 수용 | 호환성 관련 주장은 직접 확인하지 않은 내용이므로 근거에서 제외했습니다. Spring Boot 3.5 기반으로 재시도 정책을 검토한 경험을 바탕으로 채택했습니다. |
| 스키마 관리 | Flyway 또는 schema.sql을 스택 항목으로 제시 | 거부 | 저장 구조가 단순하여 별도 스키마 관리 도구를 추가할 필요성이 낮다고 판단했습니다. 또한 스키마 관리는 이 문서의 스택 선정 항목과 직접적인 관련이 적어 별도 항목으로 다루지 않기로 했습니다. |
| MySQL 폐기 사유 | "실행 마찰이 증가한다" | 수정 | Docker Compose로 실행할 수 있으므로 실행 부담을 핵심 근거로 삼지 않았습니다. 저장 대상이 제한적이어서 별도 DB 운영 환경을 추가해 얻는 검증 가치가 크지 않다고 판단했습니다. |
| P3 요금 기준 | 총액 단일 / 총액 + 상세 / 총액 + 1박 평균 3안 | 수용 (3안 선택) | 숙박 상품 간 비교를 위해 평균 1박 요금을 제공하고, 실제 결제 판단을 위해 숙박 전체 총액도 함께 제공하는 방향을 선택했습니다. |
| P3 반올림 | 나눗셈이 떨어지지 않을 때의 처리와 평균가 × 박수 ≠ 총액 문제를 제기 | 수용 | 평균 요금과 총액의 차이가 발생할 수 있으므로, 반올림 기준과 총액 산정 기준을 별도로 정하기로 했습니다. |
| P4 날짜별 요금 | 완전히 버림 / A만 노출 / 내부 모델에만 보존 3안과 각각의 손실 정리 | 수용 (3안 선택) | 공급사별 응답 차이를 외부 응답에 그대로 노출하지 않으면서도, 정규화와 추후 확장을 위해 날짜별 요금 정보는 내부 모델에 보존하기로 했습니다. |
| P8 동기화 | 기동 시 1회 + 수동 트리거 엔드포인트 | 보류 후 재검토 | 숙소 목록은 자주 변경되지 않지만 변경 사항을 반영할 수 있어야 하므로, 주기 동기화를 기본 경로로 두고 수동 트리거는 보조 수단으로 두기로 했습니다. |
| P8 Actuator | "Actuator에 두면 운영 조작임이 구분된다" | 거부 | Actuator 사용은 엔드포인트 위치만으로 운영 기능과 일반 기능이 구분되지 않으므로 채택하지 않았습니다. |
| P13 부분 실패 | 5xx / 206 / 200 + 실패 목록 / 200 + 공급사별 상태 객체 4안 | 수용 (4안 선택) | 공급사별 성공·실패 상태와 검색 결과를 함께 표현할 수 있어 고객 응답과 모니터링에 모두 활용하기 좋다고 판단했습니다. |
| P14 50개 초과 | "설계만 남기고 구현하지 않음"으로 분류 | 재검토 요청 후 수정 | 원문상 필수 구현 항목은 아니므로 우선 분할 호출 설계를 기록하되, 구현 비용이 낮아 여력이 있으면 구현하기로 했습니다. |

## [Day 2]

### 수행 내용

1. 전체 아키텍처 설계
2. 표준 숙박 상품 모델 설계
3. 공급사 코드와 내부 식별자 매핑 설계
4. Supplier Adapter 구조 설계
5. 통합 검색 흐름 설계
6. 연동 실패 처리 설계
7. API 및 테스트 전략 설계

## 1. 설계 목표

이번 설계의 목표는 서로 다른 외부 공급사의 API를 도메인 계층에서 직접 다루지 않고, 내부 표준 모델로 변환해 하나의 검색 API로 제공하는 것입니다. 고객은 상품이 어느 공급사에서 왔는지와 무관하게 동일한 형태의 검색 결과를 받습니다.

핵심 흐름은 다음과 같습니다.

```
공급사 숙소 목록 조회
    ↓
공급사 코드와 내부 식별자 매핑 저장
    ↓
고객 검색 요청 수신
    ↓
DB에서 공급사별 숙소 코드 조회
    ↓
공급사별 숙소 코드를 50개 단위로 분할
    ↓
Supplier A·B 병렬 호출
    ↓
공급사 응답을 표준 모델로 변환
    ↓
재고·요금 계산 및 결과 병합
    ↓
공급사별 처리 상태와 함께 응답
```

전체 도메인을 고려하되, 이번 구현에서는 검색 흐름이 처음부터 끝까지 동작하는 것을 최우선으로 둡니다. 인증·인가, 결제, 프론트엔드, 실제 외부 API 연동, 지역 검색, 정렬·페이징은 이번 구현 범위에서 제외합니다.

## 2. 전체 아키텍처

애플리케이션은 다음 계층으로 나눕니다.

```
Controller
    ↓
Search Application Service
    ↓
Supplier Orchestrator
    ├── Supplier A Adapter
    └── Supplier B Adapter
    ↓
Normalizer
    ↓
Standard Stay Offer

Catalog Sync Service
    ↓
Supplier Adapter
    ↓
Mapping Repository
    ↓
Relational Database
```

- **Controller**: HTTP 요청과 응답만 담당합니다. 공급사별 DTO나 공급사 코드를 직접 다루지 않고, 고객 검색 조건을 내부 `SearchCriteria` 객체로 변환해 애플리케이션 서비스에 전달합니다.
- **Application Service**: 고객의 검색 요청을 하나의 유스케이스로 조정합니다. 검색 조건 검증, DB 매핑 조회, 공급사별 코드 그룹화, 어댑터 호출, 결과 병합, 공급사별 처리 상태 생성을 담당합니다.
- **Supplier Adapter**: 각 공급사의 API 형식과 통신 방식을 캡슐화합니다. 요청 파라미터, 응답 DTO, 실패 표현 방식은 어댑터 내부에서만 알고 있어야 합니다.
- **Domain**: 공급사 API의 필드명과 무관한 내부 표준 모델을 관리합니다. 공급사별 식별자는 내부 식별자로 치환되어 처리되며, 최종 응답에는 내부 식별자만 사용합니다.
- **Persistence**: 숙소와 객실 타입의 공급사 코드 및 내부 식별자 매핑만 저장합니다. 재고와 요금은 검색 시점의 외부 응답이 원본이므로 저장하지 않습니다.

## 3. 표준 숙박 상품 모델

### 3.1 검색 조건

```
SearchCriteria
- checkIn
- checkOut
- adults
- children
```

날짜 경계는 체크아웃일을 숙박일에 포함하지 않습니다. 예를 들어 `2026-09-01`부터 `2026-09-04`까지는 3박입니다.

```
long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
```

검색 조건은 `checkIn < checkOut`, `adults > 0`, `children >= 0`을 검증하며 인원 값은 음수가 될 수 없습니다.

### 3.2 표준 검색 결과

내부 도메인에서는 검색 결과를 `StayOffer`로 표현합니다.

```
StayOffer
- internalStayId
- stayName
- internalRoomTypeId
- roomTypeName
- maxOccupancy
- availableRoomCount
- supplier
- breakfastIncluded
- price
- dailyAvailability
```

`dailyAvailability`는 날짜별 재고를 제공하는 공급사의 경우 내부 모델에 보존하되, 검색 목록 응답에서는 공급사 간 응답 구조를 동일하게 유지하기 위해 직접 노출하지 않습니다.

`stayName`, `roomTypeName`, `maxOccupancy`는 검색 시점의 재고·요금 응답을 출처로 삼습니다. 매핑에 저장하는 값은 참고용 스냅샷이며, 동기화가 밀려도 표시 값이 stale되지 않도록 하기 위함입니다.

### 3.3 요금 모델

고객에게 제공하는 요금 기준은 세금 포함 총액입니다.

```
Price
- currency
- grossTotalAmount
- averageNightlyAmount
```

Supplier A는 날짜별 세전 요금과 세금이 따로 제공되므로 합산합니다.

```
grossTotalAmount = Σ(nightlyRate + taxAmount)
```

Supplier B는 세금이 포함된 숙박 전체 총액을 제공하므로 그대로 사용합니다.

```
grossTotalAmount = totalPrice
```

1박 평균가는 표시용 파생 값으로 계산하며, 나누어떨어지지 않으면 내림합니다.

```
averageNightlyAmount = grossTotalAmount / nights
```

총액이 원본 값이고 평균가는 표시용 값이므로, 평균가에 숙박일수를 곱한 값이 총액과 다를 수 있음을 허용합니다. 통화는 환산하지 않고 공급사가 제공한 ISO 4217 코드를 그대로 사용합니다.

### 3.4 재고 모델

재고는 날짜별 잔여 객실 수를 기반으로 계산합니다.

```
availableRoomCount = min(각 숙박일의 remainingRooms)
```

연박 전체를 예약할 수 있어야 하므로 하루라도 재고가 0이면 예약 가능 객실 수는 0입니다. 요청 숙박일 중 응답에 빠진 날짜가 있으면 그날 재고를 0으로 취급합니다(방어적 처리). 예약 불가 상품은 검색 응답에서 제거하지 않고 `availableRoomCount: 0`으로 노출해, 재고 판정 결과를 응답으로 확인할 수 있게 합니다.

### 3.5 조식 정보

조식 포함 여부는 요금에 흡수하지 않고 별도 필드로 제공합니다. 같은 객실 타입이라도 공급사에 따라 조식 포함 여부가 다를 수 있어, 이를 제거하면 가격 비교의 전제가 달라지기 때문입니다.

## 4. 공급사 코드와 내부 식별자 매핑

### 4.1 숙소 매핑

숙소 매핑의 논리적 키는 `(supplier, supplierStayCode)`입니다. 같은 공급사 코드가 다시 조회되면 기존 내부 숙소 식별자를 재사용합니다.

### 4.2 객실 타입 매핑

객실 타입 코드는 숙소 내부에서만 유일하므로, 매핑 키는 `(supplier, supplierStayCode, supplierRoomTypeCode)` 세 값으로 구성합니다. 객실 타입 코드만 저장하면 서로 다른 숙소에서 같은 객실 코드가 사용될 때 충돌할 수 있습니다.

### 4.3 내부 식별자 생성

내부 식별자는 DB 시퀀스 기반의 숫자 식별자를 사용합니다. 숙소·객실 타입 매핑이 새로 생성될 때 내부 ID를 발급하고, 기존 공급사 코드가 존재하면 기존 ID를 재사용합니다. `(supplier, supplierCode)`에 유니크 제약을 두고 upsert로 저장합니다.

공급사 코드 문자열을 내부 ID로 그대로 쓰지 않는 이유는 외부 코드와 내부 식별자를 분리하기 위해서입니다. Supplier A와 B가 실제로 같은 숙소를 제공하더라도 공통 키가 없으므로 각각 별도의 내부 상품으로 관리합니다. 이름을 이용한 자동 병합은 오병합 위험이 있어 이번 구현에서는 후순위로 두고 확장 범위로 남깁니다.

### 4.4 예상 테이블

`stay_mapping`

```
- id
- supplier
- supplier_stay_code
- internal_stay_id
- stay_name
- active
- last_seen_at
- created_at
- updated_at

UNIQUE(supplier, supplier_stay_code)
UNIQUE(internal_stay_id)
```

`room_type_mapping`

```
- id
- supplier
- supplier_stay_code
- supplier_room_type_code
- internal_room_type_id
- room_type_name
- max_occupancy
- active
- last_seen_at
- created_at
- updated_at

UNIQUE(supplier, supplier_stay_code, supplier_room_type_code)
UNIQUE(internal_room_type_id)
```

요금과 재고는 검색 시점마다 달라지는 동적 데이터이므로 저장하지 않습니다.

### 4.5 숙소 목록 동기화

숙소 목록은 비교적 정적이고 재고·요금은 매번 달라지므로 같은 주기로 처리하지 않습니다. 동기화는 (1) 애플리케이션 기동 시 1회, (2) 설정된 주기에 따른 자동 동기화, (3) 즉시 반영이 필요한 경우의 수동 트리거로 둡니다.

동기화 중 한 공급사의 API가 실패하더라도 전체 애플리케이션을 종료하지 않습니다. 기존 매핑이 있으면 기존 매핑으로 서비스하고, 없으면 해당 공급사만 검색 대상에서 제외하며, 실패는 경고 로그로 기록하고 다음 주기 동기화에서 복구를 시도합니다.

## 5. Supplier Adapter 설계

공급사별 어댑터는 동일한 내부 인터페이스를 구현합니다.

```java
public interface SupplierAdapter {
    SupplierType supplier();
    Mono<SupplierCatalog> fetchCatalog();
    Mono<SupplierSearchResult> search(SearchCriteria criteria, List<String> supplierStayCodes);
}
```

### 5.1 어댑터 내부 책임

각 어댑터는 요청 URL 생성, 인증 헤더 설정, 요청 DTO 생성, WebClient 호출, 응답 역직렬화, HTTP 상태 확인, 실패 코드 확인, 표준 모델 변환을 담당합니다. 도메인 계층은 공급사가 어떤 필드명을 쓰는지, HTTP 200 상태에서 실패를 표현하는지, 요금이 날짜별인지 총액인지 알지 못해야 합니다.

정규화 과정에서 매핑에 없는 객실 코드 등 변환할 수 없는 항목은 결과에서 스킵하고 경고 로그를 남깁니다. 변환 불가 응답을 격리·기록하는 정규화 실패 격리는 확장 범위로 두고 이번에는 설계 방향만 남깁니다.

### 5.2 Supplier B 실패 처리

Supplier B는 장애가 발생해도 HTTP 200을 반환할 수 있으므로, 응답 본문의 `resultCode`를 함께 확인합니다.

```
resultCode == "0000" → 정상 응답
resultCode != "0000" → SupplierIntegrationException으로 변환
```

Supplier A의 HTTP 4xx·5xx와 Supplier B의 `resultCode` 오류는 어댑터 바깥에서는 동일한 공급사 연동 실패로 처리합니다.

### 5.3 신규 공급사 추가 방식

새로운 Supplier C를 추가할 때 기존 도메인이나 검색 API를 수정하지 않는 것을 목표로 합니다. 전용 응답 DTO 작성, `SupplierAdapter` 구현, 표준 모델 변환, 설정값 등록, 빈 등록, 테스트 추가로 작업이 국한됩니다. 도메인 계층은 어댑터 인터페이스만 의존하므로 공급사별 API 형식이 도메인으로 확산되지 않습니다.

## 6. 통합 검색 흐름

### 6.1 검색 처리 순서

1. 검색 조건 검증
2. DB에서 활성(`active = true`) 숙소 매핑 조회
3. 공급사별 숙소 코드 그룹화
4. 공급사별 숙소 코드를 최대 50개 단위로 분할
5. Supplier A·B 병렬 호출
6. 각 응답을 표준 모델로 변환
7. 재고·요금 계산
8. 내부 식별자 매핑
9. 결과와 공급사별 상태를 합쳐 응답

### 6.2 병렬 호출

공급사 호출은 `WebClient`와 `Mono`·`Flux`로 병렬 처리합니다. 각 호출은 서로 독립적이어야 하며, 한 공급사가 실패해도 다른 공급사의 결과가 취소되지 않아야 합니다. 숙소 코드가 50개를 초과하면 청크 단위로 분할하고 동시성 상한을 둡니다. 초기 구현에서는 예시 데이터가 50개 미만이므로 단일 청크 흐름을 우선 완성하고 청크 분할은 확장 가능하도록 설계합니다.

### 6.3 타임아웃

타임아웃은 **개별 호출**과 **전체 요청 예산** 두 층위로 둡니다. 개별 호출은 Connection 1초, Response 3초를 청크 호출 하나당 적용합니다. 청크가 여러 개로 나뉘고 동시성 상한에 걸리면 개별 타임아웃만으로는 고객 체감 지연이 누적되므로, 요청 전체에 상한(잠정 5~6초)을 별도로 둡니다. 전체 예산을 초과하면 그때까지 도착한 결과로 응답하고 완료되지 못한 공급사는 `TIMEOUT`으로 처리합니다. 실제 값은 Mock 공급사의 무응답 시나리오로 검증하고 구현 과정에서 조정합니다.

## 7. 부분 실패 처리

공급사 호출 결과는 예외를 상위로 전파하지 않고 공급사별 처리 결과로 감쌉니다.

```
SupplierResult
- supplier
- status
- latencyMs
- resultCount
- errorCode
- errorMessage
- offers
```

상태 값은 `SUCCESS`, `PARTIAL_SUCCESS`, `TIMEOUT`, `HTTP_ERROR`, `PROTOCOL_ERROR`, `NO_DATA`, `SKIPPED`입니다. 실패 표현이 서로 달라도 어댑터 바깥에서는 동일한 규칙으로 상태를 통일합니다. A의 4xx/5xx는 `HTTP_ERROR`, 무응답은 `TIMEOUT`, B의 `resultCode != "0000"`과 역직렬화 오류는 `PROTOCOL_ERROR`, 매핑이 없어 호출하지 않은 공급사는 `SKIPPED`로 둡니다. `NO_DATA`는 정상 응답인데 조회 대상 자체가 0건인 경우에만 쓰고, 결과가 비어 있는 일반적인 경우는 `SUCCESS`(`resultCount: 0`)로 둡니다.

한 공급사만 실패한 경우에도 전체 검색 API는 HTTP 200으로 응답하고, 실패 사실은 공급사별 상태로 본문에 드러냅니다. 다만 쓸 수 있는 결과가 하나도 없으면 상류 오류로 봅니다. 호출한 공급사가 전부 실패하면 502, 매핑이 없어 호출 대상이 하나도 없으면(전부 `SKIPPED`) 503으로 응답하되, 본문에는 공급사별 상태를 그대로 담아 원인을 전달합니다.

청크가 여러 개로 나뉘고 그중 일부만 실패하면, 성공한 청크의 결과만 병합하고 해당 공급사의 상태를 `PARTIAL_SUCCESS`로 표기합니다.

실패 목록 대신 공급사별 상태 객체를 쓰는 이유는 성공·실패와 응답 지연을 함께 담아 모니터링 지표로 이어갈 수 있기 때문입니다. 재시도와 서킷 브레이커는 필수 구현 이후 확장 사항으로 둡니다.

## 8. 검색 API 초안

요청

```
GET /api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0
```

응답

```json
{
  "results": [
    {
      "stayId": 1,
      "stayName": "Riverside Hotel Seoul",
      "roomTypeId": 1,
      "roomTypeName": "Deluxe Twin",
      "maxOccupancy": 2,
      "availableRoomCount": 1,
      "supplier": "SUPPLIER_A",
      "breakfastIncluded": false,
      "price": { "currency": "KRW", "grossTotalAmount": 429000, "averageNightlyAmount": 143000 }
    }
  ],
  "suppliers": [
    { "supplier": "SUPPLIER_A", "status": "SUCCESS", "latencyMs": 180, "resultCount": 1 },
    { "supplier": "SUPPLIER_B", "status": "SUCCESS", "latencyMs": 220, "resultCount": 1 }
  ]
}
```

검색 응답에서는 내부 숙소·객실 타입 식별자, 이름, 최대 수용 인원, 예약 가능 객실 수, 출처 공급사, 세금 포함 요금, 부분 실패 정보를 제공하며, 공급사 원본 코드가 응답 식별자로 노출되지 않도록 합니다. `suppliers[]`에는 매핑이 없어 호출하지 않은 공급사도 `SKIPPED`로 포함해, 어느 공급사가 왜 결과에 없는지 응답만으로 드러나게 합니다. 정상(부분 실패 포함)은 200, 전부 실패는 502, 전부 `SKIPPED`는 503으로 응답합니다.

## 9. Mock Supplier 설계

Mock Supplier는 본 애플리케이션과 별도 모듈로 분리하고 포트 `9090`을 사용합니다. 같은 포트를 쓰면 자기 자신을 호출하게 되어 스레드가 묶이면서 실제 외부 연동 문제와 구분하기 어려워지기 때문입니다. Mock은 `normal`, `error`, `no-response` 상태를 지원합니다. 장애는 Supplier A가 HTTP 503, Supplier B가 HTTP 200 + `resultCode: E503`으로 재현하고, 무응답은 연결은 되지만 일정 시간 응답하지 않는 방식으로 재현합니다. Mock의 목적은 정교한 상품 데이터가 아니라 연동 흐름의 정상·실패·무응답 재현이므로 복잡도는 낮게 유지합니다.

## 10. 테스트 전략

- **도메인 단위 테스트**: 숙박일수 계산, 날짜별 재고 최솟값, 재고 0 판정, 세전+세금 합산, 총액 사용, 1박 평균가 내림, 통화 보존, 조식 보존
- **어댑터 테스트**: A 정상/503 변환, B 정상/실패 코드 변환, 잘못된 응답 형식 처리
- **검색 서비스 테스트**: 두 공급사 성공, 한쪽 실패 후 나머지 반환, 무응답 타임아웃, 두 공급사 모두 실패, 청크 분할, 내부 식별자 변환
- **API 통합 테스트**: 정상 요청, 잘못된 날짜·인원, 예약 불가 0 노출, 부분 실패 200, 내부 식별자 포함 확인

## 11. 구현 순서

1. 표준 도메인 모델과 검색 조건
2. 매핑 엔티티와 Repository
3. H2 File 모드 및 JPA 설정
4. Mock Supplier 모듈
5. Supplier A Adapter
6. Supplier B Adapter
7. 숙소 목록 동기화 서비스
8. 통합 검색 오케스트레이터
9. 타임아웃·부분 실패 처리
10. 검색 Controller 및 응답 DTO
11. 단위·통합 테스트
12. README와 설계 문서 정리

Day 2에서는 코드 작성보다 계층 간 책임과 데이터 흐름을 먼저 확정하고, 이후 설계 문서의 내용과 실제 코드가 어긋나지 않도록 구현합니다.

## 12. Day 2의 최종 결정

- 도메인은 공급사 API DTO에 의존하지 않습니다.
- 숙소와 객실 타입은 공급사 코드와 내부 식별자를 별도로 관리합니다.
- 객실 타입 매핑 키는 `(공급사, 숙소 코드, 객실 코드)`로 구성합니다.
- 요금의 공통 기준은 세금 포함 총액입니다.
- 예약 가능 객실 수는 날짜별 재고의 최솟값입니다.
- 예약 불가 상품은 `0`으로 응답합니다.
- Supplier A와 B의 동일 상품 추정 병합은 후순위로 둡니다.
- 공급사 호출은 WebClient로 병렬 처리합니다.
- Supplier B의 HTTP 200 실패 응답은 어댑터에서 실패로 변환합니다.
- 한 공급사 실패가 전체 검색 실패로 이어지지 않도록 합니다.
- 숙소 목록은 기동 시·주기적·수동 방식으로 동기화합니다.
- 검색 대상은 `active = true` 매핑만 조회합니다.
- 검색 응답의 이름·최대 수용 인원은 재고·요금 실시간 응답을 출처로 삼고, 매핑 값은 참고용으로만 둡니다.
- 컨트롤러는 서비스 경계에서 `block()`으로 결과를 받아 동기 반환합니다. 리액티브 경계는 서비스 내부로 한정합니다.
- 타임아웃은 개별 호출과 전체 요청 예산 두 층위로 둡니다.
- 부분 실패는 HTTP 200 + 공급사별 상태로 응답하고, 쓸 수 있는 결과가 하나도 없으면 5xx(전부 실패 502 / 전부 `SKIPPED` 503)로 응답합니다.
- 매핑이 없어 제외된 공급사는 응답에 `SKIPPED`로 표기하고, 청크 부분 실패 시 성공 청크만 병합해 `PARTIAL_SUCCESS`로 표기합니다.
- 정규화할 수 없는 항목은 스킵하고 경고 로그를 남깁니다.
- 필수 견고성 기능을 먼저 구현하고, 재시도와 서킷 브레이커는 후순위로 둡니다.

## 13. AI 활용 기록 (설계 단계)

### 13.1 활용 방식

Day 1과 같은 방식으로, AI를 선택지 생성기이자 검토자로 사용했습니다. Day 2에서는 두 가지에 집중했습니다. 첫째, 완성한 설계 초안을 요구사항 원문과 대조해 빠졌거나 서로 모순되는 지점을 찾는 검토입니다. 둘째, 경계 상황마다 선택지와 트레이드오프를 정리받아 비교 대상으로 삼는 것입니다. 최종 판단은 직접 내렸습니다.

### 13.2 어떻게 물었는가

| 유형 | 실제 프롬프트 | 의도 |
| --- | --- | --- |
| 원문 대조 검토 | "설계 초안을 요구사항 원문과 대조해서 의사결정이 필요하거나 더 고민할 지점을 찾아줘" | 초안의 빈 곳을 스스로 떠올리기보다 원문을 기준으로 교차 점검하기 위함입니다. |
| 정합성 점검 | "이 정책(동기화 실패 시 기존 매핑으로 서비스)이 데이터 정합성에 문제가 없는지 점검해줘" | 이미 내린 결정의 약한 고리를 되짚기 위함입니다. 이 질문에서 표시 메타데이터 출처 문제가 드러났습니다. |
| 선택지 요청 | "이 경계 상황의 대안과 트레이드오프를 정리해줘" | 답 하나가 아니라 후보군을 받아 비교하기 위함입니다. |

### 13.3 수용·수정·거부 기록

| 대상 | AI가 제시한 것 | 판단 | 이유 |
| --- | --- | --- | --- |
| 표시 메타데이터 출처 | 매핑을 단일 출처로 둔 초안에 정합성 우려를 제기하고, 재고·요금 실시간 응답을 출처로 쓰는 대안 제시 | 수용 | 재고·요금 응답에 이름·인원이 모두 있어, 동기화가 밀려도 표시 값이 stale되지 않습니다. |
| 전체 실패 응답 코드 | 200 + 빈 결과 / 5xx 두 선택지 제시 | 수용 (5xx 선택) | 쓸 수 있는 결과가 하나도 없으면 상류 오류로 알리는 것이 맞다고 판단했습니다. 본문에는 상태를 담아 원인을 전달합니다. |
| 제외 공급사 표기 | 상태값 `SKIPPED` 추가 제안 | 수용 | 매핑이 없어 빠진 공급사가 왜 결과에 없는지 응답만으로 설명됩니다. |
| 타임아웃 범위 | 청크 분할 시 개별 타임아웃만으로는 지연이 누적됨을 지적하고 전체 요청 예산 제안 | 수용 | 개별 호출과 전체 예산 두 층위로 나눴습니다. |
| 카탈로그 동기화 검증 | Mock 숙소 목록 API 장애 모드와 검증 테스트 추가 제안 | 수정 (후순위) | 필수 구현이 아니므로 핵심 검색 흐름을 먼저 완성하고 뒤로 미뤘습니다. |
| 상태값 판정 규칙 | 실패 유형별 상태값 매핑 표 제안 | 수용 | 실패 표현이 달라도 동일한 규칙으로 통일하기 위함입니다. |

## 14. 구현 — #1 표준 도메인 모델과 검색 조건

하이브리드 방식에 따라 도메인 계산 로직을 TDD로 진행했습니다. 실패하는 테스트를 먼저 작성해 컴파일 실패(red)를 확인한 뒤, 도메인 모델을 구현해 통과(green)시켰습니다.

### 만든 것

- `domain` 패키지: `SupplierType`, `SearchCriteria`, `Price`, `DailyAvailability`, `StayOffer`
- 테스트: `SearchCriteriaTest`(숙박일수·숙박일 목록·검증), `PriceTest`(평균가 내림·샘플 수치), `DailyAvailabilityTest`(최솟값·0·누락일)

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 도메인 타입 | Java 21 `record` | 불변이고 간결합니다. 값 객체 성격에 맞습니다. |
| 금액 표현 | `long` (통화 최소 단위 정수) | 부록 규약이 금액을 정수로 정의하며, KRW는 소수점이 없습니다. |
| 1박 평균가 | 총액 ÷ 숙박일수, 정수 나눗셈으로 내림 | 금액이 음수가 아니므로 정수 나눗셈이 곧 내림입니다. 올림은 실제보다 비싸 보여 택하지 않습니다. |
| 누락 날짜 재고 | `getOrDefault(date, 0)`으로 0 취급 | 요청 숙박일 중 응답에 빠진 날짜는 재고를 알 수 없으므로 방어적으로 예약 불가로 봅니다. |
| 검증 위치 | `record` compact 생성자 | `SearchCriteria` 생성 시점에 `checkIn < checkOut`, `adults > 0`, `children >= 0`을 강제해 잘못된 상태의 객체가 만들어지지 않게 합니다. |

### 검증

- 숙박일수: `2026-09-01 ~ 2026-09-04` → 3박 (체크아웃일 제외)
- 재고: `min(날짜별 remainingRooms)`, 하루라도 0이면 0, 누락일 0
- 요금: 429,000 ÷ 3 = 143,000 / 452,000 ÷ 3 = 150,666(내림) — 부록 샘플과 일치

전체 테스트 통과를 `./gradlew test`로 확인했습니다.

## 15. 구현 — #2 매핑 엔티티와 Repository

영속 계층이라 엄격한 TDD 대신 엔티티·Repository 구현과 `@DataJpaTest`(내장 H2)를 병행했습니다. `build.gradle`에 `spring-boot-starter-data-jpa`와 `com.h2database:h2`를 추가했습니다.

### 만든 것

- `infrastructure.persistence` 패키지: `StayMapping`, `RoomTypeMapping`(엔티티), `StayMappingRepository`, `RoomTypeMappingRepository`
- 테스트: `StayMappingRepositoryTest`, `RoomTypeMappingRepositoryTest`

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 엔티티는 `record` 아님 | 일반 클래스 + 보호 no-arg 생성자 | JPA는 no-arg 생성자·가변 필드·프록시를 위한 비-final 클래스를 요구합니다. 무엇보다 DB가 부여한 식별자로 정체성이 정해지는 엔티티이지 값 객체가 아닙니다. |
| 매핑 엔티티 위치 | `domain`이 아닌 `infrastructure`에 배치 | `@Entity` 등 JPA(`jakarta.persistence`) 의존을 가지므로, "도메인은 프레임워크-프리"라는 규칙상 도메인에 둘 수 없습니다. 판단 기준은 애노테이션 자체가 아니라 그것이 나타내는 의존입니다. 또한 매핑은 코드↔식별자 부기 테이블이지 핵심 도메인 개념이 아닙니다(핵심 모델 `StayOffer`·`Price`는 저장하지 않음). |
| 내부 식별자를 PK로 | 별도 대리 키 없이 `internal_stay_id` / `internal_room_type_id`를 PK로 | 병합(확장 범위)이 없어 매핑 행과 내부 식별자가 1:1입니다. 병합을 구현하면 대리 PK를 분리하고 내부 식별자를 일반 컬럼으로 내립니다. (설계 문서 §4.3·4.4 동기화) |
| 식별자 발급 | `IDENTITY`(DB 자동 증가) | 예측 가능한 순차 id와 DB 이식성(H2·MySQL 공통)을 우선했습니다. `SEQUENCE`는 배치 삽입의 문을 여는 이점이 있으나, 지금 규모(매핑 수십 건)에선 이득이 측정되지 않고 id 구멍·정합성·MySQL 미지원 리스크만 늘어 도입하지 않았습니다. 대량 쓰기 배치가 필요할 규모가 되면 전환하는 경로를 설계 문서(§4.3)에 남겼습니다. |
| 엔티티 생성 패턴 | Lombok `@Getter` + `private` 생성자 + 정적 팩토리(`of`), 인자 많으면 빌더 | 게터 보일러플레이트를 줄이고 생성 경로를 팩토리로 통제합니다. `RoomTypeMapping`은 문자열 인자가 연달아 있어(코드 3개) 위치 실수를 막으려 빌더를 씁니다. |
| `supplier` 저장 | `@Enumerated(STRING)` | 순서 변경에 취약한 ORDINAL 대신 이름으로 저장합니다. |
| 유니크 제약 | 숙소 `(supplier, supplier_stay_code)`, 객실 `(supplier, supplier_stay_code, supplier_room_type_code)` | 같은 공급사 코드 재조회 시 기존 식별자 재사용의 토대이며, 다른 숙소의 같은 객실 코드 충돌을 막습니다. |
| 타임스탬프 | `@CreationTimestamp` / `@UpdateTimestamp` | 생성·수정 시각을 Hibernate가 채우게 해 보일러플레이트를 줄입니다. |

### 검증

- 저장 시 내부 식별자 발급, 활성 플래그 true
- `(supplier, code)` / 3-튜플로 조회
- 같은 키 중복 저장 시 `DataIntegrityViolationException`
- 공급사가 다르면 같은 코드라도 별도 내부 식별자 (병합 안 함)
- 다른 숙소의 같은 객실 코드는 충돌하지 않음
- `findByActiveTrue()`로 활성 매핑만 조회

"같은 코드 재조회 시 기존 식별자 재사용"의 upsert 로직 자체는 숙소 목록 동기화 서비스(#7)에서 다룹니다. 이번에는 그 토대인 조회 메서드와 유니크 제약까지 확정했습니다.

## 16. 구현 — #3 H2 File 모드와 JPA 설정

지금까지 테스트는 내장 H2를 썼는데, 실제 앱을 설계대로 H2 **file 모드**로 띄우고 clone 후 추가 설치 없이 동작하게 맞췄습니다.

### 만든 것

- `src/main/resources/application.yml`: H2 file 모드 datasource + JPA 설정
- `src/test/resources/application.yml`: 테스트용 in-memory H2 격리
- `.gitignore`: 실행 시 생성되는 `/data/` · `*.mv.db` 제외

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| DB 모드 | H2 file 모드, 상대경로 `./data/stay` | clone 후 추가 설치 없이 첫 실행 시 파일이 생성되게 합니다(스택 요건). MySQL 전환은 datasource 블록을 프로파일로 분리해 열어둡니다. |
| `ddl-auto` | `update` | 매핑 테이블을 자동 생성하고 재시작 간 데이터를 유지합니다. 테이블 2개라 마이그레이션 도구는 과설계로 보고 두지 않습니다. |
| `open-in-view` | `false` | 뷰 렌더링 구간까지 커넥션을 물고 지연 로딩이 새는 것을 막습니다. |
| `AUTO_SERVER=TRUE` | 사용 | 앱 실행 중 H2 콘솔 등 병행 접속을 허용합니다(개발 편의). |
| 테스트 격리 | 테스트는 in-memory로 분리 | 테스트가 파일 DB를 오염시키거나 테스트 간 상태가 누수되지 않게 합니다. `@DataJpaTest`는 기본 내장 대체, `@SpringBootTest`도 test 설정으로 덮습니다. |

### 검증

`./gradlew bootRun`으로 file 모드 기동을 확인했습니다. `create table` 2개 생성 로그, `Started StayApplication`(에러 없음), `data/stay.mv.db` 생성까지 확인했습니다. 현재 web 스타터가 없어 컨텍스트 기동 후 자동 종료되며, 상주 실행 검증은 검색 Controller가 붙는 #10에서 함께 합니다.

## 17. 구현 — #4 Mock Supplier 모듈

부록 A.3 스펙대로 Supplier A·B를 흉내 내는 Mock을 세웠습니다.

### 만든 것

- 멀티모듈 구성: 루트=앱 유지 + `:mock-supplier` 서브프로젝트. 플러그인 버전은 `settings.gradle`의 `pluginManagement`로 올려 모듈 간 충돌을 피합니다.
- `mock-supplier` 모듈: Spring Boot(web) 앱, 포트 `9090`
- `MockSupplierController`: 공급사별 엔드포인트 + 상태 제어 엔드포인트

### 엔드포인트·모드

| 대상 | 엔드포인트 |
| --- | --- |
| A 숙소 목록(①) | `GET /a/v1/hotels` |
| A 재고·요금(②) | `GET /a/v1/availability` |
| B 숙소 목록(①) | `GET /b/api/properties` |
| B 재고·요금(②) | `GET /b/api/search` |
| 상태 전환 | `POST /control/{a\|b}/mode?value=normal\|error\|no-response` |

- 정상: 부록 A.1/A.2 예시 고정 응답
- 장애: A는 HTTP 503, B는 HTTP 200 + `resultCode: E503` (두 공급사의 실패 표현 차이를 그대로 재현)
- 무응답: 응답 타임아웃을 넘기는 지연(30초)
- 숙소 목록(①)에는 장애 모드를 걸지 않습니다(②만 전환) — 카탈로그 동기화 실패 검증을 후순위로 둔 결정과 일치합니다.

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 별도 모듈·포트 | `:mock-supplier`, 포트 9090 | 같은 포트면 자기 자신을 호출해 스레드가 묶이고 실제 연동 문제와 구분이 어려워집니다. |
| 플러그인 버전 관리 | `settings.gradle` `pluginManagement` | 루트·서브프로젝트가 같은 Spring Boot 플러그인을 버전 충돌 없이 적용하기 위함입니다. |
| 응답 구현 | 고정 JSON 문자열 상수 | Mock은 연동 흐름 재현이 목적이라, 데이터 정교함보다 단순함을 우선합니다. |
| 상태 관리 | 공급사별 in-memory + 런타임 제어 엔드포인트 | 하나의 인스턴스로 정상·장애·무응답을 순차로 검증할 수 있습니다. |

### 검증

`java -jar`로 Mock을 띄우고 curl로 확인했습니다. A·B 정상 응답, `POST /control/a/mode?value=error` 후 A가 HTTP 503, `POST /control/b/mode?value=error` 후 B가 HTTP 200 + `resultCode: E503`을 반환하는 것까지 확인했습니다.

## 18. 구현 보강 — 모듈 레이아웃을 집계자 구조로 전환

#4에서 "루트=앱 + `:mock-supplier`" 구조로 갔는데, 두 모듈이 대등하지 않아(앱은 루트, Mock은 서브) 멀티모듈 의도가 덜 드러났습니다. 소스가 아직 적어 이동 비용이 낮은 시점에 집계자 구조로 재구성했습니다.

### 변경

- 루트를 코드 없는 **집계자(aggregator)** 로 두고, 본 앱을 `:stay-app` 서브프로젝트로 이동했습니다.

```
stay/ (루트 — 집계자)
├── stay-app/       본 애플리케이션
└── mock-supplier/  Mock (포트 9090)
```

### 결정 근거

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 레이아웃 | 집계자 + `stay-app` + `mock-supplier` | 두 모듈이 대등해 멀티모듈 구조가 분명히 읽히고, 모듈이 늘어도 일관됩니다. 루트의 빌드 조율과 앱 책임이 섞이지 않습니다. |
| 시점 | 지금 이동 | 소스가 적어 이동 비용이 낮습니다. 나중일수록 부담이 커집니다. |

### 영향

- 실행 경로: `./gradlew :stay-app:bootRun`, `./gradlew :mock-supplier:bootRun`
- H2 파일은 `stay-app` 작업 디렉터리 기준으로 생성되며, `.gitignore`는 `data/`로 위치 무관하게 제외합니다.
- `./gradlew build`로 두 모듈 빌드·테스트 green 확인.

## 19. 구현 — #5 Supplier A Adapter

`WebClient`로 Mock의 A 엔드포인트를 호출하고 응답을 표준 모델로 정규화하는 어댑터를 만들었습니다. 하이브리드에 따라 구현+테스트(MockWebServer)를 병행했습니다.

### 만든 것

- 포트 인터페이스 `SupplierAdapter` + 공통 타입(`SupplierCatalog`, `SupplierOffer`, `SupplierSearchResult`, `SupplierIntegrationException`) — `adapter` 패키지
- A 응답 DTO(`AHotelsResponse`·`AAvailabilityResponse`, record) — 어댑터 계층 내부 전용
- `SupplierAAdapter` — `/a/v1/hotels`·`/a/v1/availability` 호출 후 정규화

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| `SupplierOffer` vs `StayOffer` 분리 | 어댑터는 공급사 코드를 유지한 `SupplierOffer`까지 | 어댑터는 내부 식별자를 모릅니다. 공급사 코드→내부 식별자 치환은 매핑을 가진 오케스트레이터(#8) 책임이라, 정규화와 매핑을 계층으로 분리합니다. |
| 요금 정규화 | `gross = Σ(nightlyRate + taxAmount)`, `average = gross / nights` | A는 날짜별 세전 단가+세액을 주므로 어댑터가 세금 포함 총액으로 흡수합니다. |
| 실패 판정 | HTTP 4xx/5xx → `SupplierIntegrationException`(`onStatus`), 역직렬화 등 기타 오류도 같은 예외로 변환(`onErrorMap`) | 상위 계층이 실패 표현 방식이 아니라 "연동 실패" 사실만 다루게 합니다. |
| 어댑터 배선 | 생성자에 `WebClient` 주입, 아직 `@Component` 아님 | 테스트는 MockWebServer로 baseUrl을 지정합니다. WebClient 빈 배선은 오케스트레이터가 필요해지는 시점(#8/설정)에 합니다. |
| WebClient 의존성 | `spring-boot-starter-webflux` 추가 | WebClient만 필요하고 서버는 서블릿을 유지할 예정이라, `starter-web`은 Controller(#10)에서 추가합니다. |
| MockWebServer 버전 | 4.12.0 고정 | Spring Boot BOM이 관리하지 않아 명시했습니다. |

### 검증

MockWebServer로 확인했습니다. 정상 응답 정규화(A-10023 gross 429,000 / avg 143,000 / 재고 min=1 / 조식 false), Namsan(A-10044) 09-02 재고 0 → 예약 가능 0, HTTP 503 → `SupplierIntegrationException`, 숙소 목록 카탈로그 정규화, 잘못된 형식 → `SupplierIntegrationException`.

## 20. 구현 보강 — 패키지 구조 정리와 공통 예외 베이스

`adapter` 패키지에 포트·정규화 결과·예외·구현이 섞여 역할이 불분명했습니다. 역할별로 나누고 공통 예외 베이스를 도입했습니다.

### 정리

| 위치 | 담는 것 |
| --- | --- |
| `global.exception` | 공통 베이스 `StayException` + `SupplierIntegrationException`(상속) |
| `adapter` | 포트 인터페이스 `SupplierAdapter` |
| `adapter.result` | 정규화 결과 타입 `SupplierCatalog`·`SupplierOffer`·`SupplierSearchResult` |
| `adapter.a` | `SupplierAAdapter` |
| `adapter.a.dto` | A 원본 응답 DTO `AHotelsResponse`·`AAvailabilityResponse` (record) |

### 결정

- **역할별 분리** — DTO(원본 응답)·정규화 결과·포트·예외를 섞지 않습니다. 어디에 무엇이 있는지 이름으로 드러납니다.
- **공통 예외 베이스 `StayException`** — 모든 커스텀 예외가 이를 상속해, 예외 처리를 한 곳(향후 `@RestControllerAdvice`)에서 일괄로 다룰 밑그림을 만듭니다.
- **전역 관심사는 `global` 아래로** — 예외는 `global.exception`에 두고, 앞으로 WebClient 설정(#8)·전역 예외 핸들러(#10)도 `global`에 모아 루트 패키지를 기능 중심으로 유지합니다.
- **영속 계층도 역할별로** — `infrastructure.persistence`를 `entity`(JPA 엔티티)와 `repository`(Repository)로 나눕니다. 반면 `domain`은 값 객체 5개로 성격이 하나라 flat을 유지했습니다. 쪼갤 곳과 두는 곳을 성격 기준으로 구분합니다.
- **엔티티 배치는 레퍼런스로 검증** — JPA 엔티티를 도메인/인프라 중 어디 둘지 헥사고날·DDD 레퍼런스(분리파 vs 실용파)를 대조하고, "도메인은 프레임워크-프리, 매핑 엔티티는 infra" 결정과 근거를 [docs/architecture.md](docs/architecture.md)에 기록했습니다. 우리 경우 매핑이 부기 테이블이라 이중 모델 매핑 오버헤드 없이 도메인 순수성을 얻습니다.
- 원본 응답 DTO는 서브패키지 접근을 위해 `public`으로 두되, 참조는 어댑터 계층 안으로만 한정합니다.

테스트 green으로 검증했습니다.

## 21. 구현 — #6 Supplier B Adapter

A와 같은 `SupplierAdapter`를 구현하고 결과 타입(`SupplierCatalog`·`SupplierOffer`)은 공유합니다. B의 특징을 어댑터에서 흡수했습니다.

### 만든 것

- `adapter.b.dto`: `BPropertiesResponse`, `BSearchResponse` (record)
- `adapter.b.SupplierBAdapter`
- `SupplierBAdapterTest`

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 실패 판정 | HTTP 200이어도 본문 `resultCode != "0000"`이면 실패로 변환 | B는 장애 시에도 HTTP 200을 줍니다. `flatMap`으로 본문을 확인해 실패를 주입하고, A의 4xx/5xx와 동일하게 `SupplierIntegrationException`으로 통일합니다. (방어적으로 `onStatus`도 유지) |
| 요금 정규화 | `totalPrice`를 그대로 세금 포함 총액으로 사용 | B는 이미 세금 포함 총액을 주므로 변환이 필요 없습니다. 평균가는 A와 동일하게 `총액 / nights` 내림. |
| 응답 구조 | `data.items` 한 겹을 벗겨 정규화 | A와 감싸는 구조가 다르지만, 벗겨낸 뒤 결과 타입은 A와 동일합니다. |
| 생성자 | `@RequiredArgsConstructor` | 컨벤션에 따라 WebClient 주입을 어노테이션으로. |

두 어댑터가 같은 결과 타입으로 정규화하므로, 상위(오케스트레이터 #8)는 A·B를 구분 없이 동일하게 병합할 수 있습니다.

### 검증

MockWebServer로 확인했습니다. 정상 정규화(B77120 `totalPrice` 452,000 → gross 452,000 / avg 150,666 / 재고 min=1 / 조식 true), **HTTP 200 + `resultCode: E503` → `SupplierIntegrationException`(B 핵심)**, 숙소 목록 카탈로그 정규화, 잘못된 형식 → `SupplierIntegrationException`.

## 22. 구현 — #7 숙소 목록 동기화 서비스

두 어댑터의 카탈로그로 매핑을 채우는 동기화를 구현했습니다. 여기서 처음으로 어댑터·Repository를 Spring 빈으로 배선하고 WebClient 설정이 들어왔습니다.

### 만든 것

- `global.config`: `SupplierProperties`(`@ConfigurationProperties`), `SupplierAdapterConfig`(공급사별 WebClient + `X-Api-Key`, 어댑터 A/B 빈 등록)
- `application`: `MappingUpserter`(`@Transactional` upsert), `CatalogSyncService`(기동·주기·수동)
- `@EnableScheduling`, `application.yml`의 `supplier` 설정

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| WebClient·설정 분리 | base-url·api-key를 `SupplierProperties`로, WebClient 구성을 `config`로 | 코드에서 엔드포인트·키를 분리하고, 어댑터는 호출 로직에만 집중합니다. |
| upsert 배선 | `MappingUpserter`를 별도 빈으로 두고 `@Transactional` | 네트워크 호출(fetch)은 트랜잭션 밖에서, upsert만 트랜잭션 안에서. 동기화 서비스 내 self-invocation으로 트랜잭션이 안 걸리는 문제를 피합니다. |
| 재사용 보장 | `findBy` → 있으면 `refreshFrom`(기존 내부 식별자 유지), 없으면 `save` | "같은 공급사 코드는 항상 같은 내부 식별자"를 upsert로 보장합니다. |
| 실패 격리 | 한 공급사 실패 시 경고 로그 + 나머지 계속, 기존 매핑 유지 | 한 공급사 목록 조회 실패가 전체 동기화를 막지 않게 합니다(P9). |
| 동기화 시점 | 주기 스케줄러 하나(첫 실행이 기동 적재 겸함) + 수동(`syncAll()`) | `@Scheduled`가 initialDelay 없이 기동 직후 첫 실행되므로, 별도 기동 트리거는 중복이라 두지 않습니다. 수동 트리거의 HTTP 엔드포인트는 web이 붙는 #10에서 노출합니다. |

### 발견·수정

처음엔 기동 동기화(`@EventListener(ApplicationReadyEvent)`)와 주기 동기화(`@Scheduled`)를 따로 뒀습니다. e2e에서 둘이 기동 직후 동시에 같은 매핑을 insert해 유니크 제약 충돌(잡혀서 무해하나 불필요)이 났습니다. `@Scheduled`는 `initialDelay`가 없으면 **첫 실행이 기동 직후**이므로, 별도 기동 동기화가 사실 중복이었습니다. 그래서 **주기 스케줄러 하나로 통합**(첫 실행이 초기 적재를 겸함)하고, 테스트에서 그 첫 실행이 외부를 호출하지 않도록 `supplier.sync.enabled=false` 토글로 껐습니다. e2e 재확인 결과 완료 2건·실패 0건으로 레이스가 사라졌습니다.

또한 테스트 `application.yml`이 메인 것을 **대체**하므로(클래스패스에서 test 리소스 우선) `supplier` 설정을 테스트 쪽에도 자립적으로 둡니다.

### 검증

- 단위: upsert 재사용(같은 코드 재동기화 시 내부 식별자 동일·중복 생성 없음), 실패 격리(A 실패해도 B는 upsert)
- e2e: Mock(9090) + 앱 기동 → `동기화 완료: SUPPLIER_A stays=2`, `SUPPLIER_B stays=1` 로그로 매핑 적재 확인

## 23. AI 활용 기록 (구현 단계)

### 23.1 활용 방식

확정된 설계를 코드로 옮기는 초안 생성과 테스트 작성에 AI를 사용했습니다. 하이브리드로 도메인 계산은 TDD, 인프라·어댑터는 구현+테스트 병행으로 진행했습니다. 구조 선택지(모듈 레이아웃·패키지 분리)는 AI가 대안을 펼치고 판단은 직접 했으며, 이 단계의 특징은 **AI가 생성한 코드를 반복적으로 검토해 개선안을 되먹인 것**입니다.

### 23.2 어떻게 물었는가

| 유형 | 실제 프롬프트 | 의도                                                                                                                           |
| --- | --- |------------------------------------------------------------------------------------------------------------------------------|
| 개선 제안 검증 | "이 응답 DTO는 값 성격인데 class보다 record가 더 맞지 않아?" · "final 필드만 주입하는 생성자면 @RequiredArgsConstructor로 대체할 수 있지 않아?" · "예외는 전역 관심사이니 global 패키지로 모으는 게 낫지 않을까?" · "@Scheduled 첫 실행이 기동 직후이니, 기동 동기화를 따로 두지 말고 스케줄러 하나로 합쳐도 되지 않아?" | AI 초안을 그대로 두지 않고, 더 나은 구조·관용구를 직접 제안해 검증·반영하기 위함입니다.                                                                         |
| 근거 요구 | "이 결정이 헥사고날·DDD에서 실제로 어떻게 다뤄지는지 레퍼런스로 교차 검증해줘. 반대 견해가 있으면 같이 알려줘." · "커뮤니티 글 말고 책·원칙 같은 정본에서는 뭐라고 하는지 확인해서 보강하고, 확인 안 된 건 표시해줘." | 결정에 대한 정본·레퍼런스를 빠르게 찾아 교차 검증하기 위함입니다. AI에게 근거의 소재를 찾아오게 하고, 소스 강도(커뮤니티 글 vs 정본)와 미확인 사항을 구분해 최종 판단은 직접 했습니다. |

### 23.3 수용·수정·거부 기록

| 대상 | AI가 제시·생성한 것 | 판단 | 이유 |
| --- | --- | --- | --- |
| DTO 표현 | 원본 응답 DTO를 묶음 class(`AResponses`)로 생성 | 수정 | record 분리를 제안해 top-level record로 나누고 상자 class를 제거했습니다. |
| 게터·생성자 | 손으로 쓴 게터·생성자 | 수정 | Lombok 적용을 지적해 `@Getter`·`@RequiredArgsConstructor`·`@NoArgsConstructor`로 대체했습니다. |
| 예외 위치 | 예외를 `exception` 패키지에 배치 | 수정 | 전역 관심사 그룹핑을 제안해 `global.exception`으로 옮기고, 앞으로 config·error 핸들러도 `global`에 모으기로 했습니다. |
| 엔티티 배치 | 매핑 엔티티를 infra에 배치(초안) | 수용 + 근거 보강 | 레퍼런스 검증을 요구해 Persistence Ignorance·Hombergs로 근거를 확인하고 설계 문서에 남겼습니다. |
| 식별자 전략 | 시퀀스(`SEQUENCE`) | 수정 | 배치 이점 대비 규모·리스크를 검토해 `IDENTITY`로 바꾸고, 확장 경로는 설계 문서로 남겼습니다. |
| 동기화 스케줄 | 기동 동기화 + 주기 동기화(`initialDelay`) 분리 | 수정 | "스케줄러 첫 실행이 기동 적재를 겸한다"를 지적해 주기 스케줄러 하나로 통합했습니다. |

이 단계에서 나온 개선의 대다수가 AI 초안을 그대로 받지 않고 검토·수정한 결과이며, 각 결정의 근거는 코드 주석·설계 문서·이 과정 기록에 함께 남겼습니다.

## [Day 3]

### 수행 내용

1. 통합 검색 오케스트레이터 구현 (#8)
2. application 패키지 유스케이스별 정리
3. 타임아웃·부분 실패 보강 (#9)
4. 검색 API 컨트롤러·웹 노출 (#10)
5. 테스트 보강 (#11)

## 24. 구현 — #8 통합 검색 오케스트레이터

`search-flow.md`의 처리 순서를 코드로 옮겼습니다. 활성 매핑으로 호출 대상을 정하고, 공급사 어댑터를 병렬 호출해 응답을 표준 모델로 정규화하며, 공급사 코드를 내부 식별자로 치환해 결과와 공급사별 상태를 합칩니다.

### 만든 것

- `application`: `StaySearchService`(오케스트레이터), `SearchResult`(결과+공급사별 상태 집계), `SupplierResult`(공급사별 처리 결과), `SupplierSearchStatus`(상태 enum)
- `global.exception`: `SupplierFailureKind`(HTTP_ERROR·PROTOCOL_ERROR) 추가, `SupplierIntegrationException`에 실패 종류 부여
- `RoomTypeMappingRepository.findByActiveTrue()` 추가(코드→식별자 치환용 일괄 조회)

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 병렬 호출 | 공급사별 `Mono`를 `Flux.merge`로 병렬 실행 후 끝에서 `block()` | 리액티브 경계를 서비스 안으로 한정하고, 각 공급사 호출을 독립시켜 한쪽 실패가 다른 쪽을 취소하지 않게 합니다. |
| 부분 실패 | 공급사별로 `onErrorResume`해 예외를 상태로 흡수 | 한 공급사가 실패해도 나머지 결과로 응답합니다(P6). 예외를 상위로 전파하지 않고 공급사별 상태로 감쌉니다. |
| 실패 종류 판정 | 예외에 `SupplierFailureKind`를 붙여 어댑터에서 부여, 오케스트레이터가 `HTTP_ERROR`/`PROTOCOL_ERROR`로 통일 | A의 4xx/5xx와 B의 `resultCode`·역직렬화 오류를 `failure-handling.md`의 상태 규칙대로 구분합니다. 어댑터를 #9에서 다시 건드리지 않도록 지금 넣었습니다. |
| 식별자 치환 | 매핑을 일괄 로딩해 조회 맵을 만들고 offer마다 치환(N쿼리 회피) | 네트워크 호출 전에 필요한 값을 확보해 조회 트랜잭션을 짧게 유지합니다. 이름·수용 인원은 실시간 응답을 출처로 하고, 매핑에서는 내부 식별자만 취합니다. |
| SKIPPED | 매핑 없는 공급사는 호출하지 않고 `SKIPPED` | 어느 공급사가 왜 결과에 없는지 응답만으로 드러냅니다. |
| 결과 정렬 | 수집 후 공급사 순서로 정렬 | `merge`는 완료 순서로 섞이므로, 응답을 안정적으로 만듭니다. |

### 범위 경계 (#9로 미룸)

개별 호출 타임아웃·전체 요청 예산·`TIMEOUT`, 50개 초과 청크 분할·`PARTIAL_SUCCESS`는 다음 단계(#9)에서 더합니다. 상태 enum에는 미리 정의만 해두고 이 단계에서는 방출하지 않습니다.

### 검증

- 단위(`StaySearchServiceTest`): 두 공급사 결과 병합 + 코드→내부 식별자 치환, 매핑 없는 공급사 `SKIPPED`(호출 안 함), 한 공급사 실패 시 나머지로 응답 + 실패 공급사 `HTTP_ERROR` 상태
- 전체 스위트 36건 통과(기존 33 + 신규 3)

## 25. 구현 보강 — application 패키지 유스케이스별 정리

#8로 검색 관련 클래스가 늘면서 `application`에 성격이 다른 두 유스케이스(검색·동기화)가 평면으로 섞였습니다. 어댑터를 역할별로 나눈 것과 같은 결로 유스케이스별 서브패키지로 정리했습니다.

### 변경

- `application.search`: `StaySearchService`(오케스트레이터) + `SearchResult`·`SupplierResult`·`SupplierSearchStatus`(검색 결과 타입)
- `application.sync`: `CatalogSyncService` + `MappingUpserter`
- 테스트도 같은 구조로 이동, `CLAUDE.md` 패키지 규약에 애플리케이션 분리 기준 추가

### 결정 근거

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 분리 기준 | 유스케이스별(검색/동기화) | 두 흐름은 진입점·트랜잭션·의존이 다릅니다. 성격(서비스/결과)이 아니라 유스케이스로 나눠 응집을 높였습니다. |
| 결과 타입 위치 | 서비스와 같은 패키지(`application.search`) | 결과 `record`는 그 유스케이스에만 쓰이므로 함께 둡니다. 결과 타입이 늘면 그때 더 깊은 분리를 검토합니다(과설계 회피). |

### 검증

- 외부 참조가 없어 영향 범위가 작았고(패키지 선언만 변경), 전체 스위트 36건 그대로 통과

## 26. 구현 — #9 타임아웃 · 부분 실패

오케스트레이터에 견고성을 더했습니다. 청크 분할·동시성 상한, 청크별 응답 타임아웃, 공급사별 전체 예산, 부분 성공(`PARTIAL_SUCCESS`)·타임아웃(`TIMEOUT`) 상태를 구현했습니다.

### 만든 것

- `global.config`: `SupplierProperties.Search`(connect·response 타임아웃, 전체 예산, chunk-size, chunk-concurrency), `SupplierAdapterConfig`에 커넥터 연결 타임아웃
- `application.search`: `StaySearchService`에 청크 분할 + `flatMap` 동시성 상한 + 청크 응답 `.timeout()` + 공급사 예산 `.timeout()`, 청크 결과 집계(`SUCCESS`/`PARTIAL_SUCCESS`/전부 실패 대표 상태)
- `global.exception`: `SupplierFailureKind.TIMEOUT` 추가, `adapter.TimeoutClassifier`(연결·읽기 타임아웃 판정), 두 어댑터가 타임아웃을 `TIMEOUT` 종류로 분류
- `SupplierResult.partialSuccess` 팩토리, `application.yml`(main·test)에 `supplier.search` 설정

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 타임아웃 배치 | 연결=커넥터, 응답·예산=오케스트레이터 `.timeout()` | 응답 타임아웃을 오케스트레이터에서 잡으면 어댑터의 실패 변환과 분리돼, 모든 타임아웃을 일관되게 `TIMEOUT` 상태로 귀결시킬 수 있습니다. |
| 전체 예산 | 공급사별 파이프라인에 적용 | 공급사 호출이 병렬이라 벽시계 지연은 예산에 수렴합니다. 예산 초과 공급사만 `TIMEOUT`으로 마감하고 나머지는 그대로 응답합니다. |
| 청크 분할 | 오케스트레이터가 `chunk-size`로 나눠 `flatMap(concurrency)` 호출 | 어댑터는 "받은 코드로 1회 호출"만 유지하고, 분할·동시성은 오케스트레이터 책임으로 둡니다. 예시 데이터(50개 미만)는 단일 청크로 흐릅니다. |
| 부분 성공 | 성공 청크가 하나라도 있으면 `PARTIAL_SUCCESS`(결과 유지) | 얻은 결과를 버리지 않으면서 실패 사실을 응답에 드러냅니다(부분 실패 허용 원칙과 일관). |
| 전부 실패 대표 상태 | 우선순위 TIMEOUT > HTTP_ERROR > PROTOCOL_ERROR | 여러 청크가 서로 다른 이유로 실패해도 하나의 상태로 결정론적으로 표기합니다. 단일 청크(정상)에서는 그 청크의 상태가 그대로 대표가 됩니다. |
| 타임아웃 종류화 | `SupplierFailureKind.TIMEOUT` + `TimeoutClassifier` | 커넥터 연결 타임아웃처럼 어댑터를 통해 올라오는 타임아웃도 `TIMEOUT`으로 분류해, 오케스트레이터가 잡는 응답 타임아웃과 상태를 통일합니다. |

### 검증

- 단위(`StaySearchServiceTest` +2): 응답 지연 → `TIMEOUT`(다른 공급사는 그대로 성공), 청크 분할 시 일부 청크 실패 → 성공 청크 유지 + `PARTIAL_SUCCESS`
- 전체 스위트 38건 통과(기존 36 + 신규 2)
- 실제 WebClient·MockWebServer 기반 타임아웃 통합 검증과 무응답 시나리오 e2e는 컨트롤러가 붙는 #10 이후 #11에서 보강 예정

## 27. 구현 — #10 검색 API 컨트롤러 · 웹 노출

오케스트레이터를 HTTP로 노출했습니다. 실행 모델을 서블릿(MVC)으로 전환하고, 검색 컨트롤러·응답 DTO·전역 예외 처리·수동 동기화 트리거를 붙였습니다.

### 만든 것

- `build.gradle`: `spring-boot-starter-web` 추가(WebClient용 webflux는 유지), `application.yml`에 `spring.main.web-application-type=servlet` 고정
- `api`: `StaySearchController`(GET `/api/v1/stays/search`), `SyncController`(POST `/api/v1/admin/catalog-sync`), `api.dto`(SearchResponse·StayOfferResponse·SupplierStatusResponse·PriceResponse)
- `global.error`: `GlobalExceptionHandler`(`@RestControllerAdvice`) + `ErrorResponse`
- `global.exception`: `InvalidRequestException`(StayException 하위) 신설, `SearchCriteria`가 클라이언트 입력 검증에 사용
- `SearchResult`에 `anySucceeded()`·`allSkipped()`(전체 실패 판정용, 기존 `hasAnyResult` 대체)

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 웹 타입 | web+webflux 공존, `web-application-type=servlet`로 고정 | WebClient는 webflux에서 오지만 실행은 MVC로 합니다. 두 스타터가 함께 있어 실행 모델을 명시로 못 박아 모호함을 없앱니다. |
| 웹 패키지 | 인바운드 `api`(+`api.dto`)를 아웃바운드 `adapter`와 분리 | 방향이 반대인 경계를 섞지 않습니다. 컨트롤러는 유스케이스 호출 + 응답 DTO 변환만 담당합니다. |
| 응답 DTO 분리 | 내부 모델(StayOffer)을 그대로 노출하지 않고 DTO로 변환 | 내부 전용 `dailyAvailability`·공급사 코드가 응답에 새지 않게 합니다. `results[]`/`suppliers[]`로 분리하고, 상태별로 `resultCount`·`errorCode`를 선택 노출(null 생략). |
| 응답 코드 | 성공(부분 포함) 200, 전부 실패 502, 전부 SKIPPED 503 | 부분 실패는 오류가 아니므로 200. 쓸 수 있는 결과가 없을 때만 5xx이되 본문엔 `suppliers[]`를 담아 원인을 전달합니다. |
| 예외 처리 위치 | `global.error` `@RestControllerAdvice` 한 곳 | 잘못된 요청은 400, 예기치 못한 오류는 500으로 일괄 변환합니다. 5xx(전체 실패)는 오류가 아닌 정상 응답 경로라 컨트롤러에서 상태만 정합니다. |
| 입력 검증 예외 | `SearchCriteria`는 `InvalidRequestException`(StayException 하위)을 던지고, 핸들러는 이 타입만 400으로 매핑 | 광범위한 `IllegalArgumentException → 400` 매핑은 무관한 IAE(내부 버그·라이브러리)를 400으로 오분류할 수 있어 제거했습니다. 검증 방식은 "항상 유효한 값 객체"를 위해 생성자 검증을 유지하고(@Valid 미도입: 교차 필드 검증·도메인 프레임워크-프리 유지), `Price`·`DailyAvailability`의 내부 불변식은 클라이언트 오류가 아니므로 `IllegalArgumentException`으로 남겨 500 경로로 둡니다. |
| 타임아웃 메시지 | 응답 타임아웃 사유를 Reactor 내부 문구 대신 명시 문자열로 | e2e에서 `errorMessage`에 Reactor 내부 텍스트가 노출되어, 사유를 사람이 읽을 문구로 정리했습니다. |

### 검증

- 단위(`StaySearchControllerTest` 6, `SyncControllerTest` 1): 성공 200(내부 전용 필드 미노출 확인), 날짜 범위·누락·형식 400, 전부 실패 502·전부 SKIPPED 503(본문에 상태 유지), 수동 동기화 200
- e2e: Mock(9090)+앱(8080) 기동 → 서블릿(Tomcat) 확인, 수동 동기화 후 검색 200(응답이 api.md 예시와 일치), B 무응답 모드 → B `TIMEOUT`(약 3.0초)·A `SUCCESS` 결과 유지·HTTP 200으로 부분 실패 확인
- 전체 스위트 45건 통과(기존 38 + 신규 7)

## 28. 구현 — #11 테스트 보강

수동 curl로만 하던 e2e를 자동화하고, 목으로는 검증되지 않던 실제 동작을 통합 테스트로 고정했습니다.

### 만든 것

- `StaySearchIntegrationTest`(`@SpringBootTest` 랜덤 포트 + MockWebServer): 컨트롤러 → 오케스트레이터 → 실제 WebClient → 공급사 대역 전체 스택
- `TimeoutClassifierTest`: 원인 사슬 순회·감싸인 타임아웃·연결 타임아웃·비타임아웃·자기참조 방어

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 통합 테스트 범위 | 전체 스택(실제 WebClient)까지 태움 | 슬라이스·단위 테스트는 어댑터를 목 처리해 `.timeout()`·역직렬화·DTO 직렬화 등 실제 동작을 못 봅니다. 수동 curl로만 확인하던 경로를 자동화합니다. |
| 매핑 시딩 | DB에 직접 저장(동기화 경유 안 함) | 검색 경로만 격리해, 동기화 실패와 검색 실패를 섞지 않습니다. |
| 타임아웃 재현 | MockWebServer 본문 지연(1500ms) > 응답 타임아웃(800ms) | 실제 WebClient의 응답 타임아웃이 `TIMEOUT`으로 귀결되는지 검증합니다. `@DynamicPropertySource`로 base-url·타임아웃을 테스트값으로 주입합니다. |
| 커버 시나리오 | 정상 병합 200 / 한쪽 응답 타임아웃 → 부분 실패 200 / 매핑 없음 503 | 부분 실패 허용과 전체 실패 판정을 실제 스택에서 확인합니다. |
| 테스트 층위 | 단위·컴포넌트(실제 WebClient)·슬라이스·전체 통합으로 분리 | "무엇을 검증하는가"에 맞는 가장 가벼운 도구를 골라 피라미드로 둡니다. 목으로 안 드러나는 실제 동작만 실물로 확인합니다(근거는 [docs/testing.md](docs/testing.md) §0). |

### 검증

- 단위(`TimeoutClassifierTest` 5) + 통합(`StaySearchIntegrationTest` 3): 실제 WebClient 응답 타임아웃 → `TIMEOUT`, 정상 병합, 503 자동 검증
- 전체 스위트 53건 통과(기존 45 + 신규 8)

## 29. 구현 — API 문서(SpringDoc) 및 취약점 대응

스택 표(README)에 SpringDoc + Swagger UI를 채택해 두고 구현이 빠져 있어, 문서와 코드를 일치시켰습니다.

### 만든 것

- `springdoc-openapi-starter-webmvc-ui` 추가 → `/v3/api-docs`(스펙), `/swagger-ui.html`(UI) 자동 생성
- `global.config.OpenApiConfig`(제목·설명·버전), 컨트롤러에 `@Tag`·`@Operation` 부여
- 통합 테스트에 OpenAPI 노출 검증 1건 추가

### 결정·대응

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 스키마 생성 | 컨트롤러·DTO에서 자동 생성 | 응답 스키마 자체가 설계 결정이라 문서로 노출해 호출로 검증합니다. 수기 `api.md`는 설계 근거, Swagger는 실행 가능한 계약으로 역할을 나눕니다. |
| 취약점 대응 | `commons-lang3`를 3.18.0으로 강제 | springdoc(swagger-core)가 끌어오는 `commons-lang3 3.17.0`에 CVE-2025-48924(제어되지 않은 재귀 → StackOverflow DoS, 5.3)가 있어, Spring Boot 관리 버전 속성(`ext['commons-lang3.version']`)으로 수정본으로 올렸습니다. |

### 검증

- `/v3/api-docs`·`/swagger-ui/index.html` 200 확인, 검색 경로가 스펙에 포함
- `commons-lang3` 3.18.0 해석 확인(취약점 해소), 전체 스위트 54건 통과(기존 53 + 신규 1)

## 30. 구현 — #12 마무리 문서

README를 실제 구현 상태에 맞춰 확정하고, Swagger 기반 동작 테스트 가이드를 추가했습니다.

### 한 것

- **README 정정(문서↔코드 정합)**: 식별자 전략을 "시퀀스"에서 실제값 `IDENTITY`로 수정, 존재하지 않는 MySQL 프로파일 문구 제거, "설계 확정 단계" 등 미구현 뉘앙스 문장 삭제, 동기화 표현을 통합 스케줄러(첫 실행이 기동 적재 겸함)로 정정
- **실행·문서 링크 추가**: Swagger UI(`/swagger-ui.html`)·OpenAPI(`/v3/api-docs`)·H2 콘솔 URL
- **동작 테스트 가이드**: 매핑 적재 → 정상 검색 → Mock 모드 전환(장애/무응답)으로 부분 실패·`TIMEOUT` 재현 → 전체 실패(502)·매핑 없음(503) 확인 순서
- 잔여 문서 정합성 점검(시퀀스 잔재·미구현 뉘앙스 없음 확인)

### 검증

- 문서만 변경(코드 무변), 스위트 54건 유지
- README 가이드는 앞선 e2e(수동 curl) 결과와 일치

## [Day 4]

### 수행 내용

1. 코드 리뷰 반영 — 견고성 개선(응답 버퍼)과 청크 상한 정책 정리
2. 매핑 비활성화(`active`/`last_seen_at`) 제거
3. 재시도·서킷 브레이커 (확장)
4. 연동 지표·모니터링 (확장)
5. 확장 5종 설계 초안
6. 요금/재고 캐시 (확장)
7. 정규화 실패 격리 (확장)
8. 중복 상품 병합 — 자동 병합 미도입 결정
9. AI 활용 기록 (확장 단계)

## 31. 개선 — 응답 버퍼 상한과 청크 상한 정책 (리뷰 반영)

전체 리뷰에서 나온 견고성 항목을 반영했습니다.

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 응답 버퍼 | WebClient `maxInMemorySize`를 기본 256KB → 4MB로 상향 | 청크(최대 50개 숙소) 재고·요금 응답이 기본값을 넘으면 디코딩이 실패(`PROTOCOL_ERROR`)합니다. 여유를 둬 큰 응답에서도 끊기지 않게 합니다. |
| 청크 상한(50) 표현 | 코드 상수로 박지 않고 설정값(`supplier.search.chunk-size`)과 그 주석으로만 명시 | 규약 상한을 코드·설정 두 곳에 두면 변경 시 중복 수정이 필요합니다. 잘못 51로 둬도 해당 공급사만 우아하게 실패(다른 공급사는 정상)하므로 치명적이지 않습니다. 다만 0·음수는 분할 알고리즘을 깨뜨리므로 `Math.max(1, …)`만 코드로 방어합니다. |

## 32. 결정 — 매핑 비활성화(`active`/`last_seen_at`) 제거

설계 단계에서 "카탈로그에서 사라진 숙소" 처리를 위한 훅으로 `active` 플래그와 `last_seen_at`을 심어 두고, 비활성화 로직 자체는 확장 범위로 미뤄 뒀습니다. 리뷰에서 이 필드들이 실제로는 쓰이지 않는다는 점(항상 `active=true` → `findByActiveTrue`가 사실상 `findAll`, `last_seen_at`은 미사용)이 드러나, 목적을 재검토한 뒤 **제거**하기로 했습니다.

### 목적 재검토

- 두 필드의 목적은 "공급사 카탈로그에서 사라진(delisting) 숙소를 검색에서 빼기"입니다.
- 그런데 검색은 **실시간 재고·요금**을 출처로 하므로, 사라진 숙소 코드로 조회하면 응답에 포함되지 않아 **결과에서 이미 자연히 제외**됩니다. 즉 비활성화는 고객이 보는 결과의 정합성 요건이 아니라, "죽은 코드를 조회 요청에서 빼는" **효율·청소** 성격입니다.

### 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 비활성화 훅 | `active`·`last_seen_at` 필드, `findByActiveTrue` 제거(검색은 `findAll`) | 요구사항에 없는 항목이고, 정합성은 실시간 재고가 이미 보장합니다. 쓰지 않는 코드를 남기지 않는다는 원칙에 따라 제거했습니다. |
| 대안(구현) | 미채택 | "성공한 동기화 기준 미조회 코드만 비활성화(부분 실패 시 금지)" 로직은 복잡도가 있는데, 지금 규모에선 얻는 이득(효율·청소)이 작아 미룹니다. 필요해지면 확장으로 제대로 도입합니다. |

behavior는 보존됩니다(오늘 기준 `active`는 항상 true였으므로 `findAll`과 동일). 관련 서술은 [mapping.md](docs/mapping.md)·[CLAUDE.md](CLAUDE.md)·[search-flow.md](docs/search-flow.md)에서 함께 갱신했습니다.

### 검증

- 엔티티·리포지토리·서비스·테스트에서 필드/메서드 제거, `StaySearchService`는 `findAll`로 전환
- 전체 스위트 53건 통과(활성 조회 단위 테스트 1건 제거, 나머지 그대로)

## 33. 구현 — 재시도 · 서킷 브레이커 (확장)

필수 기능을 안정화한 뒤, 공급사 호출의 일시적 장애 흡수(재시도)와 지속 장애 공급사 차단(서킷)을 확장으로 추가했습니다. 확정 설계는 [docs/resilience.md](docs/resilience.md)에 있습니다.

### 만든 것

- Resilience4j(`reactor`·`retry`·`circuitbreaker`) 의존성, `global.config.ResilienceConfig`(설정값 → `Retry` 1개 공유 + 공급사별 `CircuitBreakerRegistry`)
- `StaySearchService.callChunk`에 연산자 삽입: `(call+응답 타임아웃) → RetryOperator → CircuitBreakerOperator`, 에러 흡수는 그 뒤
- `SupplierIntegrationException`에 `retryable` 플래그, 어댑터가 4xx/5xx·연결 실패를 구분해 설정
- `SupplierSearchStatus.CIRCUIT_OPEN` 추가, `TransportErrorClassifier`(기존 TimeoutClassifier 개명·확장: 타임아웃 + 연결 실패 판정)
- `supplier.resilience.*` 설정(main·test)

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 적용 방식 | 애노테이션 대신 Reactor 연산자(`transformDeferred`) | 청크·공급사 단위 정밀 제어가 필요해 리액티브 체인에 스테이지로 삽입합니다(AOP 프록시는 경계·인스턴스 선택과 안 맞음). |
| 연산자 순서 | CB(가장 바깥) → Retry → (call+타임아웃) | CB가 open이면 호출·재시도를 건너뛰고, 재시도는 "call+재시도"의 최종 결과 하나를 CB가 기록하게 합니다. 에러→결과 변환(`onErrorResume`)은 CB·retry가 실제 예외를 보도록 맨 끝에 둡니다. |
| 재시도 대상 | 타임아웃·연결 실패·5xx만 | 일시적 실패만 재시도합니다. 4xx·`resultCode`·역직렬화는 결정적이라 제외. 상태로는 전송/HTTP 실패를 `HTTP_ERROR`로 묶되 `retryable` 플래그로 구분합니다. |
| 서킷 단위·표기 | 공급사별 독립 CB, open은 `CIRCUIT_OPEN` | 한 공급사 장애가 다른 공급사에 영향을 주지 않게 하고, "차단되어 호출 안 함"을 응답만으로 드러냅니다. 전부 실패 대표 상태 우선순위: CIRCUIT_OPEN > TIMEOUT > HTTP_ERROR > PROTOCOL_ERROR. |
| 예산과의 관계 | 재시도는 전체 예산(`.timeout(budget)`) 안에서 수행 | 재시도가 고객 대기를 무한정 늘리지 못하게 예산이 상한 역할을 유지합니다. |

### 검증

- 단위(`StaySearchServiceTest` +2): 일시적 실패 재시도 후 성공(구독 2회로 확인), 비재시도 실패는 재시도 안 함(구독 1회). `TransportErrorClassifierTest`에 연결 실패 판정 추가.
- 통합 테스트(`@SpringBootTest`)가 `ResilienceConfig` 빈 배선을 포함해 로딩·정상 검색·타임아웃 부분 실패를 그대로 통과.
- 전체 스위트 56건 통과(기존 53 + 신규 3).

## 34. 구현 — 연동 지표·모니터링 (확장)

공급사 연동의 건강도(성공률·응답 지연·타임아웃 비율)를 관측할 수 있게 계측했습니다. 확정 설계는 [docs/observability.md](docs/observability.md)에 있습니다.

### 만든 것

- 의존성: `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `resilience4j-micrometer`
- `StaySearchService`가 검색 종료 시 각 `SupplierResult`를 지표로 기록: `supplier.search.calls{supplier,status}`(Counter), `supplier.search.latency{supplier}`(Timer, SKIPPED 제외)
- `ResilienceConfig`를 레지스트리 기반으로 바꾸고, `TaggedRetryMetrics`·`TaggedCircuitBreakerMetrics`를 MeterBinder 빈으로 등록(서킷·재시도 지표 자동)
- Actuator 노출 최소화(`health,info,metrics,prometheus`), `/actuator/prometheus`로 스크레이프 포맷 제공

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 계측 방식 | Micrometer(벤더 중립 파사드) + Actuator 노출 | 수집 백엔드를 바꿔도 계측 코드는 그대로. 이미 만들던 `SupplierResult`(supplier·status·latencyMs)를 계측 지점으로 재사용합니다. |
| 파생 지표 | 성공률·타임아웃 비율은 저장 안 하고 상태별 카운터에서 집계 시 유도 | 원자료(카운터)만 남기고 파생은 대시보드에서 계산해 유연성을 둡니다. |
| 범위 | 앱은 지표 측정·노출까지만, Prometheus 서버·Grafana는 미도입 | 수집·시각화는 운영 인프라 영역이라 저장소 밖으로 두고 방향만 남깁니다. |
| 부수 수정 | 매핑되지 않은 경로의 `NoResourceFoundException`을 404로 처리 | 전역 핸들러가 프레임워크 404를 500으로 뭉개던 문제를 바로잡았습니다(지표 노출 검증 중 발견). |

### 검증

- 단위(`StaySearchServiceTest` +1): 검색 후 상태별 호출 카운터·지연 타이머 기록, SKIPPED는 타이머 제외
- 통합(`StaySearchIntegrationTest` +1): `@AutoConfigureObservability`로 메트릭 익스포트를 켜고 `/actuator/prometheus`에 `supplier_search_*` 노출 확인
- 실기동 확인: `supplier_search_calls_total`·`supplier_search_latency_seconds_*`, `resilience4j_retry_calls_total`·`resilience4j_circuitbreaker_state` 노출
- 전체 스위트 58건 통과(기존 56 + 신규 2)

## 35. 설계 — 확장 5종 초안

여력이 생기면 하나씩 구현하기로 하고, 먼저 5개 확장의 설계 초안을 [docs/extensions.md](docs/extensions.md)에 정리했습니다. 구현은 아직 없습니다.

### 대상·핵심 판단

| 확장 | 핵심 판단 |
| --- | --- |
| 요금/재고 캐시 | 짧은 TTL + single-flight + 지터로 스탬피드 방지, 표시 지연은 허용하되 예약 확정은 실시간 재검증 |
| 정규화 실패 격리 | 스킵 대신 dead-letter로 사유 코드와 함께 격리(best-effort), 검색은 그대로 부분 성공 |
| 중복 상품 병합 | 정본 그룹핑 모델(큐레이션 우선), 서빙 경로의 자동 퍼지 병합은 오병합 위험으로 지양 |
| 통화 처리 | 원본=권위·표시 환산=근사(환율 시각 표기), 비교·정렬은 표시 통화, 확정은 원본 통화 |
| 예약 대행 | saga + 멱등 키 + 예약 상태 기계, 실패 시 공급사 예약 취소로 보상, 모호한 타임아웃은 보정 잡 |

### 공통 원칙

- 원본 데이터는 잃지 않는다(캐시·환산·병합은 파생).
- 표시(근사·지연 허용)와 확정(실시간 원본 재검증)을 분리한다.

### 구현 우선순위(효과 대비 비용)

캐시·정규화 실패 격리(기존 검색 경로에 얹기 쉬움) → 통화(표시 계층) → 중복 병합·예약 대행(새 하위 시스템·오병합/정합 리스크 커 설계 심화 후).

## 36. 개선 — Supplier B의 일시적 resultCode 재시도

지표 확인 중, **B가 error(일시적 장애)를 내도 재시도하지 않는** 점이 드러났습니다. A는 HTTP 5xx/4xx로 일시적/결정적을 구분해 5xx만 재시도하는데, B는 `resultCode != "0000"`을 **구분 없이 전부 비재시도**로 처리하고 있었습니다. Mock의 B error가 `resultCode "E503"(TEMPORARILY_UNAVAILABLE)` 즉 A의 503과 같은 일시 장애라, 같은 상황이 A는 재시도되고 B는 안 되는 불일치였습니다.

### 결정

- B 어댑터에 **일시적 resultCode 집합**(`E503`·`E429` 등 서버측 일시 코드)을 두고, 그 코드는 `retryable=true`로. 나머지 resultCode(잘못된 요청·업무 실패)는 결정적이라 `false` 유지.
- 상태는 `PROTOCOL_ERROR`(본문으로 실패를 알린 것은 맞음) 그대로 두고, **retryable만 코드별로 판정**(상태·재시도 판정 독립 원칙). 어떤 코드가 일시적인지는 공급사 스펙을 따른다.

### 검증

- 단위(`SupplierBAdapterTest`): `E503` → retryable=true, `E400` → retryable=false
- e2e: 신규 인스턴스에서 B=E503로 검색 1회 → `resilience4j_retry_calls_total{kind="failed_with_retry"}` +1(B 재시도), A는 `successful_without_retry` +1. 개선 전이면 B는 `failed_without_retry`였음.
- 문서 정정: [resilience.md](docs/resilience.md)·[failure-handling.md](docs/failure-handling.md)의 "resultCode=비재시도" 서술을 일시적 코드 구분으로 갱신
- 전체 스위트 59건 통과(기존 58 + 신규 1)

## 37. 메모 — 재시도·전체 예산 상호작용과 지표

지표 확인 중, 무응답(no-response) 공급사는 `resilience4j_retry_calls_total`에 재시도가 **집계되지 않는** 점을 확인했습니다. 원인은 `responseTimeout×maxAttempts`(3s×3=9s)가 전체 예산(6s)보다 커서, **재시도가 소진되기 전에 예산이 호출을 취소**하고, Reactor의 취소는 실패가 아니라 재시도 카운터가 기록하지 않기 때문입니다.

이는 **"고객 대기 상한을 재시도 완주보다 우선"**한 의도된 선택입니다. 무응답을 9s까지 재시도하기보다 예산(6s)에서 끊고 나머지 공급사로 응답하는 편이 낫습니다. "타임아웃 발생"은 재시도 지표가 아니라 공급사별 상태 지표 `supplier.search.calls{status="TIMEOUT"}`에서 확인합니다. 근거를 [resilience.md](docs/resilience.md)·[observability.md](docs/observability.md)에 기재했습니다.

- e2e로 대비 확인: no-response 검색 1회 → 재시도 카운터 0(예산 취소), 상태 `TIMEOUT` / E503 검색 1회 → `failed_with_retry` +1(예산 안에 재시도 완주).

## 38. 구현 — 요금/재고 캐시 (확장)

반복되는 인기 질의의 공급사 부하·지연을 줄이기 위해 청크 호출 결과를 캐시했습니다. 확정 설계는 [docs/cache.md](docs/cache.md)에 있습니다.

### 만든 것

- Caffeine 의존성, `application.search.ChunkResultCache`(Caffeine `AsyncCache`, 키 `(supplier, 정렬 코드, 조건)`, TTL 지터, 성공만 캐시, Micrometer 통계)
- `StaySearchService.callChunk`에서 캐시를 가장 바깥에 배치(히트면 타임아웃·재시도·서킷·공급사 호출 생략)
- `supplier.cache.*` 설정(main), 테스트는 공유 컨텍스트 오염 방지를 위해 비활성

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 캐시 단위 | 청크 단위 `(supplier, 정렬 코드, 조건)` | 코드가 안정적(활성 매핑)이라 청크 단위로도 재사용이 큼. 숙소 단위(부분 변경에 견고)는 미스 배치 조회·병합 복잡도가 커 규모 확장 시로 미룸 |
| 위치 | 리질리언스보다 바깥 | 히트면 호출 자체가 없음. 성공만 캐시(실패·타임아웃·서킷은 다음에 재시도되게) |
| 스탬피드 방지 | single-flight(Caffeine `AsyncCache` 내장) + TTL 지터 | 인기 키 만료 시 몰림·동시 만료 분산 |
| TTL | 짧게(설정값, 잠정) + 예약 확정 시 재검증 전제 | 요금·재고 신선도. 값은 변동 속도·히트율로 튜닝. 확정 재검증이 stale 안전망 |
| 저장소 | 로컬 Caffeine, 다중 인스턴스면 Redis 승격 | 단일 인스턴스엔 인프로세스로 충분 |

### 검증

- 단위(`StaySearchServiceTest` +1): 같은 조건 재검색 시 공급사 `search`가 1회만 호출(캐시 히트)
- e2e: 같은 조건 2회 검색 → 2회차 응답 시간 대폭 단축(77ms→10ms), `cache_gets_total{result="hit"}=2`·`miss=2`(공급사 2개=엔트리 2개, 1회차 미스·2회차 히트)
- 전체 스위트 60건 통과(기존 59 + 신규 1)

## 39. 구현 — 정규화 실패 데이터 격리 (확장)

정규화(치환)할 수 없어 결과에서 제외되던 항목을 **버리지 않고 격리(dead-letter)**하도록 했습니다. 설계는 [supplier-adapter.md](docs/supplier-adapter.md) §4에 반영했습니다.

### 만든 것

- `infrastructure.persistence`: `NormalizationFailure` 엔티티(`normalization_failure` 테이블), `NormalizationFailureReason`(현재 `MAPPING_NOT_FOUND`), 리포지토리
- `application.search.NormalizationFailureRecorder`: best-effort 일괄 저장(+ 격리 건수 지표)
- `StaySearchService`: 치환 실패 항목을 리액티브 구간에서 수집 → 검색 종료 후 저장

### 구현 결정

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 발생 지점 | 코드→내부 식별자 치환 실패(매핑 없음) | 우리 코드의 per-item 정규화 실패는 이 지점. 어댑터 역직렬화 실패는 청크 전체 실패라 별도 |
| 저장 시점 | 리액티브 구간에선 메모리 수집만, `block()` 이후 일괄 저장 | JPA 쓰기는 블로킹이라 리액터 스레드를 막지 않게 함. 수집 리스트는 병렬이라 스레드 안전(`CopyOnWriteArrayList`) |
| 실패 격리 | best-effort(저장 실패해도 검색 무영향) | 격리 기록이 검색(부분 성공)을 막지 않게 함 |
| 알려진 한계 | 캐시 히트마다 반복 기록 가능(드문 방어적 경로), 사유별 dedup은 향후 | 치환 실패가 드물어 영향 작음 |

### 검증

- 단위(`StaySearchServiceTest` +1): 객실 타입 매핑이 없어 치환 실패한 offer는 결과에서 제외되고(청크는 SUCCESS), `saveAll`로 `MAPPING_NOT_FOUND` 격리 레코드 1건 저장 확인
- 전체 스위트 61건 통과(기존 60 + 신규 1)

## 40. 결정 — 중복 상품 병합: 자동 병합하지 않음 (코드 없음)

두 공급사가 같은 물리적 숙소를 각자의 코드로 팔 때 이를 하나로 합칠지 검토했고, **서빙 경로에서 자동 병합하지 않고 분리 노출을 유지**하기로 결정했습니다. 상세 근거는 [extensions.md](docs/extensions.md) §3에 정리했습니다.

### 판단 과정

- 공급사 간 **공통 키가 없어** 병합하려면 이름·주소·좌표로 추정 매칭해야 합니다.
- **이름 기반 자동 매칭의 리스크가 이득보다 큼**: (1) 서로 다른 호텔이 같은 이름을 쓰는 경우(체인·일반명)가 반드시 있어, 병합 시 **다른 호텔의 가격·재고를 한 상품으로** 보여주는 치명적 혼동이 발생합니다(정확 일치로 좁혀도 남음). (2) 같은 호텔도 공급사 간 표기가 달라 정확 일치로는 재현율이 낮습니다. (3) 오병합을 걸러낼 **주소·좌표가 현재 모델에 없습니다**.
- 태깅(a)만 두는 안도 검토했으나, 데이터(큐레이션 정본) 없이 항상 null인 필드를 넣는 것은 "안 쓰는 코드 안 남김" 원칙에 어긋나 배제했습니다.

### 결정

- **분리 노출 유지**(현재 동작). 백엔드는 추정 병합을 하지 않습니다.
- 병합은 **권위 있는 큐레이션 정본 매핑**으로만 하고, 이름·퍼지는 큐레이션 후보 제안으로만 씁니다. 이는 운영 도구·주소/좌표 등 추가 데이터가 필요해 **범위 밖(향후)**.
- 코드 변경 없음(문서·정책만). 향후 큐레이션 도입 시 표시 방식(태깅 vs 그룹 응답)과 저장소 변경점은 §3에 남김.

## 41. AI 활용 기록 (확장 단계)

확장(재시도·서킷·지표·캐시·정규화 격리·중복 병합)에서는 **방향을 먼저 합의한 뒤 구현하는** 순서를 유지했습니다. AI는 대안·구현 초안·검증 도구로 쓰되, **설계 판단과 채택·거부는 직접** 내렸습니다. 특히 리스크를 선제적으로 짚어 **"하지 않기로 한" 결정(중복 병합 자동화, 미사용 필드)도 근거와 함께** 내린 것이 이 단계의 특징입니다(과설계·리스크 회피).

### 어떻게 물었는가

| 유형 | 실제 프롬프트(요지) | 의도 |
| --- | --- | --- |
| 방향 먼저 | "구현 전에 방향부터 잡자" · "이 개념부터 잡고 가자" | 코드 전에 설계·개념을 합의해 드리프트·재작업을 줄이기 위함 |
| 근거 문서화 | "우리가 논의한 근거를 문서에 채워줘" · "이 값(TTL 30초)은 어떤 근거로 나온 거지?" | 논의로 정한 판단 근거를 코드·문서에 남겨 나중에도 설명 가능하게 하기 위함 |
| 리스크 선제 검토 | "동일 이름으로 병합하면 리스크가 있을 것 같은데" · "이 구조에선 오병합이 치명적일 수 있다" | 채택 전에 실패 모드를 먼저 짚어 도입 여부를 판단하기 위함 |
| 실동작 교차검증 | "no-response인데 재시도 카운터가 안 쌓이는 것 같은데 확인해보자" | 구현이 의도대로 동작하는지 지표·실행으로 확인하기 위함 |

### 수용·수정·거부 기록

| 대상 | AI가 제시·구현한 것 | 판단 | 이유 |
| --- | --- | --- | --- |
| 청크 캐시 단위 | 청크 단위 제안 | 수용 + 근거 문서화 | 코드 안정성·single-flight 궁합을 근거로 채택하되, 그 근거를 문서에 명시하게 함 |
| 캐시 TTL 값 | "30초" 예시 | 수정 | 근거 없는 값임을 지적, 설정값 + "운영에서 튜닝"으로 재프레이밍 |
| B `resultCode` 재시도 | resultCode 실패를 전부 비재시도로 구현 | 수정 | E503(일시적)은 A의 5xx와 같아 재시도돼야 함을 지적 → 일시 코드만 재시도로 보강 |
| 청크 상한 강제 | 코드 상수 + 설정 이중 | 수정 | 한계값 중복이라 설정값 하나로 통일 |
| 매핑 비활성화(`active`) | 유지/구현/제거 3안 | 제거 선택 | 실시간 재고가 이미 delisting을 걸러 정합성 요건이 아님 → 미사용 필드 제거 |
| 중복 상품 병합 | 태깅/그룹/이름 병합 안 제시 | 거부(자동 병합 안 함) | 다른 호텔이 같은 이름을 쓰는 오병합이 치명적. 병합은 큐레이션 정본으로만 향후 |
| 재시도·예산 지표 공백 | — | 관찰 → 문서화 | no-response가 재시도 카운터에 안 잡히는 것을 관찰, 예산 취소의 의도된 동작임을 확인·기재 |

각 결정의 근거는 코드 주석·설계 문서·이 기록에 함께 남겼고, "하지 않기로 한" 결정(중복 병합, 매핑 비활성화)도 근거와 함께 문서화했습니다.
