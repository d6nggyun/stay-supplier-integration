package d6nggyun.stay.infrastructure.persistence.repository;

import d6nggyun.stay.domain.SupplierType;
import d6nggyun.stay.infrastructure.persistence.entity.RoomTypeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomTypeMappingRepository extends JpaRepository<RoomTypeMapping, Long> {

    Optional<RoomTypeMapping> findBySupplierAndSupplierStayCodeAndSupplierRoomTypeCode(
            SupplierType supplier, String supplierStayCode, String supplierRoomTypeCode);

    /** 검색 시 공급사 코드를 내부 식별자로 치환하기 위해 활성 매핑을 일괄 조회한다. */
    List<RoomTypeMapping> findByActiveTrue();
}
