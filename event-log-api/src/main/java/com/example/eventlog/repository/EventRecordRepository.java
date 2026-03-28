package com.example.eventlog.repository;

import com.example.eventlog.model.EventRecordEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRecordRepository extends JpaRepository<EventRecordEntity, UUID> {

    Optional<EventRecordEntity> findFirstByCallerIdAndResolvedTimestampAndMessageHash(
            String callerId,
            Instant resolvedTimestamp,
            String messageHash
    );

    @Query("""
            SELECT e FROM EventRecordEntity e
            WHERE e.resolvedTimestamp <= :to AND e.resolvedTimestamp >= :from
              AND (:lastTimestamp IS NULL OR e.resolvedTimestamp < :lastTimestamp
                   OR (e.resolvedTimestamp = :lastTimestamp AND e.id < :lastId))
            ORDER BY e.resolvedTimestamp DESC, e.id DESC
            """)
    List<EventRecordEntity> findPage(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("lastTimestamp") Instant lastTimestamp,
            @Param("lastId") UUID lastId,
            Pageable pageable
    );

    long deleteByResolvedTimestampBefore(Instant threshold);
}
