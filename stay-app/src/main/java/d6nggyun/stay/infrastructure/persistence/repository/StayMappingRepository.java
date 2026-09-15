package d6nggyun.stay.infrastructure.persistence.repository;

import d6nggyun.stay.domain.SupplierType;
import d6nggyun.stay.infrastructure.persistence.entity.StayMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StayMappingRepository extends JpaRepository<StayMapping, Long> {

    Optional<StayMapping> findBySupplierAndSupplierStayCode(SupplierType supplier, String supplierStayCode);

    /** 검색 대상은 활성 매핑만 조회한다. */
    List<StayMapping> findByActiveTrue();
}
