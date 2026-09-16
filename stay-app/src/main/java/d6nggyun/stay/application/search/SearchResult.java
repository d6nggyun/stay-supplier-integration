package d6nggyun.stay.application.search;

import d6nggyun.stay.domain.StayOffer;

import java.util.List;

/**
 * 통합 검색의 최종 결과. 공급사별 처리 결과(suppliers)를 담고, 결과 목록(results)은 이들의 offer를 펼쳐서 만든다.
 * 결과와 공급사별 상태를 함께 두어, 부분 실패도 "결과 + 어느 공급사가 왜 실패했는지"로 함께 표현한다.
 */
public record SearchResult(List<SupplierResult> suppliers) {

    /** 공급사별 offer를 하나의 결과 목록으로 펼친다. */
    public List<StayOffer> results() {
        return suppliers.stream()
                .flatMap(supplier -> supplier.offers().stream())
                .toList();
    }

    /** 성공(또는 부분 성공)한 공급사가 하나라도 있는지. 결과가 비어 있어도 성공은 성공이다(전체 실패 판정에 쓴다). */
    public boolean anySucceeded() {
        return suppliers.stream().anyMatch(supplier ->
                supplier.status() == SupplierSearchStatus.SUCCESS
                        || supplier.status() == SupplierSearchStatus.PARTIAL_SUCCESS);
    }

    /** 호출 대상이 하나도 없었는지(모두 SKIPPED, 또는 공급사 자체가 없음). 503 판정에 쓴다. */
    public boolean allSkipped() {
        return suppliers.stream().allMatch(supplier -> supplier.status() == SupplierSearchStatus.SKIPPED);
    }
}
