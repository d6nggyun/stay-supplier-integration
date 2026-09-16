package d6nggyun.stay.api;

import d6nggyun.stay.api.dto.SearchResponse;
import d6nggyun.stay.application.search.SearchResult;
import d6nggyun.stay.application.search.StaySearchService;
import d6nggyun.stay.domain.SearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 통합 검색 API. 쿼리 파라미터를 검색 조건으로 바꿔 오케스트레이터에 넘기고, 결과·공급사 상태를 응답으로 반환한다.
 * 부분 실패는 HTTP 200 + 공급사별 상태로 표현하고, 쓸 수 있는 결과가 하나도 없으면 5xx로 응답하되 본문에 상태를 담는다.
 */
@Tag(name = "검색", description = "통합 숙박 상품 검색")
@RestController
@RequiredArgsConstructor
public class StaySearchController {

    private final StaySearchService staySearchService;

    @Operation(summary = "통합 검색",
            description = "공급사를 병렬 조회해 표준 모델로 병합합니다. 부분 실패는 200 + 공급사별 상태, "
                    + "전부 실패 502 / 전부 SKIPPED 503으로 응답합니다.")
    @GetMapping("/api/v1/stays/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam int adults,
            @RequestParam(defaultValue = "0") int children) {
        // 잘못된 날짜·인원은 SearchCriteria 검증에서 InvalidRequestException → 전역 핸들러가 400으로 변환한다.
        SearchCriteria criteria = new SearchCriteria(checkIn, checkOut, adults, children);
        SearchResult result = staySearchService.search(criteria);
        return ResponseEntity.status(decideStatus(result)).body(SearchResponse.from(result));
    }

    /**
     * 응답 코드 결정. 부분 실패 포함 성공이면 200,
     * 호출 대상이 하나도 없으면(전부 SKIPPED) 503, 호출했으나 전부 실패면 502.
     * 5xx여도 본문에는 공급사별 상태를 담아 원인을 전달한다.
     */
    private HttpStatus decideStatus(SearchResult result) {
        if (result.anySucceeded()) {
            return HttpStatus.OK;
        }
        return result.allSkipped() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
    }
}
