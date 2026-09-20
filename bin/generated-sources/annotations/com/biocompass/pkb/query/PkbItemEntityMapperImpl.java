package com.biocompass.pkb.query;

import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import java.time.OffsetDateTime;
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
public class PkbItemEntityMapperImpl implements PkbItemEntityMapper {

    @Override
    public PkbItemRecord toRecord(PkbItemEntity entity) {
        if ( entity == null ) {
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

        itemId = entity.getPkbItemId();
        userId = entity.getUserId();
        entityType = entity.getEntityType();
        subtype = entity.getSubtype();
        status = entity.getStatus();
        Map<String, Object> map = entity.getPayload();
        if ( map != null ) {
            payload = new LinkedHashMap<String, Object>( map );
        }
        sourceType = entity.getSourceType();
        sourceId = entity.getSourceId();
        observedAt = map( entity.getObservedAt() );
        ingestedAt = map( entity.getIngestedAt() );
        validFrom = map( entity.getValidFrom() );
        validUntil = map( entity.getValidUntil() );
        language = entity.getLanguage();
        consentScope = map( entity.getConsentScope() );
        privacyScope = map( entity.getPrivacyScope() );
        verificationStatus = entity.getVerificationStatus();
        supersedes = entity.getSupersedes();
        supersededBy = entity.getSupersededBy();
        createdAt = map( entity.getCreatedAt() );
        updatedAt = map( entity.getUpdatedAt() );

        PkbItemRecord pkbItemRecord = new PkbItemRecord( itemId, userId, entityType, subtype, status, payload, sourceType, sourceId, observedAt, ingestedAt, validFrom, validUntil, language, consentScope, privacyScope, verificationStatus, supersedes, supersededBy, createdAt, updatedAt );

        return pkbItemRecord;
    }
}
