# Supplier Adapter 설계

## 1. 공통 인터페이스

공급사별 어댑터는 동일한 내부 인터페이스를 구현합니다.

```java
public interface SupplierAdapter {

    SupplierType supplier();

    Mono<SupplierCatalog> fetchCatalog();

    Mono<SupplierSearchResult> search(
            SearchCriteria criteria,
            List<String> supplierStayCodes
    );
}
```

구현체는 `SupplierAAdapter`, `SupplierBAdapter`입니다.

## 2. 어댑터 내부 책임

각 어댑터는 다음을 담당합니다.

- 공급사 요청 URL 생성
- 인증 헤더 설정 (`X-Api-Key`)
- 공급사 요청 DTO 생성
- WebClient 호출
- 공급사 응답 DTO 역직렬화
- HTTP 상태 코드 확인
- 공급사 응답의 실패 코드 확인
- 응답을 내부 표준 모델로 변환

도메인 계층은 다음 사실을 알지 못해야 합니다.

- 한 공급사가 `hotelCode`를, 다른 공급사가 `propertyId`를 사용한다는 점
- 한 공급사가 HTTP 200 상태에서 실패를 표현한다는 점
- 요금이 날짜별 단가인지 숙박 전체 총액인지

즉 공급사별 요청/응답 형식과 실패 표현 방식이 도메인 계층으로 새어 나가지 않도록 어댑터가 경계를 담당합니다.

## 3. 실패 판정 통일

두 공급사는 실패를 서로 다르게 표현합니다.

| 공급사 | 실패 표현 |
| --- | --- |
| Supplier A | HTTP 4xx / 5xx |
| Supplier B | 항상 HTTP 200 + 본문 `resultCode` |

Supplier B는 장애가 발생해도 HTTP 200을 반환할 수 있으므로, HTTP 상태 코드만 검사하면 안 되고 응답 본문의 `resultCode`를 함께 확인해야 합니다.

```
resultCode == "0000"   → 정상 응답
resultCode != "0000"   → SupplierIntegrationException으로 변환
```

Supplier A의 HTTP 4xx·5xx와 Supplier B의 `resultCode` 오류는 어댑터 바깥에서는 동일한 공급사 연동 실패로 처리합니다. 상위 계층은 실패의 표현 방식이 아니라 "연동 실패"라는 사실만 다룹니다.

## 4. 정규화 실패 처리

응답을 내부 표준 모델로 변환하는 과정에서, 매핑에 없는 객실 타입 코드가 오는 등 정규화할 수 없는 항목이 있을 수 있습니다.

이번 구현의 방침은 **해당 항목을 결과에서 스킵하고 경고 로그를 남기는 것**입니다. 변환할 수 없는 응답을 버리지 않고 격리·기록해 추후 분석 가능하게 만드는 정규화 실패 격리는 확장 범위로 두고, 이번에는 설계 방향만 남깁니다.

## 5. 신규 공급사 추가 방식

새로운 Supplier C를 추가할 때 기존 도메인이나 검색 API를 수정하지 않는 것을 목표로 합니다. 추가 작업은 다음과 같습니다.

1. Supplier C 전용 응답 DTO 작성
2. `SupplierAdapter` 구현
3. Supplier C 응답을 표준 모델로 변환
4. Supplier C 설정값 등록
5. 어댑터 목록에 빈 등록
6. Supplier C 테스트 추가

도메인 계층은 어댑터 인터페이스만 의존하므로, 공급사별 API 형식이 도메인으로 확산되지 않습니다.
