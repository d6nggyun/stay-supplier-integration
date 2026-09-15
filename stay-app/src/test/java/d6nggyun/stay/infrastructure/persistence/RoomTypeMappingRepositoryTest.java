package d6nggyun.stay.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static d6nggyun.stay.domain.SupplierType.SUPPLIER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class RoomTypeMappingRepositoryTest {

    @Autowired
    private RoomTypeMappingRepository repository;

    private RoomTypeMapping deluxeTwin(String stayCode) {
        return RoomTypeMapping.builder()
                .supplier(SUPPLIER_A)
                .supplierStayCode(stayCode)
                .supplierRoomTypeCode("DLX-TWN")
                .roomTypeName("Deluxe Twin")
                .maxOccupancy(2)
                .build();
    }

    @Test
    void 저장하면_내부_객실_타입_식별자가_발급된다() {
        RoomTypeMapping saved = repository.save(deluxeTwin("A-10023"));

        assertThat(saved.getInternalRoomTypeId()).isNotNull();
    }

    @Test
    void 공급사와_숙소코드와_객실코드_3튜플로_조회한다() {
        repository.save(deluxeTwin("A-10023"));

        Optional<RoomTypeMapping> found = repository
                .findBySupplierAndSupplierStayCodeAndSupplierRoomTypeCode(SUPPLIER_A, "A-10023", "DLX-TWN");

        assertThat(found).isPresent();
        assertThat(found.get().getMaxOccupancy()).isEqualTo(2);
    }

    @Test
    void 다른_숙소에_같은_객실_코드가_있어도_충돌하지_않는다() {
        repository.save(RoomTypeMapping.builder()
                .supplier(SUPPLIER_A).supplierStayCode("A-10023").supplierRoomTypeCode("STD-DBL")
                .roomTypeName("Standard Double").maxOccupancy(2).build());
        repository.save(RoomTypeMapping.builder()
                .supplier(SUPPLIER_A).supplierStayCode("A-10044").supplierRoomTypeCode("STD-DBL")
                .roomTypeName("Standard Double").maxOccupancy(2).build());

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void 같은_3튜플은_중복_저장되지_않는다() {
        repository.saveAndFlush(deluxeTwin("A-10023"));

        assertThatThrownBy(() ->
                repository.saveAndFlush(RoomTypeMapping.builder()
                        .supplier(SUPPLIER_A).supplierStayCode("A-10023").supplierRoomTypeCode("DLX-TWN")
                        .roomTypeName("다른 이름").maxOccupancy(3).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
