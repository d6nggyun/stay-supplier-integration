package d6nggyun.stay.infrastructure.persistence.repository;

import d6nggyun.stay.domain.SupplierType;
import d6nggyun.stay.infrastructure.persistence.entity.StayMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StayMappingRepository extends JpaRepository<StayMapping, Long> {

    Optional<StayMapping> findBySupplierAndSupplierStayCode(SupplierType supplier, String supplierStayCode);
}
