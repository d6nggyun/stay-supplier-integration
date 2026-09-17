# 아키텍처 설계

## 1. 설계 목표

이 시스템의 목표는 서로 다른 외부 숙박 상품 공급사의 API를 도메인 계층에서 직접 다루지 않고, 내부 표준 모델로 변환해 하나의 검색 API로 제공하는 것입니다.

고객은 상품이 어느 공급사에서 왔는지와 무관하게 동일한 형태의 검색 결과를 받습니다.

전체 도메인을 고려하되, 이번 구현에서는 검색 흐름이 처음부터 끝까지 끊김 없이 동작하는 것을 최우선으로 둡니다.

## 2. 범위

### 구현 범위

- 통합 숙박 상품 모델 ([domain-model.md](domain-model.md))
- 공급사 코드와 내부 식별자 매핑 저장 ([mapping.md](mapping.md))
- 공급사 연동 어댑터 ([supplier-adapter.md](supplier-adapter.md))
- 통합 검색 API ([search-flow.md](search-flow.md), [api.md](api.md))
- 타임아웃·부분 실패·실패 판정 통일 ([failure-handling.md](failure-handling.md))
- Mock 공급사 ([mock-supplier.md](mock-supplier.md))

### 범위 밖

인증·인가, 결제, 관리자 기능, 프론트엔드, 실제 외부 API 호출, 지역·키워드 검색 필터, 정렬·페이징은 이번 범위에서 제외합니다.

### 확장 범위

확장 범위는 필수 흐름을 안정화한 뒤 다뤘습니다. 재시도·서킷 브레이커([resilience.md](resilience.md)), 연동 지표·모니터링([observability.md](observability.md)), 요금/재고 캐시([cache.md](cache.md)), 정규화 실패 격리([supplier-adapter.md](supplier-adapter.md) §4)는 구현했고, 중복 상품 병합은 오병합 리스크로 **자동 병합하지 않기로 결정**했습니다([extensions.md](extensions.md) §3). 다중 통화·예약 대행 흐름은 설계 초안으로 남겨 향후 검토합니다.

## 3. 계층 구조

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

### Controller

HTTP 요청과 응답만 담당합니다. 공급사별 DTO나 공급사 코드를 직접 다루지 않으며, 고객 검색 조건을 내부 `SearchCriteria` 객체로 변환해 애플리케이션 서비스에 전달합니다.

### Application Service

고객의 검색 요청을 하나의 유스케이스로 조정합니다. 검색 조건 검증, 매핑 조회, 공급사별 코드 그룹화, 어댑터 호출, 결과 병합, 공급사별 처리 상태 생성을 담당합니다.

### Supplier Adapter

각 공급사의 API 형식과 통신 방식을 캡슐화합니다. 공급사의 요청 파라미터, 응답 DTO, 실패 표현 방식은 어댑터 내부에서만 알고 있어야 합니다.

### Domain

공급사 API의 필드명과 무관한 내부 표준 모델을 관리합니다. 공급사별 식별자는 도메인에서 모두 내부 식별자로 치환되어 처리되며, 최종 응답에는 내부 식별자만 노출됩니다.

### Persistence

숙소와 객실 타입의 공급사 코드 및 내부 식별자 매핑만 저장합니다. 재고와 요금은 검색 시점의 외부 응답이 원본이므로 저장하지 않습니다.

JPA 엔티티(`StayMapping`·`RoomTypeMapping`)는 `jakarta.persistence`에 의존하므로 도메인이 아니라 이 인프라 계층에 둡니다. 판단 기준은 애노테이션 자체가 아니라 그것이 나타내는 프레임워크 의존이며, 도메인 계층은 프레임워크에 의존하지 않게 유지합니다. 매핑 테이블은 코드↔식별자를 저장하는 부기 테이블이지 핵심 도메인 개념이 아니라는 점도 같은 결론을 가리킵니다.

### 엔티티 배치 결정 (도메인 vs 인프라)

JPA 엔티티를 도메인 계층에 둘지, 인프라에 분리할지는 헥사고날/DDD에서 견해가 갈리는 지점입니다. 레퍼런스를 대조해 결정을 기록합니다.

- **분리파 (다수 헥사고날/DDD 권장)**: JPA 엔티티를 도메인에 두지 않습니다. 도메인 모델은 프레임워크 애노테이션 없이 순수하게 두고, 인프라에 별도 JPA 엔티티를 두어 리포지토리 어댑터가 도메인↔JPA를 변환합니다. 기술 종속을 도메인에서 떼어내 테스트·재사용성을 확보한다는 논리입니다.
- **실용파**: 도메인에 `@Entity`를 직접 붙여 모델을 하나로 유지하기도 합니다. 분리는 매핑 코드·유지보수·메모리 오버헤드를 늘리므로, 모델이 단순하면 분리 이득이 작다는 입장입니다.

