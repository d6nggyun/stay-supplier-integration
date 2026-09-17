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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 객실 타입 단위의 공급사 코드 ↔ 내부 식별자 매핑.
 * 객실 타입 코드는 숙소 내부에서만 유일하므로 논리적 키는 (supplier, supplierStayCode, supplierRoomTypeCode)이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "room_type_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_room_type_mapping_supplier_code",
                columnNames = {"supplier", "supplier_stay_code", "supplier_room_type_code"}))
public class RoomTypeMapping {

    /**
     * 내부 객실 타입 식별자. 공급사 코드가 아닌 자사 식별자이며 DB 자동 증가(IDENTITY)로 발급한다.
     * 현재는 병합(확장 범위)이 없어 매핑 행과 1:1이므로 이 값을 PK로 둔다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "internal_room_type_id")
    private Long internalRoomTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplierType supplier;

    @Column(name = "supplier_stay_code", nullable = false)
    private String supplierStayCode;

    @Column(name = "supplier_room_type_code", nullable = false)
    private String supplierRoomTypeCode;

    @Column(name = "room_type_name")
    private String roomTypeName;

    @Column(name = "max_occupancy")
    private int maxOccupancy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Builder
    private RoomTypeMapping(SupplierType supplier, String supplierStayCode, String supplierRoomTypeCode,
                           String roomTypeName, int maxOccupancy) {
        this.supplier = supplier;
        this.supplierStayCode = supplierStayCode;
        this.supplierRoomTypeCode = supplierRoomTypeCode;
        this.roomTypeName = roomTypeName;
        this.maxOccupancy = maxOccupancy;
    }

    /** 같은 코드가 다시 조회됐을 때, 기존 내부 식별자를 유지한 채 최신 상태로 갱신한다. */
    public void refreshFrom(String roomTypeName, int maxOccupancy) {
        this.roomTypeName = roomTypeName;
        this.maxOccupancy = maxOccupancy;
    }
}
