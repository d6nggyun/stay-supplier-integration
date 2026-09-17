# 설계 문서

확정된 설계의 단일 출처입니다. 카테고리별로 문서를 나누고, 각 결정에는 근거를 함께 남깁니다.

## 문서 목록

| 문서 | 내용 |
| --- | --- |
| [architecture.md](architecture.md) | 아키텍처 · 계층 구조 · 실행 모델 · 핵심 흐름 |
| [domain-model.md](domain-model.md) | 통합 숙박 상품 모델 (검색 조건 · 요금 · 재고 · 조식 · 메타데이터 출처) |
| [mapping.md](mapping.md) | 공급사 코드 ↔ 내부 식별자 매핑 · 동기화 |
| [supplier-adapter.md](supplier-adapter.md) | 공급사 어댑터 · 실패 판정 통일 · 정규화 실패 · 신규 공급사 |
| [search-flow.md](search-flow.md) | 통합 검색 흐름 · 병렬 호출 · 청크 분할 · 타임아웃 |
| [failure-handling.md](failure-handling.md) | 부분 실패 처리 · 공급사별 상태 객체 |
| [resilience.md](resilience.md) | 재시도 · 서킷 브레이커 |
| [observability.md](observability.md) | 연동 지표 · 모니터링 |
| [mock-supplier.md](mock-supplier.md) | Mock 공급사 (정상 · 장애 · 무응답 재현) |
| [testing.md](testing.md) | 테스트 전략 |
| [api.md](api.md) | 통합 검색 API 명세 |

## 주제별 안내

- **아키텍처 문서**: [architecture.md](architecture.md)
- **통합 모델 설계**: [domain-model.md](domain-model.md)와 [mapping.md](mapping.md)
- **API 명세**: [api.md](api.md)

빌드·실행 방법과 설계 의사결정 요약은 저장소 루트 [README.md](../README.md)에, 일자별 의사결정 과정은 [JOURNAL.md](../JOURNAL.md)에 있습니다.
