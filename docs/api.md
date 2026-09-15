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
| `stayId` | 내부 숙소 식별자 (공급사 코드가 아님) |
| `stayName` | 숙소명 |
| `roomTypeId` | 내부 객실 타입 식별자 |
| `roomTypeName` | 객실 타입명 |
| `maxOccupancy` | 객실 1실 기준 최대 수용 인원 |
| `availableRoomCount` | 예약 가능 객실 수 (0이면 예약 불가, 제거하지 않고 노출) |
| `supplier` | 출처 공급사 |
| `breakfastIncluded` | 조식 포함 여부 |
| `price.currency` | ISO 4217 통화 코드 (환산 없음) |
| `price.grossTotalAmount` | 세금 포함 숙박 전체 총액 |
| `price.averageNightlyAmount` | 1박 평균가 (총액 ÷ 박수, 내림) |

`suppliers[]`는 공급사별 처리 상태를 담습니다. (상태 값과 부분 실패 표현은 [failure-handling.md](failure-handling.md) 참고)

공급사 원본 코드가 고객 응답의 식별자로 노출되지 않도록 합니다.

## 검증 오류

| 상황 | 응답 |
| --- | --- |
| `checkIn`이 `checkOut` 이후 | 400 (잘못된 날짜 범위) |
| `adults <= 0` 또는 인원 음수 | 400 (잘못된 인원 값) |

부분 실패는 오류가 아니라 HTTP 200 + 공급사별 상태로 표현합니다.
