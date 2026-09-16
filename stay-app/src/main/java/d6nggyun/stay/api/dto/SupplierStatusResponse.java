package d6nggyun.stay.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import d6nggyun.stay.application.search.SupplierResult;
import d6nggyun.stay.application.search.SupplierSearchStatus;

/**
 * 공급사별 처리 상태. 어느 공급사가 어떤 이유로 결과에 있고/없는지를 응답만으로 드러낸다.
 * 성공(부분 성공)일 때만 resultCount를, 실패일 때만 errorCode·errorMessage를 담는다(null 필드는 직렬화에서 생략).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupplierStatusResponse(
        String supplier,
        String status,
        long latencyMs,
        Integer resultCount,
        String errorCode,
        String errorMessage) {

    public static SupplierStatusResponse from(SupplierResult result) {
        boolean succeeded = result.status() == SupplierSearchStatus.SUCCESS
                || result.status() == SupplierSearchStatus.PARTIAL_SUCCESS;
        Integer resultCount = succeeded ? result.resultCount() : null;
        return new SupplierStatusResponse(
                result.supplier().name(),
                result.status().name(),
                result.latencyMs(),
                resultCount,
                result.errorCode(),
                result.errorMessage());
    }
}
