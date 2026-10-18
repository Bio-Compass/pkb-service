package com.biocompass.pkb.persistence;

import com.biocompass.pkb.authorization.*;
import com.biocompass.pkb.command.*;
import com.biocompass.pkb.command.dto.*;
import com.biocompass.pkb.command.event.*;
import com.biocompass.pkb.kafka.*;
import com.biocompass.pkb.eventing.PkbOutboxEventEntity;
import com.biocompass.pkb.eventing.PkbOutboxEventRepository;
import com.biocompass.pkb.persistence.dao.PkbItemDao;
import com.biocompass.pkb.persistence.entity.PkbItemEntity;
import com.biocompass.pkb.persistence.repository.PkbProcessedCommandRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest
class PkbSupersessionIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired PkbCommandService commands;
    @Autowired PkbKafkaCommandProcessor processor;
    @Autowired PkbItemDao items;
    @Autowired PkbProcessedCommandRepository inbox;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PkbAuthorizationGateway authorization;
    @Autowired PkbOutboxEventRepository outbox;

    @BeforeEach
    void configureBoundaries() {
        when(authorization.decide(any())).thenAnswer(invocation -> {
            PkbAuthorizationRequest request = invocation.getArgument(0);
            return new PkbAuthorizationDecision(true, "test:" + request.commandId(), "write",
                    request.resource().targetUserId(), Map.of());
        });
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "true,true,false", "false,true,false", "true,false,false",
            "false,true,true"})
    void concurrentReplacementIsAtomic(boolean firstExplicit, boolean secondExplicit, boolean rollbackFirst)
            throws Exception {
        var user = UUID.randomUUID();
        var original = commands.handle(create(user));
        jdbc.update("delete from pkb_outbox_event where user_id = ?", user);
        var first = message(replacement(user, original.getPkbItemId(), firstExplicit));
        var second = message(replacement(user, original.getPkbItemId(), secondExplicit));
        var cached = new CountDownLatch(1);
        var firstWritten = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var firstPid = new AtomicInteger();
        var secondPid = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            // Preload the original in the waiting transaction to exercise Hibernate's first-level cache.
            var waiter = executor.submit(() -> transaction().execute(status -> {
                secondPid.set(backendPid());
                items.findByUserAndItemId(user, original.getPkbItemId()).orElseThrow();
                cached.countDown();
                waitFor(firstWritten);
                return processor.process(second);
            }));
            var holder = executor.submit(() -> transaction().execute(status -> {
                waitFor(cached);
                firstPid.set(backendPid());
                processor.process(first);
                entityManager.flush();
                firstWritten.countDown();
                waitFor(releaseFirst);
                if (rollbackFirst) status.setRollbackOnly();
                return true;
            }));
            try {
                waitFor(firstWritten);
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                        assertThat(jdbc.queryForObject("select ? = any(pg_blocking_pids(?))", Boolean.class,
                                firstPid.get(), secondPid.get())).isTrue());
                assertThat(eventsFor(user)).isEmpty();
            } finally {
                releaseFirst.countDown();
            }
            assertThat(holder.get(20, TimeUnit.SECONDS)).isTrue();
            if (rollbackFirst) {
                assertThat(waiter.get(20, TimeUnit.SECONDS)).isTrue();
            } else {
                assertThatThrownBy(() -> waiter.get(20, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(PkbCommandValidationException.class)
                        .hasRootCauseMessage("PKB item is already superseded");
            }
        } finally {
            releaseFirst.countDown();
            firstWritten.countDown();
            cached.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
        var winner = rollbackFirst ? second : first;
        var loser = rollbackFirst ? first : second;
        assertThat(inbox.existsById(winner.commandId())).isTrue();
        assertThat(inbox.existsById(loser.commandId())).isFalse();
        assertCommittedReplacement(user, original.getPkbItemId(), winner.command().correlationId());
    }

    @Test
    void sequentialDuplicateRollsBackInboxAndForeignOrMissingTargetsAreNotFound() {
        var user = UUID.randomUUID();
        var original = commands.handle(create(user));
        var winner = message(replacement(user, original.getPkbItemId(), false));
        processor.process(winner);
        var committedEvents = eventsFor(user);
        var loser = message(replacement(user, original.getPkbItemId(), true));
        assertThatThrownBy(() -> processor.process(loser)).isInstanceOf(PkbCommandValidationException.class)
                .hasMessage("PKB item is already superseded");
        assertThat(inbox.existsById(loser.commandId())).isFalse();
        for (var target : List.of(original.getPkbItemId(), UUID.randomUUID())) {
            var foreignUser = UUID.randomUUID();
            var rejected = message(replacement(foreignUser, target, true));
            assertThatThrownBy(() -> processor.process(rejected)).isInstanceOf(PkbCommandNotFoundException.class);
            assertThat(inbox.existsById(rejected.commandId())).isFalse();
            assertThat(items.findAllByUser(foreignUser)).isEmpty();
            assertThat(eventsFor(foreignUser)).isEmpty();
        }
        assertThat(eventsFor(user)).containsExactlyInAnyOrderElementsOf(committedEvents);
        assertThat(items.findAllByUser(user)).hasSize(2);
    }

    @Test
    void lockingLookupRequiresAnExistingWriteTransaction() {
        assertThatThrownBy(() -> items.findOwnedItemForUpdate(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalTransactionStateException.class);
        var readOnly = transaction();
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.execute(status ->
                items.findOwnedItemForUpdate(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessage("Supersession requires a write transaction");
    }

    private void assertCommittedReplacement(UUID user, UUID originalId, String correlation) {
        var saved = items.findAllByUser(user);
        assertThat(saved).hasSize(2);
        var original = items.findByUserAndItemId(user, originalId).orElseThrow();
        var replacement = items.findByUserAndItemId(user, original.getSupersededBy()).orElseThrow();
        assertThat(replacement.getSupersedes()).isEqualTo(originalId);
        assertThat(items.findProvenance(replacement.getPkbItemId())).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from pkb_provenance p join pkb_item i "
                + "on i.pkb_item_id = p.pkb_item_id where i.user_id = ?", Integer.class, user)).isEqualTo(2);
        var events = eventsFor(user);
        assertThat(events).extracting(PkbDomainEvent::eventType)
                .containsExactlyInAnyOrder(PkbDomainEventType.ITEM_SUPERSESSION_LINKED, PkbDomainEventType.ITEM_CREATED);
        assertThat(events).allSatisfy(event -> assertThat(event.correlationId()).isEqualTo(correlation));
        assertThat(events).filteredOn(event -> event.eventType() == PkbDomainEventType.ITEM_SUPERSESSION_LINKED)
                .singleElement().satisfies(event -> {
                    assertThat(event.pkbItemId()).isEqualTo(originalId);
                    assertThat(event.relatedPkbItemId()).isEqualTo(replacement.getPkbItemId());
                });
        assertThat(events).filteredOn(event -> event.eventType() == PkbDomainEventType.ITEM_CREATED)
                .singleElement().satisfies(event -> assertThat(event.pkbItemId()).isEqualTo(replacement.getPkbItemId()));
    }

    private List<PkbDomainEvent> eventsFor(UUID user) {
        return outbox.findAll().stream().filter(event -> event.getUserId().equals(user))
                .map(PkbOutboxEventEntity::toDomainEvent).toList();
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }
    private int backendPid() { return jdbc.queryForObject("select pg_backend_pid()", Integer.class); }
    private static void waitFor(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("Transaction barrier timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
    private static CreatePkbItemCommand create(UUID user) {
        return new CreatePkbItemCommand(user, "observation", "note", "active", Map.of("text", "supersession"),
                "test", UUID.randomUUID().toString(), null, null, null, null, null, List.of(), List.of(), null,
                null, new PkbProvenanceCommand("test", "system", null, null, null), UUID.randomUUID().toString());
    }
    private static PkbCommand<PkbItemEntity> replacement(UUID user, UUID original, boolean explicit) {
        var command = create(user).withUserIdSupersedesAndCorrelationId(user, original, "replacement");
        return explicit ? new SupersedePkbItemCommand(user, original, command, command.correlationId()) : command;
    }
    private static PkbKafkaCommandMessage message(PkbCommand<?> command) {
        var actor = new PkbAuthorizationRequest.Actor("test-actor", command.userId(), false,
                Set.of("user"), Set.of("pkb:write"), "self");
        return new PkbKafkaCommandMessage(UUID.randomUUID(), Instant.now(),
                new PkbKafkaCommandContext("test-producer", actor, "prior-decision"), command);
    }
}
