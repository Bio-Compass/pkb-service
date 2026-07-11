package com.biocompass.pkb.query;

import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PkbItemResponseMapper {

    PkbItemResponse toResponse(PkbItemRecord item);

    List<PkbItemResponse> toResponses(List<PkbItemRecord> items);
}