**결정: 도메인은 프레임워크-프리(값 객체)로 두고, JPA 엔티티(매핑)는 `infrastructure`에 둡니다.** 분리파의 순수성을 택하되, 실용파가 지적하는 매핑 변환 오버헤드는 이 프로젝트에선 발생하지 않습니다. 저장 대상인 `stay_mapping`·`room_type_mapping`은 코드↔식별자 부기 테이블이지 도메인 엔티티가 아니어서, "도메인 엔티티 ↔ JPA 엔티티" 이중 모델 자체가 없기 때문입니다. 즉 도메인 순수성은 얻으면서 이중 매핑 비용은 피하는 위치입니다.

이 방향은 DDD의 **영속 무지(Persistence Ignorance)** 원칙 — 도메인 클래스는 저장 방식(ORM·DB)에 오염되지 않아야 한다 — 과 클린 아키텍처의 영속 어댑터 패턴을 따른 것입니다. 참고로 Hombergs는 도메인과 영속 모델의 경계 매핑을 No Mapping / One-Way / Two-Way / Full 네 가지 전략으로 구분합니다. 즉 "분리냐 결합이냐"는 이분법이 아니라 매핑 전략의 스펙트럼이며, 모델 복잡도에 따라 고르는 문제입니다. 우리는 공유되는 개념 자체가 없어 이 스펙트럼을 논할 필요조차 없는 경우입니다.

참고 레퍼런스:

정본(책·원칙)

- Eric Evans, *Domain-Driven Design* — 도메인 계층은 DB 연결 같은 기술 세부에 오염되지 않아야 한다(참고: [Fowler, Domain Driven Design](https://martinfowler.com/bliki/DomainDrivenDesign.html)).
- Vaughn Vernon, *Implementing Domain-Driven Design* — 아키텍처·리포지토리 장에서 도메인과 영속 기술의 분리를 다룸.
- Tom Hombergs, *Get Your Hands Dirty on Clean Architecture* — 영속 어댑터가 도메인 모델을 DB 스키마로 매핑하고 JPA 엔티티·애노테이션은 어댑터 내부에 캡슐화한다. 경계 매핑을 No/One-Way/Two-Way/Full 전략으로 구분([O'Reilly](https://www.oreilly.com/library/view/get-your-hands/9781805128373/)).
- 원칙 요약: Persistence Ignorance — [DevIQ](https://deviq.com/principles/persistence-ignorance/).

커뮤니티 글(통념 확인)

- Baeldung — [Hexagonal Architecture, DDD, and Spring](https://www.baeldung.com/hexagonal-architecture-ddd-spring)
- Vaadin — [DDD & Hexagonal Architecture](https://vaadin.com/blog/ddd-part-3-domain-driven-design-and-the-hexagonal-architecture)
- Ürgo Ringo — [Separating Persistence and Domain Models](https://urgo.medium.com/separating-persistence-and-domain-models-cc3a7e7cd4e5)
- DZone — [Domain-Driven Design With JPA](https://dzone.com/articles/domain-driven-design-with-jpa-a-practical-guide)

## 4. 실행 모델

MVC(서블릿) 위에서 공급사 호출 구간만 리액티브로 처리합니다. WebFlux 전면 도입은 하지 않습니다.

- 오케스트레이터는 `Mono`·`Flux`로 공급사 호출을 병렬 조합합니다.
- 리액티브 경계는 애플리케이션 서비스 내부로 한정합니다. 서비스 끝에서 결과를 블로킹(`block`)으로 받아 컨트롤러는 동기 방식으로 반환합니다.
- 이렇게 나눈 이유는 병렬·타임아웃·부분 실패 같은 핵심 동작을 리액티브로 확보하면서도, 컨트롤러와 테스트는 단순한 동기 흐름으로 유지하기 위해서입니다. 이번 규모에서는 요청당 서블릿 스레드 하나가 응답까지 점유하는 비용이 문제가 되지 않습니다.

## 5. 핵심 흐름

```
[사전] 공급사 숙소 목록 조회 → 내부 숙소·객실 타입으로 매핑해 저장

고객 검색 요청 (날짜·인원)
    ↓
내부 DB의 보유 숙소를 공급사별 코드로 묶음 (50개씩)
    ↓
Supplier A·B 재고·요금 병렬 조회 (WebClient)
    ↓
각 응답을 내부 표준 숙박 상품 모델로 정규화
    ↓
재고·요금 계산 및 결과 병합 (한쪽이 실패해도 나머지로 응답)
    ↓
공급사별 처리 상태와 함께 통합 검색 결과 반환
```
