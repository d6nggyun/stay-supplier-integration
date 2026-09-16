package d6nggyun.stay.application.search;

import d6nggyun.stay.domain.StayOffer;
import d6nggyun.stay.domain.SupplierType;

import java.util.List;

/**
 * 한 공급사의 검색 처리 결과. 성공 시 정규화된 offer 목록을, 실패·스킵 시 그 사유(상태·에러)를 담는다.
 * offer를 공급사별로 묶어 두면 최종 응답에서 결과 목록으로 펼치면서도 공급사별 성공·실패를 함께 보고할 수 있다.
 */
public record SupplierResult(
        SupplierType supplier,
        SupplierSearchStatus status,
        long latencyMs,
        List<StayOffer> offers,
        String errorCode,
        String errorMessage) {

    /** 정상 응답. 결과 건수는 offers 크기로 표현한다. */
    public static SupplierResult success(SupplierType supplier, long latencyMs, List<StayOffer> offers) {
        return new SupplierResult(supplier, SupplierSearchStatus.SUCCESS, latencyMs, offers, null, null);
    }

    /** 호출 없이 스킵(매핑 없음). */
    public static SupplierResult skipped(SupplierType supplier) {
        return new SupplierResult(supplier, SupplierSearchStatus.SKIPPED, 0L, List.of(), null, null);
    }

    /** 연동 실패. 결과가 없으므로 offers는 비운다. */
    public static SupplierResult failure(SupplierType supplier, SupplierSearchStatus status,
                                         long latencyMs, String errorCode, String errorMessage) {
        return new SupplierResult(supplier, status, latencyMs, List.of(), errorCode, errorMessage);
    }

    public int resultCount() {
        return offers.size();
    }
}
