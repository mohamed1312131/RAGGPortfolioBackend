package com.example.chatml.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_stats")
@Data
public class DailyStats {
    
    @Id
    private LocalDate date;
    
    @Column(columnDefinition = "INTEGER DEFAULT 0")
    private Integer userCount = 0;
    
    @Column(columnDefinition = "INTEGER DEFAULT 0")
    private Integer questionCount = 0;
    
    @Column(columnDefinition = "INTEGER DEFAULT 0")
    private Integer pageViewCount = 0;
    
    @Column(columnDefinition = "INTEGER DEFAULT 0")
    private Integer linkClickCount = 0;
    
    @Column(columnDefinition = "INTEGER DEFAULT 0")
    private Integer avgSessionDuration = 0;
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
