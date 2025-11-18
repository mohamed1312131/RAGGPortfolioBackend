package com.example.chatml.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tracking_events", indexes = {
    @Index(name = "idx_tracking_session", columnList = "sessionId"),
    @Index(name = "idx_tracking_type", columnList = "eventType"),
    @Index(name = "idx_tracking_timestamp", columnList = "timestamp")
})
@Data
public class TrackingEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false)
    private String sessionId;
    
    @Column(nullable = false)
    private String eventType; // page_view, question, link_click
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    private String location;
    
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
