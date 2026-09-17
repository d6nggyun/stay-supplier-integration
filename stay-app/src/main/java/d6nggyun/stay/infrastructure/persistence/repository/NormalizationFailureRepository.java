package d6nggyun.stay.infrastructure.persistence.repository;

import d6nggyun.stay.infrastructure.persistence.entity.NormalizationFailure;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizationFailureRepository extends JpaRepository<NormalizationFailure, Long> {
}
