package d6nggyun.stay;

import com.jayway.jsonpath.JsonPath;
import d6nggyun.stay.infrastructure.persistence.entity.RoomTypeMapping;
import d6nggyun.stay.infrastructure.persistence.entity.StayMapping;
import d6nggyun.stay.infrastructure.persistence.repository.RoomTypeMappingRepository;
import d6nggyun.stay.infrastructure.persistence.repository.StayMappingRepository;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static d6nggyun.stay.domain.SupplierType.SUPPLIER_A;
import static d6nggyun.stay.domain.SupplierType.SUPPLIER_B;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전체 스택 통합 테스트. 컨트롤러 → 오케스트레이터 → 실제 WebClient → MockWebServer(공급사 대역)를 태워,
 * 정상 병합과 실제 응답 타임아웃(부분 실패)을 자동 검증한다. 매핑은 DB에 직접 심어 검색 경로만 격리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaySearchIntegrationTest {

    private static final MockWebServer supplierServer = new MockWebServer();
    // 공급사 B 응답 지연(ms). 0이면 즉시 응답, 응답 타임아웃(800ms)보다 크면 TIMEOUT을 유발한다.
    private static volatile long supplierBDelayMs = 0;

    @DynamicPropertySource
    static void supplierProps(DynamicPropertyRegistry registry) throws IOException {
        supplierServer.setDispatcher(dispatcher());
        supplierServer.start();
        String baseUrl = "http://localhost:" + supplierServer.getPort();
        registry.add("supplier.a.base-url", () -> baseUrl);
        registry.add("supplier.b.base-url", () -> baseUrl);
        registry.add("supplier.search.response-timeout-ms", () -> "800");
        registry.add("supplier.search.connect-timeout-ms", () -> "1000");
        registry.add("supplier.search.request-budget-ms", () -> "5000");
        registry.add("supplier.sync.enabled", () -> "false");
    }

    @AfterAll
    static void shutdown() throws IOException {
        supplierServer.shutdown();
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private StayMappingRepository stayMappingRepository;
    @Autowired
    private RoomTypeMappingRepository roomTypeMappingRepository;

    @BeforeEach
    void seed() {
        supplierBDelayMs = 0;
        roomTypeMappingRepository.deleteAll();
        stayMappingRepository.deleteAll();
        stayMappingRepository.save(StayMapping.of(SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"));
        stayMappingRepository.save(StayMapping.of(SUPPLIER_A, "A-10044", "Namsan Garden Stay"));
        stayMappingRepository.save(StayMapping.of(SUPPLIER_B, "B77120", "Riverside Hotel Seoul"));
        roomTypeMappingRepository.save(roomType(SUPPLIER_A, "A-10023", "DLX-TWN", "Deluxe Twin"));
        roomTypeMappingRepository.save(roomType(SUPPLIER_A, "A-10044", "STD-DBL", "Standard Double"));
        roomTypeMappingRepository.save(roomType(SUPPLIER_B, "B77120", "R-401", "Deluxe Twin Room"));
    }

    @Test
    void 두_공급사가_정상이면_200과_병합된_결과를_반환한다() {
        ResponseEntity<String> response = search();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat((int) JsonPath.read(body, "$.results.length()")).isEqualTo(3);
        List<String> statuses = JsonPath.read(body, "$.suppliers[*].status");
        assertThat(statuses).containsExactlyInAnyOrder("SUCCESS", "SUCCESS");
        // 응답에 내부 전용 필드가 노출되지 않는다.
        assertThat(body).doesNotContain("dailyAvailability");
    }

    @Test
    void 한_공급사가_응답_타임아웃이면_200에_TIMEOUT으로_나머지_결과를_반환한다() {
        supplierBDelayMs = 1500; // 응답 타임아웃(800ms) 초과

        ResponseEntity<String> response = search();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        // A만 결과(2건)로 응답하고 B는 TIMEOUT 상태로 표기된다.
        assertThat((int) JsonPath.read(body, "$.results.length()")).isEqualTo(2);
        List<String> aStatus = JsonPath.read(body, "$.suppliers[?(@.supplier=='SUPPLIER_A')].status");
        List<String> bStatus = JsonPath.read(body, "$.suppliers[?(@.supplier=='SUPPLIER_B')].status");
        assertThat(aStatus).containsExactly("SUCCESS");
        assertThat(bStatus).containsExactly("TIMEOUT");
    }

    @Test
    void 매핑이_없으면_503을_반환한다() {
        roomTypeMappingRepository.deleteAll();
        stayMappingRepository.deleteAll();

        ResponseEntity<String> response = search();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        List<String> statuses = JsonPath.read(response.getBody(), "$.suppliers[*].status");
        assertThat(statuses).containsOnly("SKIPPED");
    }

    private ResponseEntity<String> search() {
        return rest.getForEntity(
                "/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0", String.class);
    }

    private RoomTypeMapping roomType(d6nggyun.stay.domain.SupplierType supplier, String stayCode,
                                    String roomTypeCode, String roomTypeName) {
        return RoomTypeMapping.builder()
                .supplier(supplier)
                .supplierStayCode(stayCode)
                .supplierRoomTypeCode(roomTypeCode)
                .roomTypeName(roomTypeName)
                .maxOccupancy(2)
                .build();
    }

    private static Dispatcher dispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/a/v1/availability")) {
                    return json(A_AVAILABILITY);
                }
                if (path != null && path.startsWith("/b/api/search")) {
                    MockResponse response = json(B_SEARCH);
                    if (supplierBDelayMs > 0) {
                        response.setBodyDelay(supplierBDelayMs, TimeUnit.MILLISECONDS);
                    }
                    return response;
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private static MockResponse json(String body) {
        return new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static final String A_AVAILABILITY = """
            {
              "items": [
                {
                  "hotelCode": "A-10023", "hotelName": "Riverside Hotel Seoul",
                  "roomTypeCode": "DLX-TWN", "roomTypeName": "Deluxe Twin",
                  "maxOccupancy": 2, "breakfastIncluded": false, "currency": "KRW",
                  "dailyRates": [
                    { "date": "2026-09-01", "remainingRooms": 3, "nightlyRate": 120000, "taxAmount": 12000 },
                    { "date": "2026-09-02", "remainingRooms": 1, "nightlyRate": 150000, "taxAmount": 15000 },
                    { "date": "2026-09-03", "remainingRooms": 5, "nightlyRate": 120000, "taxAmount": 12000 }
                  ]
                },
                {
                  "hotelCode": "A-10044", "hotelName": "Namsan Garden Stay",
                  "roomTypeCode": "STD-DBL", "roomTypeName": "Standard Double",
                  "maxOccupancy": 2, "breakfastIncluded": false, "currency": "KRW",
                  "dailyRates": [
                    { "date": "2026-09-01", "remainingRooms": 2, "nightlyRate": 88000, "taxAmount": 8800 },
                    { "date": "2026-09-02", "remainingRooms": 0, "nightlyRate": 99000, "taxAmount": 9900 },
                    { "date": "2026-09-03", "remainingRooms": 4, "nightlyRate": 88000, "taxAmount": 8800 }
                  ]
                }
              ]
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
}
