package d6nggyun.stay.adapter.a;

import d6nggyun.stay.adapter.result.SupplierCatalog;
import d6nggyun.stay.adapter.result.SupplierOffer;
import d6nggyun.stay.adapter.result.SupplierSearchResult;
import d6nggyun.stay.domain.SearchCriteria;
import d6nggyun.stay.global.exception.SupplierIntegrationException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierAAdapterTest {

    private MockWebServer server;
    private SupplierAAdapter adapter;

    private final SearchCriteria criteria = SearchCriteria.of(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04"), 2, 0);

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        adapter = new SupplierAAdapter(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private MockResponse jsonResponse(int code, String body) {
        return new MockResponse().setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    @Test
    void 재고요금_응답을_표준_offer로_정규화한다() {
        server.enqueue(jsonResponse(200, A_AVAILABILITY));

        SupplierSearchResult result = adapter.search(criteria, List.of("A-10023", "A-10044")).block();

        assertThat(result.offers()).hasSize(2);
        SupplierOffer riverside = result.offers().get(0);
        // 세금 포함 총액 = Σ(nightlyRate + taxAmount) = 429,000, 1박 평균가 = 143,000
        assertThat(riverside.price().grossTotalAmount()).isEqualTo(429_000);
        assertThat(riverside.price().averageNightlyAmount()).isEqualTo(143_000);
        assertThat(riverside.price().currency()).isEqualTo("KRW");
        // 재고 = min(3, 1, 5) = 1
        assertThat(riverside.availableRoomCount()).isEqualTo(1);
        assertThat(riverside.breakfastIncluded()).isFalse();
        assertThat(riverside.supplierStayCode()).isEqualTo("A-10023");
        assertThat(riverside.supplierRoomTypeCode()).isEqualTo("DLX-TWN");
    }

    @Test
    void 하루라도_재고가_0이면_예약_가능_객실_수가_0이다() {
        server.enqueue(jsonResponse(200, A_AVAILABILITY));

        SupplierSearchResult result = adapter.search(criteria, List.of("A-10023", "A-10044")).block();

        // Namsan Garden Stay: 2026-09-02 재고 0 -> min = 0
        SupplierOffer namsan = result.offers().get(1);
        assertThat(namsan.supplierStayCode()).isEqualTo("A-10044");
        assertThat(namsan.availableRoomCount()).isEqualTo(0);
    }

    @Test
    void HTTP_5xx는_연동_실패로_변환한다() {
        server.enqueue(jsonResponse(503, "{ \"error\": \"SERVICE_UNAVAILABLE\" }"));

        assertThatThrownBy(() -> adapter.search(criteria, List.of("A-10023")).block())
                .isInstanceOf(SupplierIntegrationException.class);
    }

    @Test
    void 숙소_목록을_표준_카탈로그로_정규화한다() {
        server.enqueue(jsonResponse(200, A_HOTELS));

        SupplierCatalog catalog = adapter.fetchCatalog().block();

        assertThat(catalog.stays()).hasSize(2);
        SupplierCatalog.Stay riverside = catalog.stays().get(0);
        assertThat(riverside.supplierStayCode()).isEqualTo("A-10023");
        assertThat(riverside.roomTypes()).hasSize(1);
        assertThat(riverside.roomTypes().get(0).supplierRoomTypeCode()).isEqualTo("DLX-TWN");
        assertThat(riverside.roomTypes().get(0).maxOccupancy()).isEqualTo(2);
    }

    @Test
    void 잘못된_응답_형식은_연동_실패로_변환한다() {
        server.enqueue(jsonResponse(200, "not a json"));

        assertThatThrownBy(() -> adapter.fetchCatalog().block())
                .isInstanceOf(SupplierIntegrationException.class);
    }

    private static final String A_HOTELS = """
            {
              "items": [
                { "hotelCode": "A-10023", "hotelName": "Riverside Hotel Seoul",
                  "roomTypes": [ { "roomTypeCode": "DLX-TWN", "roomTypeName": "Deluxe Twin", "maxOccupancy": 2 } ] },
                { "hotelCode": "A-10044", "hotelName": "Namsan Garden Stay",
                  "roomTypes": [ { "roomTypeCode": "STD-DBL", "roomTypeName": "Standard Double", "maxOccupancy": 2 } ] }
              ]
            }
            """;

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
}
