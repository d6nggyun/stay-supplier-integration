package d6nggyun.stay.infrastructure.persistence.repository;

import d6nggyun.stay.infrastructure.persistence.entity.StayMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static d6nggyun.stay.domain.SupplierType.SUPPLIER_A;
import static d6nggyun.stay.domain.SupplierType.SUPPLIER_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class StayMappingRepositoryTest {

    @Autowired
    private StayMappingRepository repository;

    @Test
    void 저장하면_내부_숙소_식별자가_발급된다() {
        StayMapping saved = repository.save(StayMapping.of(SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"));

        assertThat(saved.getInternalStayId()).isNotNull();
    }

    @Test
    void 공급사와_코드로_매핑을_조회한다() {
        repository.save(StayMapping.of(SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"));

        Optional<StayMapping> found = repository.findBySupplierAndSupplierStayCode(SUPPLIER_A, "A-10023");

        assertThat(found).isPresent();
        assertThat(found.get().getStayName()).isEqualTo("Riverside Hotel Seoul");
    }

    @Test
    void 같은_공급사와_코드는_중복_저장되지_않는다() {
        repository.saveAndFlush(StayMapping.of(SUPPLIER_A, "A-10023", "Riverside Hotel Seoul"));

        assertThatThrownBy(() ->
                repository.saveAndFlush(StayMapping.of(SUPPLIER_A, "A-10023", "다른 이름")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 공급사가_다르면_같은_코드라도_별도_내부_식별자를_갖는다() {
        StayMapping a = repository.save(StayMapping.of(SUPPLIER_A, "SAME-CODE", "A 숙소"));
        StayMapping b = repository.save(StayMapping.of(SUPPLIER_B, "SAME-CODE", "B 숙소"));

        assertThat(a.getInternalStayId()).isNotEqualTo(b.getInternalStayId());
    }
}
