package com.biocompass.pkb.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/pkb/items")
@RequiredArgsConstructor
public class PkbItemQueryController {

    private final PkbItemQueryService queryService;
    private final PkbItemResponseMapper responseMapper;

    @GetMapping("/{itemId}")
    public PkbItemResponse getItem(@UserId UUID userId, @PathVariable UUID itemId) {
        PkbItemRecord item = queryService.getItem(userId, itemId);
        return responseMapper.toResponse(item);
    }

    @GetMapping
    public List<PkbItemResponse> searchItems(
            @UserId UUID userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String subtype,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime observedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime observedUntil,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime validAt,
            @RequestParam(required = false) String privacyScope,
            @RequestParam(required = false, name = "text") String textQuery,
            @RequestParam(defaultValue = "50") @Min(1) @Max(PkbItemSearchCriteria.MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        PkbItemSearchCriteria criteria = new PkbItemSearchCriteria(
                userId,
                entityType,
                subtype,
                status,
                observedFrom,
                observedUntil,
                validAt,
                privacyScope,
                textQuery,
                limit,
                offset);

        return responseMapper.toResponses(queryService.search(criteria));
    }
}
