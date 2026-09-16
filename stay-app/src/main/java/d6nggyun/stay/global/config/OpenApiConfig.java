package d6nggyun.stay.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI(Swagger UI) 문서의 기본 메타데이터. 스키마·엔드포인트는 컨트롤러·DTO에서 자동 생성되며,
 * 여기서는 제목·설명·버전만 지정한다. UI는 /swagger-ui.html, 스펙은 /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("통합 숙박 상품 검색 API")
                .description("두 공급사의 API를 하나의 표준 모델로 흡수해 제공하는 통합 검색 API입니다. "
                        + "부분 실패는 HTTP 200 + 공급사별 상태로, 쓸 수 있는 결과가 없으면 5xx로 응답합니다.")
                .version("v1"));
    }
}
