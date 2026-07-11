package com.biocompass.pkb.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PkbItemQueryServiceTest {

    @Test
    void returnsUserScopedItem() {
        UUID userId = UUID.randomUUID();
        PkbItemRecord item = TestPkbItems.itemForUser(userId);
        RecordingRepository repository = new RecordingRepository();
        repository.item = Optional.of(item);
        PkbItemQueryService service = new PkbItemQueryService(repository);

        PkbItemRecord result = service.getItem(userId, item.itemId());

        assertThat(result).isEqualTo(item);
        assertThat(repository.findCalls).isEqualTo(1);
    }

    @Test
    void reportsNotFoundForMissingUserScopedItem() {
        UUID userId = UUID.randomUUID();
        RecordingRepository repository = new RecordingRepository();
        PkbItemQueryService service = new PkbItemQueryService(repository);

        assertThatThrownBy(() -> service.getItem(userId, UUID.randomUUID()))
                .isInstanceOf(PkbItemNotFoundException.class);
    }

    @Test
    void returnsRepositorySearchResults() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        RecordingRepository repository = new RecordingRepository();
        PkbItemRecord readable = TestPkbItems.itemForUser(userId);
        PkbItemRecord other = TestPkbItems.itemForUser(otherUserId);
        repository.searchResults = List.of(readable, other);
        PkbItemQueryService service = new PkbItemQueryService(repository);

        List<PkbItemRecord> results = service.search(PkbItemSearchCriteria.forUser(userId));

        assertThat(results).containsExactly(readable, other);
    }

    private static final class RecordingRepository implements PkbItemQueryRepository {
        private Optional<PkbItemRecord> item = Optional.empty();
        private List<PkbItemRecord> searchResults = List.of();
        private int findCalls;

        @Override
        public Optional<PkbItemRecord> findByUserIdAndItemId(UUID userId, UUID itemId) {
            findCalls++;
            return item.filter(record -> record.userId().equals(userId) && record.itemId().equals(itemId));
        }

        @Override
        public List<PkbItemRecord> search(PkbItemSearchCriteria criteria) {
            return searchResults;
        }
    }
}
