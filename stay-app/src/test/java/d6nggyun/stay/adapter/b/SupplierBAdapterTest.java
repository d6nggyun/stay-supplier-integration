package d6nggyun.stay.adapter.b;

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

class SupplierBAdapterTest {

    private MockWebServer server;
    private SupplierBAdapter adapter;

    private final SearchCriteria criteria = SearchCriteria.of(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04"), 2, 0);

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        adapter = new SupplierBAdapter(webClient);
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
        server.enqueue(jsonResponse(200, B_SEARCH));

        SupplierSearchResult result = adapter.search(criteria, List.of("B77120")).block();

        assertThat(result.offers()).hasSize(1);
        SupplierOffer riverside = result.offers().get(0);
        // B는 세금 포함 총액을 그대로 사용: 452,000, 1박 평균가 = 452,000/3 = 150,666(내림)
        assertThat(riverside.price().grossTotalAmount()).isEqualTo(452_000);
        assertThat(riverside.price().averageNightlyAmount()).isEqualTo(150_666);
        assertThat(riverside.price().currency()).isEqualTo("KRW");
        // 재고 = min(3, 1, 5) = 1
        assertThat(riverside.availableRoomCount()).isEqualTo(1);
        assertThat(riverside.breakfastIncluded()).isTrue();
        assertThat(riverside.supplierStayCode()).isEqualTo("B77120");
        assertThat(riverside.supplierRoomTypeCode()).isEqualTo("R-401");
    }

    @Test
    void HTTP_200이어도_resultCode가_0000이_아니면_연동_실패로_변환한다() {
        // B의 핵심: 장애 시에도 HTTP 200을 주고 본문 resultCode로 실패를 알린다.
        server.enqueue(jsonResponse(200,
                "{ \"resultCode\": \"E503\", \"resultMessage\": \"TEMPORARILY_UNAVAILABLE\", \"data\": null }"));

        assertThatThrownBy(() -> adapter.search(criteria, List.of("B77120")).block())
                .isInstanceOf(SupplierIntegrationException.class);
    }

    @Test
    void 숙소_목록을_표준_카탈로그로_정규화한다() {
        server.enqueue(jsonResponse(200, B_PROPERTIES));

        SupplierCatalog catalog = adapter.fetchCatalog().block();

        assertThat(catalog.stays()).hasSize(1);
        SupplierCatalog.Stay riverside = catalog.stays().get(0);
        assertThat(riverside.supplierStayCode()).isEqualTo("B77120");
        assertThat(riverside.roomTypes()).hasSize(1);
        assertThat(riverside.roomTypes().get(0).supplierRoomTypeCode()).isEqualTo("R-401");
        assertThat(riverside.roomTypes().get(0).maxOccupancy()).isEqualTo(2);
    }

    @Test
    void 잘못된_응답_형식은_연동_실패로_변환한다() {
        server.enqueue(jsonResponse(200, "not a json"));

        assertThatThrownBy(() -> adapter.fetchCatalog().block())
                .isInstanceOf(SupplierIntegrationException.class);
    }

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
}
