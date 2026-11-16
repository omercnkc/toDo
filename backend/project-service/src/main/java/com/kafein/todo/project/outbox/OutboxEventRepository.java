package com.kafein.todo.project.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Zamanı gelmiş bekleyen kayıtlar, eskiden yeniye. {@code FOR UPDATE SKIP LOCKED}: başka bir relay örneğinin
     * o an kilitlediği satırlar atlanır, aynı kayıt iki örnek tarafından alınmaz. Transaction içinde çağrılır.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE status = 0 AND next_attempt_at <= :now"
            + " ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockDueEvents(@Param("now") Instant now, @Param("limit") int limit);

    /** Günlük temizlik: {@code before}'dan önce işlenmiş SENT kayıtları. */
    @Modifying
    @Query("delete from OutboxEvent e where e.status = :status and e.processedAt < :before")
    int deleteProcessedBefore(@Param("status") OutboxStatus status, @Param("before") Instant before);

    List<OutboxEvent> findByAggregateIdOrderByIdAsc(long aggregateId);
}
