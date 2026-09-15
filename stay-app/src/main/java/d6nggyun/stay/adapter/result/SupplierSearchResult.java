package d6nggyun.stay.adapter.result;

import d6nggyun.stay.domain.SupplierType;

import java.util.List;

/**
 * 한 공급사의 재고·요금 조회(②)를 정규화한 결과. 성공한 offer 목록을 담는다.
 * 실패는 SupplierIntegrationException으로 표현하며, 성공·실패를 감싸는 공급사별 상태는 오케스트레이터가 만든다.
 */
public record SupplierSearchResult(SupplierType supplier, List<SupplierOffer> offers) {
}
