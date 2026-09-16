package d6nggyun.stay.api.dto;

import d6nggyun.stay.application.search.SearchResult;

import java.util.List;

/**
 * 통합 검색 응답. 결과 목록(results)과 공급사별 처리 상태(suppliers)를 함께 담는다.
 * 부분 실패든 전체 실패든 같은 형태로, HTTP 코드는 "쓸 수 있는 결과 유무"를, 본문은 "원인"을 전달한다.
 */
public record SearchResponse(List<StayOfferResponse> results, List<SupplierStatusResponse> suppliers) {

    public static SearchResponse from(SearchResult result) {
        List<StayOfferResponse> results = result.results().stream()
                .map(StayOfferResponse::from)
                .toList();
        List<SupplierStatusResponse> suppliers = result.suppliers().stream()
                .map(SupplierStatusResponse::from)
                .toList();
        return new SearchResponse(results, suppliers);
    }
}
