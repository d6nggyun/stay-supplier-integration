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
CIRCUIT_OPEN
HTTP_ERROR
PROTOCOL_ERROR
NO_DATA
SKIPPED
```

단순 실패 목록 대신 공급사별 상태·응답 지연을 담는 이유는 다음과 같습니다.

- 어떤 공급사가 실패했는지 알 수 있습니다.
- 성공·실패 여부와 응답 시간을 함께 기록할 수 있습니다.
- 추후 공급사별 성공률과 타임아웃 비율을 측정하기 쉽습니다.
- 부분 성공과 전체 실패를 같은 구조로 표현할 수 있습니다.

### 상태값 판정 규칙

실패 표현 방식이 서로 달라도 어댑터 바깥에서는 아래 규칙으로 상태를 통일합니다.

| 상황 | 상태 |
| --- | --- |
| 정상 응답 (결과 0건 포함) | `SUCCESS` (`resultCount`로 건수 구분) |
| 여러 청크 중 일부만 성공 | `PARTIAL_SUCCESS` |
| 연결/응답 타임아웃 | `TIMEOUT` |
| Supplier A의 HTTP 4xx·5xx, 연결 실패 | `HTTP_ERROR` |
| Supplier B의 `resultCode != "0000"` | `PROTOCOL_ERROR` |
| 응답 역직렬화·형식 오류 | `PROTOCOL_ERROR` |
| 서킷 브레이커가 열려 차단됨 | `CIRCUIT_OPEN` |
| 매핑이 없어 호출하지 않음 | `SKIPPED` |

두 공급사는 어댑터 바깥에서 같은 `SupplierIntegrationException`으로 보이므로, 오케스트레이터가 어느 규칙을 적용할지 알 수 있도록 **어댑터가 예외에 실패 종류(`SupplierFailureKind`: `HTTP_ERROR`·`PROTOCOL_ERROR`·`TIMEOUT`)를 실어 보냅니다.** 오케스트레이터는 이 종류를 위 표의 상태로 옮깁니다. (A의 4xx/5xx·B의 실제 HTTP 오류 → `HTTP_ERROR`, B의 `resultCode` 오류·응답 역직렬화·형식 오류 → `PROTOCOL_ERROR`, 연결·읽기 타임아웃 → `TIMEOUT`)

오케스트레이터가 청크 호출에 직접 적용하는 응답 타임아웃과 공급사별 전체 예산도 같은 `TIMEOUT` 상태로 귀결합니다. 서킷 브레이커가 열려 호출이 차단되면 `CIRCUIT_OPEN`입니다. 한 공급사가 여러 청크로 나뉘고 그중 일부만 실패하면 성공 청크 결과를 유지한 채 `PARTIAL_SUCCESS`로 표기하며, 모든 청크가 실패하면 대표 상태를 우선순위(`CIRCUIT_OPEN` > `TIMEOUT` > `HTTP_ERROR` > `PROTOCOL_ERROR`)로 정합니다.

전송/HTTP 계층 실패(연결 실패·4xx·5xx)는 상태로는 `HTTP_ERROR`로 묶되, 재시도 여부는 내부적으로 구분합니다(5xx·연결 실패는 재시도, 4xx는 비재시도). 자세한 재시도·서킷 정책은 [resilience.md](resilience.md)를 참고하세요.

`NO_DATA`는 "정상 응답인데 조회 대상 자체가 0건"인 경우에만 한정해 사용하며, 정상 응답에 결과가 비어 있는 일반적인 경우는 `SUCCESS`(`resultCount: 0`)로 둡니다.

`SKIPPED`는 숙소 목록 동기화 실패 등으로 해당 공급사의 매핑이 없어 호출 대상에서 빠진 경우입니다. 어떤 공급사가 왜 결과에 없는지 응답만으로 설명하기 위해 별도 상태로 둡니다. (동기화 실패 정책은 [mapping.md](mapping.md#동기화-실패-처리) 참고)

### 전체 실패 시 응답

부분 실패는 HTTP 200으로 응답하지만, 결과를 만들 수 있는 성공 공급사가 하나도 없으면 상류 오류로 봅니다.

| 상황 | HTTP |
| --- | --- |
| 호출한 공급사가 전부 실패 | 502 |
| 매핑이 없어 호출 대상이 하나도 없음 (전부 `SKIPPED`) | 503 |

이 경우에도 응답 본문에는 `suppliers[]` 상태를 그대로 담아, 어느 공급사가 어떤 이유로 실패했는지 진단할 수 있게 합니다. 즉 HTTP 코드로는 "쓸 수 있는 결과가 없음"을 알리고, 본문으로는 원인을 전달합니다.

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

재시도와 서킷 브레이커는 필수(타임아웃·병렬·부분 실패·실패 판정)를 안정화한 뒤 확장으로 도입했습니다. 재시도는 전이성 실패(타임아웃·연결 실패·5xx)만 대상으로 하고, 서킷은 공급사별로 둡니다. 상세 설계·설정은 [resilience.md](resilience.md)를 참고하세요.
