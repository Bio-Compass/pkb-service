package com.biocompass.pkb.query;

import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PkbItemEntityMapper {

    @Mapping(target = "itemId", source = "pkbItemId")
    PkbItemRecord toRecord(PkbItemEntity entity);

    default OffsetDateTime map(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    default List<String> map(String[] values) {
        return values == null ? List.of() : List.copyOf(Arrays.asList(values));
    }
}
