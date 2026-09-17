package d6nggyun.stay.global.exception;

import d6nggyun.stay.domain.SupplierType;

/**
 * 공급사 연동 실패를 나타내는 예외. 공급사마다 실패 표현이 달라도(A: HTTP 4xx/5xx, B: 본문 resultCode)
 * 어댑터가 이 타입으로 변환해, 상위 계층은 "어느 공급사가 어떤 종류로 실패했다"는 사실만 다룬다.
 *
 * retryable은 재시도 대상인지를 나타낸다(전이성 실패=true). 사용자 노출 상태와는 무관한 내부 판정용이며,
 * 예: 전송/HTTP 계층 실패는 상태로는 HTTP_ERROR로 묶되 5xx·연결 실패는 retryable, 4xx는 비재시도로 둔다.
 */
public class SupplierIntegrationException extends StayException {

    private final SupplierType supplier;
    private final SupplierFailureKind kind;
    private final boolean retryable;

    public SupplierIntegrationException(SupplierType supplier, SupplierFailureKind kind, boolean retryable,
                                        String message) {
        super(message);
        this.supplier = supplier;
        this.kind = kind;
        this.retryable = retryable;
    }

    public SupplierIntegrationException(SupplierType supplier, SupplierFailureKind kind, boolean retryable,
                                        String message, Throwable cause) {
        super(message, cause);
        this.supplier = supplier;
        this.kind = kind;
        this.retryable = retryable;
    }

    public SupplierType getSupplier() {
        return supplier;
    }

    public SupplierFailureKind getKind() {
        return kind;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
