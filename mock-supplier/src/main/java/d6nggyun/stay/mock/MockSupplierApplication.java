package d6nggyun.stay.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Supplier A·B를 흉내 내는 Mock 서버. 본 애플리케이션과 별도 모듈·별도 포트(9090)로 실행한다.
 * 목적은 정교한 상품 데이터가 아니라 연동 흐름의 정상·장애·무응답 재현이므로 복잡도는 낮게 유지한다.
 */
@SpringBootApplication
public class MockSupplierApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockSupplierApplication.class, args);
    }
}
