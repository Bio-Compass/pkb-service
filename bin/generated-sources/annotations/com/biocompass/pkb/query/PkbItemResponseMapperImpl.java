package com.biocompass.pkb.query;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-23T21:46:29+0200",
    comments = "version: 1.6.3, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class PkbItemResponseMapperImpl implements PkbItemResponseMapper {

    @Override
    public PkbItemResponse toResponse(PkbItemRecord item) {
        if ( item == null ) {
            return null;
        }

        UUID itemId = null;
        UUID userId = null;
        String entityType = null;
        String subtype = null;
        String status = null;
        Map<String, Object> payload = null;
        String sourceType = null;
        String sourceId = null;
        OffsetDateTime observedAt = null;
        OffsetDateTime ingestedAt = null;
        OffsetDateTime validFrom = null;
        OffsetDateTime validUntil = null;
        String language = null;
        List<String> consentScope = null;
        List<String> privacyScope = null;
        String verificationStatus = null;
        UUID supersedes = null;
        UUID supersededBy = null;
        OffsetDateTime createdAt = null;
        OffsetDateTime updatedAt = null;

        itemId = item.itemId();
        userId = item.userId();
        entityType = item.entityType();
        subtype = item.subtype();
        status = item.status();
        Map<String, Object> map = item.payload();
        if ( map != null ) {
            payload = new LinkedHashMap<String, Object>( map );
        }
        sourceType = item.sourceType();
        sourceId = item.sourceId();
        observedAt = item.observedAt();
        ingestedAt = item.ingestedAt();
        validFrom = item.validFrom();
        validUntil = item.validUntil();
        language = item.language();
        List<String> list = item.consentScope();
        if ( list != null ) {
            consentScope = new ArrayList<String>( list );
        }
        List<String> list1 = item.privacyScope();
        if ( list1 != null ) {
            privacyScope = new ArrayList<String>( list1 );
        }
        verificationStatus = item.verificationStatus();
        supersedes = item.supersedes();
        supersededBy = item.supersededBy();
        createdAt = item.createdAt();
        updatedAt = item.updatedAt();

        PkbItemResponse pkbItemResponse = new PkbItemResponse( itemId, userId, entityType, subtype, status, payload, sourceType, sourceId, observedAt, ingestedAt, validFrom, validUntil, language, consentScope, privacyScope, verificationStatus, supersedes, supersededBy, createdAt, updatedAt );

        return pkbItemResponse;
    }

    @Override
    public List<PkbItemResponse> toResponses(List<PkbItemRecord> items) {
        if ( items == null ) {
            return null;
        }

        List<PkbItemResponse> list = new ArrayList<PkbItemResponse>( items.size() );
        for ( PkbItemRecord pkbItemRecord : items ) {
            list.add( toResponse( pkbItemRecord ) );
        }

        return list;
    }
}
