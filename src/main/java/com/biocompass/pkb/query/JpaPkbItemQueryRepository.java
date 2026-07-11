package com.biocompass.pkb.query;

import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import com.biocompass.pkb.persistence.repository.PkbItemRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaPkbItemQueryRepository implements PkbItemQueryRepository {

    private final PkbItemRepository itemRepository;
    private final PkbItemEntityMapper itemMapper;

    @Override
    public Optional<PkbItemRecord> findByUserIdAndItemId(UUID userId, UUID itemId) {
        return itemRepository.findByPkbItemIdAndUserId(itemId, userId)
                .map(itemMapper::toRecord);
    }

    @Override
    public List<PkbItemRecord> search(PkbItemSearchCriteria criteria) {
        return itemRepository.findAll(specification(criteria), defaultSort()).stream()
                .filter(item -> matchesPrivacyScope(item, criteria.privacyScope()))
                .filter(item -> matchesTextQuery(item, criteria.textQuery()))
                .skip(criteria.offset())
                .limit(criteria.limit())
                .map(itemMapper::toRecord)
                .toList();
    }

    private Specification<PkbItemEntity> specification(PkbItemSearchCriteria criteria) {
        return (root, _, criteriaBuilder) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("userId"), criteria.userId()));

            if (criteria.entityType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("entityType"), criteria.entityType()));
            }
            if (criteria.subtype() != null) {
                predicates.add(criteriaBuilder.equal(root.get("subtype"), criteria.subtype()));
            }
            if (criteria.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.observedFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("observedAt"),
                        instant(criteria.observedFrom())));
            }
            if (criteria.observedUntil() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("observedAt"),
                        instant(criteria.observedUntil())));
            }
            if (criteria.validAt() != null) {
                Instant validAt = instant(criteria.validAt());
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.isNull(root.get("validFrom")),
                        criteriaBuilder.lessThanOrEqualTo(root.get("validFrom"), validAt)));
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.isNull(root.get("validUntil")),
                        criteriaBuilder.greaterThanOrEqualTo(root.get("validUntil"), validAt)));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort defaultSort() {
        return Sort.by(
                Sort.Order.desc("observedAt").nullsLast(),
                Sort.Order.desc("createdAt"));
    }

    private boolean matchesPrivacyScope(PkbItemEntity item, String privacyScope) {
        if (privacyScope == null) {
            return true;
        }
        String[] scopes = item.getPrivacyScope();
        return scopes != null && Arrays.asList(scopes).contains(privacyScope);
    }

    private boolean matchesTextQuery(PkbItemEntity item, String textQuery) {
        if (textQuery == null) {
            return true;
        }

        String normalizedQuery = textQuery.toLowerCase(Locale.ROOT);
        return contains(item.getEntityType(), normalizedQuery)
                || contains(item.getSubtype(), normalizedQuery)
                || contains(item.getStatus(), normalizedQuery)
                || contains(item.getSourceType(), normalizedQuery)
                || contains(item.getSourceId(), normalizedQuery)
                || contains(item.getVerificationStatus(), normalizedQuery)
                || contains(String.valueOf(item.getPayload()), normalizedQuery);
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private Instant instant(OffsetDateTime value) {
        return value.toInstant();
    }
}
