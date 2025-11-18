package com.example.chatml.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
@Data
public class UserSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String sessionId;
    
    @Column(nullable = false)
    private LocalDateTime firstVisit;
    
    @Column(nullable = false)
    private LocalDateTime lastVisit;
    
    private String location;
    
    @Column(columnDefinition = "INTEGER DEFAULT 0")
    private Integer totalPageViews = 0;
    
    @Column(columnDefinition = "INTEGER DEFAULT 0")
    private Integer totalQuestions = 0;
    
    @Column(columnDefinition = "INTEGER DEFAULT 0")
    private Integer totalLinkClicks = 0;
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (firstVisit == null) {
            firstVisit = LocalDateTime.now();
        }
        if (lastVisit == null) {
            lastVisit = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
