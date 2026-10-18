package com.biocompass.pkb.persistence.repository;

import com.biocompass.pkb.persistence.entity.PkbProcessedCommandEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PkbProcessedCommandRepository extends JpaRepository<PkbProcessedCommandEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO pkb_processed_command (
                command_id,
                command_type,
                user_id,
                payload_hash,
                producer_service,
                actor_id,
                actor_user_id,
                purpose_of_use,
                prior_decision_reference,
                applied_decision_reference,
                correlation_id
            )
            VALUES (
                :commandId,
                :commandType,
                :userId,
                :payloadHash,
                :producerService,
                :actorId,
                :actorUserId,
                :purposeOfUse,
                :priorDecisionReference,
                :appliedDecisionReference,
                :correlationId
            )
            ON CONFLICT (command_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("commandId") UUID commandId,
            @Param("commandType") String commandType,
            @Param("userId") UUID userId,
            @Param("payloadHash") String payloadHash,
            @Param("producerService") String producerService,
            @Param("actorId") String actorId,
            @Param("actorUserId") UUID actorUserId,
            @Param("purposeOfUse") String purposeOfUse,
            @Param("priorDecisionReference") String priorDecisionReference,
            @Param("appliedDecisionReference") String appliedDecisionReference,
            @Param("correlationId") String correlationId
    );

    @Modifying
    @Query(value = """
            UPDATE pkb_processed_command
            SET delivery_count = delivery_count + 1,
                last_received_at = now()
            WHERE command_id = :commandId
            """, nativeQuery = true)
    int recordDuplicate(@Param("commandId") UUID commandId);
}
