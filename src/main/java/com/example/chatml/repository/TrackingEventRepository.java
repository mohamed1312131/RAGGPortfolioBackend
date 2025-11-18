package com.example.chatml.repository;

import com.example.chatml.entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {
    
    @Query("SELECT COUNT(e) FROM TrackingEvent e WHERE e.eventType = :type AND e.timestamp >= :startDate")
    Long countByTypeAfter(@Param("type") String type, @Param("startDate") LocalDateTime startDate);
}
