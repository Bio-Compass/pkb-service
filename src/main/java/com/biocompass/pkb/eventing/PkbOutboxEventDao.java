package com.biocompass.pkb.eventing;

import com.biocompass.pkb.command.event.PkbDomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PkbOutboxEventDao {

    private final PkbOutboxEventRepository eventRepository;
    private final PkbEventPayloadMapper eventPayloadMapper;

    @Transactional
    public void save(PkbDomainEvent event) {
        eventRepository.save(PkbOutboxEventEntity.from(event, eventPayloadMapper.toPayload(event)));
    }

    @Transactional
    public List<PkbOutboxEventEntity> claimUnpublished(int batchSize) {
        return eventRepository.findUnpublishedForRelay(batchSize);
    }

}
