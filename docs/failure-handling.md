# 부분 실패 처리

## 1. 기본 원칙

공급사 호출 결과는 예외를 그대로 상위 계층으로 전파하지 않고, 공급사별 처리 결과로 감쌉니다. 일부라도 결과가 있으면 성공 응답으로 돌려주고, 실패 사실은 응답 본문에 드러냅니다.

한 공급사만 실패한 경우에도 전체 검색 API는 HTTP 200으로 응답합니다.

## 2. 공급사별 상태 객체

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

상태 값:

```
SUCCESS
PARTIAL_SUCCESS
TIMEOUT
HTTP_ERROR
PROTOCOL_ERROR
NO_DATA
```

단순 실패 목록 대신 공급사별 상태·응답 지연을 담는 이유는 다음과 같습니다.

- 어떤 공급사가 실패했는지 알 수 있습니다.
- 성공·실패 여부와 응답 시간을 함께 기록할 수 있습니다.
- 추후 공급사별 성공률과 타임아웃 비율을 측정하기 쉽습니다.
- 부분 성공과 전체 실패를 같은 구조로 표현할 수 있습니다.

## 3. 부분 실패 표현 방식 선택 근거

| 방식 | 채택 여부 | 이유 |
| --- | --- | --- |
| HTTP 5xx | 제외 | 부분 실패 허용 요구와 배치됩니다. |
| HTTP 206 | 제외 | Range 응답 의미라 오용입니다. |
| 200 + 실패 목록 | 제외 | 성공률·지연 지표 수집 지점과 연결되지 않습니다. |
| 200 + 공급사별 상태 객체 | 채택 | 결과와 실패 사실, 지표 수집 지점을 하나의 구조로 표현합니다. |

## 4. 청크 부분 실패

한 공급사의 숙소 코드가 50개를 초과해 여러 청크로 나뉘고, 그중 일부 청크만 실패하는 경우가 있습니다.

이때는 **성공한 청크의 결과만 병합하고, 해당 공급사의 상태를 `PARTIAL_SUCCESS`로 표기**합니다. 얻은 결과를 버리지 않으면서 실패 사실을 응답에 드러내는 방향으로, 부분 실패 허용 원칙과 일관됩니다.

예시 데이터는 단일 청크라 구현 흐름에서는 이 경로가 실제로 실행되지 않지만, 상태의 의미는 위와 같이 확정해 둡니다.

## 5. 응답 예시 (한 공급사 타임아웃)

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
      "supplier": "SUPPLIER_B",
      "breakfastIncluded": true,
      "price": {
        "currency": "KRW",
        "grossTotalAmount": 452000,
        "averageNightlyAmount": 150666
      }
    }
  ],
  "suppliers": [
    { "supplier": "SUPPLIER_A", "status": "TIMEOUT", "latencyMs": 3001 },
    { "supplier": "SUPPLIER_B", "status": "SUCCESS", "latencyMs": 240, "resultCount": 1 }
  ]
}
```

## 6. 재시도와 서킷 브레이커

재시도와 서킷 브레이커는 필수 구현 이후의 확장 사항으로 둡니다. 초기에는 타임아웃, 병렬 호출, 부분 실패, 실패 코드 판정을 먼저 안정적으로 구현합니다. 도입한다면 5xx·타임아웃만 대상으로 검토합니다.
