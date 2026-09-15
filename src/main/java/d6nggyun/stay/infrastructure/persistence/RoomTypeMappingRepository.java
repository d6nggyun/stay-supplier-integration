package d6nggyun.stay.infrastructure.persistence;

import d6nggyun.stay.domain.SupplierType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomTypeMappingRepository extends JpaRepository<RoomTypeMapping, Long> {

    Optional<RoomTypeMapping> findBySupplierAndSupplierStayCodeAndSupplierRoomTypeCode(
            SupplierType supplier, String supplierStayCode, String supplierRoomTypeCode);
}
