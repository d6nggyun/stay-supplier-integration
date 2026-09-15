package d6nggyun.stay.infrastructure.persistence.entity;

import d6nggyun.stay.domain.SupplierType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 숙소 단위의 공급사 코드 ↔ 내부 식별자 매핑.
 * 논리적 키는 (supplier, supplierStayCode)이며, 같은 공급사 코드는 항상 같은 내부 식별자로 돌아온다.
 * 재고·요금은 저장하지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "stay_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stay_mapping_supplier_code",
                columnNames = {"supplier", "supplier_stay_code"}))
public class StayMapping {

    /**
     * 내부 숙소 식별자. 공급사 코드가 아닌 자사 식별자이며 DB 자동 증가(IDENTITY)로 발급한다.
     * 현재는 병합(확장 범위)이 없어 매핑 행과 1:1이므로 이 값을 PK로 둔다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "internal_stay_id")
    private Long internalStayId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplierType supplier;

    @Column(name = "supplier_stay_code", nullable = false)
    private String supplierStayCode;

    @Column(name = "stay_name")
    private String stayName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    private StayMapping(SupplierType supplier, String supplierStayCode, String stayName) {
        this.supplier = supplier;
        this.supplierStayCode = supplierStayCode;
        this.stayName = stayName;
        this.active = true;
        this.lastSeenAt = Instant.now();
    }

    public static StayMapping of(SupplierType supplier, String supplierStayCode, String stayName) {
        return new StayMapping(supplier, supplierStayCode, stayName);
    }

    /** 동기화에서 같은 코드가 다시 조회됐을 때, 기존 내부 식별자를 유지한 채 최신 상태로 갱신한다. */
    public void refreshFrom(String stayName) {
        this.stayName = stayName;
        this.active = true;
        this.lastSeenAt = Instant.now();
    }
}
