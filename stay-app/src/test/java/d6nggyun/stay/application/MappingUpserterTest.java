package d6nggyun.stay.application;

import d6nggyun.stay.adapter.result.SupplierCatalog;
import d6nggyun.stay.infrastructure.persistence.repository.RoomTypeMappingRepository;
import d6nggyun.stay.infrastructure.persistence.repository.StayMappingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static d6nggyun.stay.domain.SupplierType.SUPPLIER_A;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(MappingUpserter.class)
class MappingUpserterTest {

    @Autowired
    private MappingUpserter upserter;
    @Autowired
    private StayMappingRepository stayMappingRepository;
    @Autowired
    private RoomTypeMappingRepository roomTypeMappingRepository;

    private SupplierCatalog catalogA() {
        return new SupplierCatalog(SUPPLIER_A, List.of(
                new SupplierCatalog.Stay("A-10023", "Riverside Hotel Seoul", List.of(
                        new SupplierCatalog.RoomType("DLX-TWN", "Deluxe Twin", 2)))));
    }

    @Test
    void 카탈로그를_매핑으로_저장한다() {
        upserter.upsert(catalogA());

        assertThat(stayMappingRepository.findAll()).hasSize(1);
        assertThat(roomTypeMappingRepository.findAll()).hasSize(1);
        assertThat(stayMappingRepository.findBySupplierAndSupplierStayCode(SUPPLIER_A, "A-10023")).isPresent();
    }

    @Test
    void 같은_코드_재동기화_시_내부_식별자를_재사용하고_중복_생성하지_않는다() {
        upserter.upsert(catalogA());
        Long firstStayId = stayMappingRepository
                .findBySupplierAndSupplierStayCode(SUPPLIER_A, "A-10023").orElseThrow().getInternalStayId();

        upserter.upsert(catalogA());  // 재동기화

        Long secondStayId = stayMappingRepository
                .findBySupplierAndSupplierStayCode(SUPPLIER_A, "A-10023").orElseThrow().getInternalStayId();
        assertThat(secondStayId).isEqualTo(firstStayId);
        assertThat(stayMappingRepository.findAll()).hasSize(1);
        assertThat(roomTypeMappingRepository.findAll()).hasSize(1);
    }
}
