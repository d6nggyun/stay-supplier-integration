package d6nggyun.stay.mock;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supplier A·B의 숙소 목록(①)·재고요금(②) 엔드포인트와, 상태를 바꾸는 제어 엔드포인트를 제공한다.
 *
 * 상태는 공급사별로 normal | error | no-response 를 가지며 런타임에 전환한다.
 *   POST /control/a/mode?value=error       → A 장애(HTTP 503)
 *   POST /control/b/mode?value=no-response → B 무응답
 *   POST /control/a/mode?value=normal      → 정상으로 되돌리기
 *
 * 숙소 목록(①)에는 장애 모드를 걸지 않는다(재고요금 ②만 전환). 요청 파라미터·인증 헤더는 무시하고 고정 응답을 준다.
 */
@RestController
public class MockSupplierController {

    /** 응답 타임아웃(기본 3s)을 넘겨 무응답을 재현하기 위한 지연. */
    private static final long NO_RESPONSE_MILLIS = 30_000L;

    private final Map<String, String> modes = new ConcurrentHashMap<>();

    private String mode(String supplier) {
        return modes.getOrDefault(supplier, "normal");
    }

    @PostMapping("/control/{supplier}/mode")
    public Map<String, String> setMode(@PathVariable String supplier, @RequestParam String value) {
        modes.put(supplier, value);
        return Map.of(supplier, value);
    }

    // ── Supplier A ──────────────────────────────────────────────

    @GetMapping(value = "/a/v1/hotels", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> hotelsA() {
        return ResponseEntity.ok(A_HOTELS);
    }

    @GetMapping(value = "/a/v1/availability", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> availabilityA() throws InterruptedException {
        return switch (mode("a")) {
            case "error" -> ResponseEntity.status(503).body(A_ERROR);
            case "no-response" -> {
                Thread.sleep(NO_RESPONSE_MILLIS);
                yield ResponseEntity.ok("{}");
            }
            default -> ResponseEntity.ok(A_AVAILABILITY);
        };
    }

    // ── Supplier B ──────────────────────────────────────────────

    @GetMapping(value = "/b/api/properties", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> propertiesB() {
        return ResponseEntity.ok(B_PROPERTIES);
    }

    @GetMapping(value = "/b/api/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchB() throws InterruptedException {
        return switch (mode("b")) {
            // B는 장애 상황에서도 HTTP 200 + 본문 resultCode 로 실패를 표현한다.
            case "error" -> ResponseEntity.ok(B_ERROR);
            case "no-response" -> {
                Thread.sleep(NO_RESPONSE_MILLIS);
                yield ResponseEntity.ok("{}");
            }
            default -> ResponseEntity.ok(B_SEARCH);
        };
    }

    // ── 고정 응답 (부록 A.1 / A.2 예시) ─────────────────────────

    private static final String A_HOTELS = """
            {
              "items": [
                {
                  "hotelCode": "A-10023",
                  "hotelName": "Riverside Hotel Seoul",
                  "roomTypes": [
                    { "roomTypeCode": "DLX-TWN", "roomTypeName": "Deluxe Twin", "maxOccupancy": 2 }
                  ]
                },
                {
                  "hotelCode": "A-10044",
                  "hotelName": "Namsan Garden Stay",
                  "roomTypes": [
                    { "roomTypeCode": "STD-DBL", "roomTypeName": "Standard Double", "maxOccupancy": 2 }
                  ]
                }
              ]
            }
            """;

    private static final String A_AVAILABILITY = """
            {
              "items": [
                {
                  "hotelCode": "A-10023",
                  "hotelName": "Riverside Hotel Seoul",
                  "roomTypeCode": "DLX-TWN",
                  "roomTypeName": "Deluxe Twin",
                  "maxOccupancy": 2,
                  "breakfastIncluded": false,
                  "currency": "KRW",
                  "dailyRates": [
                    { "date": "2026-09-01", "remainingRooms": 3, "nightlyRate": 120000, "taxAmount": 12000 },
                    { "date": "2026-09-02", "remainingRooms": 1, "nightlyRate": 150000, "taxAmount": 15000 },
                    { "date": "2026-09-03", "remainingRooms": 5, "nightlyRate": 120000, "taxAmount": 12000 }
                  ]
                },
                {
                  "hotelCode": "A-10044",
                  "hotelName": "Namsan Garden Stay",
                  "roomTypeCode": "STD-DBL",
                  "roomTypeName": "Standard Double",
                  "maxOccupancy": 2,
                  "breakfastIncluded": false,
                  "currency": "KRW",
                  "dailyRates": [
                    { "date": "2026-09-01", "remainingRooms": 2, "nightlyRate": 88000, "taxAmount": 8800 },
                    { "date": "2026-09-02", "remainingRooms": 0, "nightlyRate": 99000, "taxAmount": 9900 },
                    { "date": "2026-09-03", "remainingRooms": 4, "nightlyRate": 88000, "taxAmount": 8800 }
                  ]
                }
              ]
            }
            """;

    private static final String A_ERROR = """
            { "error": "SERVICE_UNAVAILABLE", "message": "temporarily unavailable" }
            """;

    private static final String B_PROPERTIES = """
            {
              "resultCode": "0000",
              "resultMessage": "SUCCESS",
              "data": {
                "items": [
                  {
                    "propertyId": "B77120",
                    "propertyName": "Riverside Hotel Seoul",
                    "rooms": [
                      { "roomId": "R-401", "roomName": "Deluxe Twin Room", "maxOccupancy": 2 }
                    ]
                  }
                ]
              }
            }
            """;

    private static final String B_SEARCH = """
            {
              "resultCode": "0000",
              "resultMessage": "SUCCESS",
              "data": {
                "items": [
                  {
                    "propertyId": "B77120",
                    "propertyName": "Riverside Hotel Seoul",
                    "roomId": "R-401",
                    "roomName": "Deluxe Twin Room",
                    "maxOccupancy": 2,
                    "breakfastIncluded": true,
                    "currency": "KRW",
                    "totalPrice": 452000,
                    "taxIncluded": true,
                    "inventory": [
                      { "date": "2026-09-01", "remainingRooms": 3 },
                      { "date": "2026-09-02", "remainingRooms": 1 },
                      { "date": "2026-09-03", "remainingRooms": 5 }
                    ]
                  }
                ]
              }
            }
            """;

    private static final String B_ERROR = """
            { "resultCode": "E503", "resultMessage": "TEMPORARILY_UNAVAILABLE", "data": null }
            """;
}
