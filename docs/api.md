# 통합 검색 API 명세

## 요청

```
GET /api/v1/stays/search
    ?checkIn=2026-09-01
    &checkOut=2026-09-04
    &adults=2
    &children=0
```

| 파라미터 | 타입 | 설명 |
| --- | --- | --- |
| `checkIn` | date | 체크인일 (`YYYY-MM-DD`) |
| `checkOut` | date | 체크아웃일 (숙박일에 미포함) |
| `adults` | int | 성인 인원 (0보다 커야 함) |
| `children` | int | 아동 인원 (0 이상) |

## 응답

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
      "price": {
        "currency": "KRW",
        "grossTotalAmount": 429000,
        "averageNightlyAmount": 143000
      }
    }
  ],
  "suppliers": [
    { "supplier": "SUPPLIER_A", "status": "SUCCESS", "latencyMs": 180, "resultCount": 1 },
    { "supplier": "SUPPLIER_B", "status": "SUCCESS", "latencyMs": 220, "resultCount": 1 }
  ]
}
```

## 응답 필드

`results[]`의 각 항목은 다음 값을 반드시 내부 식별자 기준으로 제공합니다.

| 필드 | 설명 |
| --- | --- |
| `stayId` | 내부 숙소 식별자 (공급사 코드가 아님, 매핑으로 치환) |
| `stayName` | 숙소명 (재고·요금 응답에서 취득) |
| `roomTypeId` | 내부 객실 타입 식별자 (매핑으로 치환) |
| `roomTypeName` | 객실 타입명 (재고·요금 응답에서 취득) |
| `maxOccupancy` | 객실 1실 기준 최대 수용 인원 (재고·요금 응답에서 취득) |
| `availableRoomCount` | 예약 가능 객실 수 (0이면 예약 불가, 제거하지 않고 노출) |
| `supplier` | 출처 공급사 |
| `breakfastIncluded` | 조식 포함 여부 |
| `price.currency` | ISO 4217 통화 코드 (환산 없음) |
| `price.grossTotalAmount` | 세금 포함 숙박 전체 총액 |
| `price.averageNightlyAmount` | 1박 평균가 (총액 ÷ 박수, 내림) |

`suppliers[]`는 공급사별 처리 상태를 담습니다. 매핑이 없어 호출하지 않은 공급사도 `SKIPPED` 상태로 포함되어, 어느 공급사가 왜 결과에 없는지 응답만으로 드러납니다. (상태 값과 부분 실패 표현은 [failure-handling.md](failure-handling.md) 참고)

공급사 원본 코드가 고객 응답의 식별자로 노출되지 않도록 합니다.

## 응답 코드

| 상황 | HTTP |
| --- | --- |
| 정상 (부분 실패 포함) | 200 |
| `checkIn`이 `checkOut` 이후 | 400 (잘못된 날짜 범위) |
| `adults <= 0` 또는 인원 음수 | 400 (잘못된 인원 값) |
| 호출한 공급사가 전부 실패 | 502 |
| 매핑이 없어 호출 대상이 하나도 없음 (전부 `SKIPPED`) | 503 |

부분 실패는 오류가 아니라 HTTP 200 + 공급사별 상태로 표현합니다. 반대로 쓸 수 있는 결과가 하나도 없으면 5xx로 응답하되, 본문에는 `suppliers[]` 상태를 그대로 담아 원인을 전달합니다. (근거는 [failure-handling.md](failure-handling.md#전체-실패-시-응답) 참고)

`suppliers[]`의 각 항목은 `supplier`·`status`·`latencyMs`를 항상 담고, 성공(부분 성공)일 때만 `resultCount`, 실패일 때만 `errorCode`·`errorMessage`를 담습니다(그 외에는 생략).

## 잘못된 요청 응답 (400)

날짜 범위·인원 검증 실패, 필수 파라미터 누락, 형식 오류는 공통 오류 형식으로 반환합니다.

```json
{ "code": "INVALID_REQUEST", "message": "checkIn은 checkOut보다 이전이어야 합니다." }
```

| code | 상황 |
| --- | --- |
| `INVALID_REQUEST` | 날짜 범위·인원 값이 규칙에 어긋남 |
| `MISSING_PARAMETER` | 필수 파라미터 누락 |
| `INVALID_PARAMETER` | 파라미터 형식 오류(예: 날짜 형식) |

## 숙소 목록 수동 동기화

```
POST /api/v1/admin/catalog-sync
```

주기 동기화가 기본 경로이며, 이 엔드포인트는 즉시 재적재가 필요할 때의 보조 수단입니다. 공급사별 실패를 격리하며 동기적으로 수행하고, 완료되면 `200 OK`로 응답합니다. (동기화 정책은 [mapping.md](mapping.md) 참고)
