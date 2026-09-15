package d6nggyun.stay.adapter;

import d6nggyun.stay.adapter.result.SupplierCatalog;
import d6nggyun.stay.adapter.result.SupplierSearchResult;
import d6nggyun.stay.domain.SearchCriteria;
import d6nggyun.stay.domain.SupplierType;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 공급사별 어댑터의 공통 인터페이스. 공급사 API 형식과 실패 표현은 구현 내부에만 두고 도메인으로 새지 않게 한다.
 * 신규 공급사는 이 인터페이스를 구현하는 것만으로 추가되며 도메인·검색 API는 수정하지 않는다.
 */
public interface SupplierAdapter {

    SupplierType supplier();

    /** 숙소 목록(①)을 조회해 표준 카탈로그로 정규화한다. */
    Mono<SupplierCatalog> fetchCatalog();

    /** 숙소 코드 목록으로 재고·요금(②)을 조회해 표준 offer로 정규화한다. */
    Mono<SupplierSearchResult> search(SearchCriteria criteria, List<String> supplierStayCodes);
}
