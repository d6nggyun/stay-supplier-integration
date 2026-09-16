package d6nggyun.stay.global.exception;

import d6nggyun.stay.domain.SupplierType;

/**
 * 공급사 연동 실패를 나타내는 예외. 공급사마다 실패 표현이 달라도(A: HTTP 4xx/5xx, B: 본문 resultCode)
 * 어댑터가 이 타입으로 변환해, 상위 계층은 "어느 공급사가 어떤 종류로 실패했다"는 사실만 다룬다.
 */
public class SupplierIntegrationException extends StayException {

    private final SupplierType supplier;
    private final SupplierFailureKind kind;

    public SupplierIntegrationException(SupplierType supplier, SupplierFailureKind kind, String message) {
        super(message);
        this.supplier = supplier;
        this.kind = kind;
    }

    public SupplierIntegrationException(SupplierType supplier, SupplierFailureKind kind, String message, Throwable cause) {
        super(message, cause);
        this.supplier = supplier;
        this.kind = kind;
    }

    public SupplierType getSupplier() {
        return supplier;
    }

    public SupplierFailureKind getKind() {
        return kind;
    }
}
