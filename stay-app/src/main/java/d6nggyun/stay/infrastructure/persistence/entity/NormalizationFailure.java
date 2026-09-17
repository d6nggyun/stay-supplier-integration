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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 정규화할 수 없어 결과에서 제외된 항목의 격리(dead-letter) 레코드.
 * 버리는 대신 사유·공급사 코드·시각을 남겨 추후 분석(매핑·어댑터 개선)에 쓴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "normalization_failure")
public class NormalizationFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplierType supplier;

    @Column(name = "supplier_stay_code")
    private String supplierStayCode;

    @Column(name = "supplier_room_type_code")
    private String supplierRoomTypeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NormalizationFailureReason reason;

    /** 사람이 읽을 부가 정보(예: 숙소명). 원인 파악을 돕는 스냅샷이다. */
    @Column(name = "detail")
    private String detail;

    @CreationTimestamp
    @Column(name = "occurred_at", updatable = false)
    private Instant occurredAt;

    private NormalizationFailure(SupplierType supplier, String supplierStayCode, String supplierRoomTypeCode,
                                 NormalizationFailureReason reason, String detail) {
        this.supplier = supplier;
        this.supplierStayCode = supplierStayCode;
        this.supplierRoomTypeCode = supplierRoomTypeCode;
        this.reason = reason;
        this.detail = detail;
    }

    public static NormalizationFailure of(SupplierType supplier, String supplierStayCode, String supplierRoomTypeCode,
                                          NormalizationFailureReason reason, String detail) {
        return new NormalizationFailure(supplier, supplierStayCode, supplierRoomTypeCode, reason, detail);
    }
}
