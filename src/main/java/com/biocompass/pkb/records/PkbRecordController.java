package com.biocompass.pkb.records;

import com.biocompass.pkb.records.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/internal/items") @RequiredArgsConstructor @Validated
public class PkbRecordController {
    private final PkbSourceRecordStore store;
    private final PkbRecordAuthorization authorization;

    @PostMapping
    public WriteResponse create(@AuthenticationPrincipal PkbRecordAuthority authority, @RequestBody @Valid PkbRecordRequests.Create request) {
        var decision = authorization.authorize(authority, "create", request.entityType(), request.subtype(), request.sourceType(),
                request.privacyScope(), request.consentScope(), request.itemId());
        var result = store.create(new PkbSourceRecordCreate(request.itemId(), authority.userId(), authority.producer(),
                request.sourceRecordId(), request.idempotencyKey(), request.fingerprint(), request.entityType(), request.subtype(),
                request.quantity().value(), request.quantity().unit(), request.observedAt(), request.recordedAt(), request.sourceType(),
                request.privacyScope(), request.consentScope(), decision));
        if ("replayed".equals(result.outcome())) authorizeRow(authority, "create", result.item());
        return new WriteResponse(result.outcome(), Item.from(result.item()));
    }
    @GetMapping("/{itemId}") @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Item lookup(@AuthenticationPrincipal PkbRecordAuthority authority, @PathVariable UUID itemId) {
        var item = store.find(authority.userId(), authority.producer(), itemId).orElseThrow(() -> new PkbRecordFailure(404,"not_found"));
        authorizeRow(authority,"read",item);
        return Item.from(item);
    }
    @DeleteMapping("/{itemId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public void delete(@AuthenticationPrincipal PkbRecordAuthority authority, @PathVariable UUID itemId) {
        var item = store.findIncludingDeleted(authority.userId(),authority.producer(),itemId);
        if (item.isEmpty()) {
            authorization.authorize(authority,"delete","","",null,List.of(),List.of(),itemId);
            return;
        }
        authorizeRow(authority,"delete",item.get());
        store.delete(authority.userId(),authority.producer(),itemId);
    }
    @GetMapping @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Page history(@AuthenticationPrincipal PkbRecordAuthority authority,
            @RequestParam("entity_type") @Pattern(regexp="[a-z][a-z0-9_]{0,63}") String entityType,
            @RequestParam @Pattern(regexp="[a-z][a-z0-9_]{0,63}") String subtype,
            @RequestParam("observed_from") Instant from, @RequestParam("observed_before") Instant before,
            @RequestParam(defaultValue="500") @Min(1) @Max(500) int limit,
            @RequestParam(required=false) @Size(max=512) String cursor) {
        if (!from.isBefore(before)) throw new PkbRecordFailure(422,"invalid_request");
        authorization.authorize(authority,"read",entityType,subtype,null,List.of(),List.of(),null);
        var rows = store.history(authority.userId(),authority.producer(),entityType,subtype,from,before,limit+1,decodeCursor(cursor));
        var page = rows.subList(0,Math.min(limit,rows.size()));
        page.forEach(row -> authorizeRow(authority,"read",row));
        return new Page(page.stream().map(Item::from).toList(),rows.size()>limit ? encodeCursor(page.getLast()) : null);
    }
    @PostMapping("/quantity-totals") @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public TotalsResponse totals(@AuthenticationPrincipal PkbRecordAuthority authority, @RequestBody @Valid PkbRecordRequests.Totals request) {
        var windows = request.windows().stream().map(w -> new PkbQuantityWindow(w.from(),w.before())).toList();
        authorization.authorize(authority,"aggregate",request.entityType(),request.subtype(),null,List.of(),List.of(),null);
        if (windows.isEmpty()) return new TotalsResponse(List.of());
        authorizeGroups(authority,request.entityType(),request.subtype(),store.governanceGroupsForWindows(
                authority.userId(),authority.producer(),request.entityType(),request.subtype(),windows));
        return new TotalsResponse(store.totals(authority.userId(),authority.producer(),request.entityType(),request.subtype(),
                request.unit(),windows).stream().map(BigDecimal::toPlainString).toList());
    }
    @GetMapping("/recent-quantities") @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ValuesResponse recent(@AuthenticationPrincipal PkbRecordAuthority authority,
            @RequestParam("entity_type") @Pattern(regexp="[a-z][a-z0-9_]{0,63}") String entityType,
            @RequestParam @Pattern(regexp="[a-z][a-z0-9_]{0,63}") String subtype,
            @RequestParam @Pattern(regexp="mL") String unit, @RequestParam(defaultValue="3") @Min(1) @Max(3) int limit) {
        authorization.authorize(authority,"aggregate",entityType,subtype,null,List.of(),List.of(),null);
        authorizeGroups(authority,entityType,subtype,
                store.governanceGroups(authority.userId(),authority.producer(),entityType,subtype,null,null));
        return new ValuesResponse(store.recentQuantities(authority.userId(),authority.producer(),entityType,subtype,unit,limit)
                .stream().map(BigDecimal::toPlainString).toList());
    }
    private void authorizeRow(PkbRecordAuthority authority,String action,PkbSourceRecord item) {
        authorization.authorize(authority,action,item.entityType(),item.subtype(),item.sourceType(),item.privacyScope(),item.consentScope(),item.itemId());
    }
    private void authorizeGroups(PkbRecordAuthority authority,String entity,String subtype,List<PkbSourceRecordGovernance> groups) {
        if(groups.size()>100) throw new PkbRecordFailure(503,"unavailable");
        groups.forEach(g -> authorization.authorize(authority,"aggregate",entity,subtype,g.sourceType(),g.privacyScope(),g.consentScope(),null));
    }
    static PkbSourceRecordCursor decodeCursor(String cursor) {
        if(cursor==null) return null;
        try {
            var p=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split("\\|",-1);
            if(p.length!=3) throw new IllegalArgumentException();
            return new PkbSourceRecordCursor(Instant.parse(p[0]),Instant.parse(p[1]),UUID.fromString(p[2]));
        } catch(RuntimeException exception) { throw new PkbRecordFailure(422,"invalid_request"); }
    }
    static String encodeCursor(PkbSourceRecord item) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((item.observedAt()+"|"+item.recordedAt()+"|"+item.itemId()).getBytes(StandardCharsets.UTF_8));
    }
    public record Quantity(String value,String unit) {}
    public record Item(@JsonProperty("item_id") UUID itemId,@JsonProperty("user_id") UUID userId,
            @JsonProperty("source_system") String sourceSystem,@JsonProperty("source_record_id") String sourceRecordId,
            @JsonProperty("idempotency_key") UUID idempotencyKey,String fingerprint,@JsonProperty("entity_type") String entityType,
            String subtype,Quantity quantity,@JsonProperty("observed_at") Instant observedAt,@JsonProperty("recorded_at") Instant recordedAt,
            @JsonProperty("source_type") String sourceType) {
        static Item from(PkbSourceRecord r) { return new Item(r.itemId(),r.userId(),r.sourceSystem(),r.sourceRecordId(),r.idempotencyKey(),
                r.fingerprint(),r.entityType(),r.subtype(),new Quantity(r.value().toPlainString(),r.unit()),r.observedAt(),r.recordedAt(),r.sourceType()); }
    }
    public record WriteResponse(String outcome,Item item) {}
    public record Page(List<Item> items,@JsonProperty("next_cursor") String nextCursor) {}
    public record TotalsResponse(List<String> totals) {}
    public record ValuesResponse(List<String> values) {}
}
