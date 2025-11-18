package com.example.chatml.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "questions", indexes = {
    @Index(name = "idx_question_session", columnList = "sessionId"),
    @Index(name = "idx_question_timestamp", columnList = "timestamp")
})
@Data
public class Question {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false)
    private String sessionId;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    private String location;
    
    @Column(columnDefinition = "INTEGER DEFAULT 0")
    private Integer tokensUsed = 0;
    
    private Integer sessionDuration; // in seconds
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
