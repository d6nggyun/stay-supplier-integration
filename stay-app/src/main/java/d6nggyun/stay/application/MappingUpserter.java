package d6nggyun.stay.application;

import d6nggyun.stay.adapter.result.SupplierCatalog;
import d6nggyun.stay.domain.SupplierType;
import d6nggyun.stay.infrastructure.persistence.entity.RoomTypeMapping;
import d6nggyun.stay.infrastructure.persistence.entity.StayMapping;
import d6nggyun.stay.infrastructure.persistence.repository.RoomTypeMappingRepository;
import d6nggyun.stay.infrastructure.persistence.repository.StayMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공급사 카탈로그를 매핑으로 upsert한다. 같은 공급사 코드가 다시 조회되면 기존 내부 식별자를 재사용하고,
 * 없으면 새 내부 식별자를 발급한다. 한 공급사 단위로 하나의 트랜잭션이다.
 */
@Component
@RequiredArgsConstructor
public class MappingUpserter {

    private final StayMappingRepository stayMappingRepository;
    private final RoomTypeMappingRepository roomTypeMappingRepository;

    @Transactional
    public void upsert(SupplierCatalog catalog) {
        SupplierType supplier = catalog.supplier();
        for (SupplierCatalog.Stay stay : catalog.stays()) {
            upsertStay(supplier, stay);
            for (SupplierCatalog.RoomType roomType : stay.roomTypes()) {
                upsertRoomType(supplier, stay.supplierStayCode(), roomType);
            }
        }
    }

    private void upsertStay(SupplierType supplier, SupplierCatalog.Stay stay) {
        stayMappingRepository.findBySupplierAndSupplierStayCode(supplier, stay.supplierStayCode())
                .ifPresentOrElse(
                        existing -> existing.refreshFrom(stay.stayName()),  // 기존 내부 식별자 유지, 최신 상태로 갱신
                        () -> stayMappingRepository.save(
                                StayMapping.of(supplier, stay.supplierStayCode(), stay.stayName())));
    }

    private void upsertRoomType(SupplierType supplier, String supplierStayCode, SupplierCatalog.RoomType roomType) {
        roomTypeMappingRepository
                .findBySupplierAndSupplierStayCodeAndSupplierRoomTypeCode(
                        supplier, supplierStayCode, roomType.supplierRoomTypeCode())
                .ifPresentOrElse(
                        existing -> existing.refreshFrom(roomType.roomTypeName(), roomType.maxOccupancy()),
                        () -> roomTypeMappingRepository.save(RoomTypeMapping.builder()
                                .supplier(supplier)
                                .supplierStayCode(supplierStayCode)
                                .supplierRoomTypeCode(roomType.supplierRoomTypeCode())
                                .roomTypeName(roomType.roomTypeName())
                                .maxOccupancy(roomType.maxOccupancy())
                                .build()));
    }
}
