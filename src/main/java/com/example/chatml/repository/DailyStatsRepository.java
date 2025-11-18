package com.example.chatml.repository;

import com.example.chatml.entity.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyStatsRepository extends JpaRepository<DailyStats, LocalDate> {
    
    List<DailyStats> findByDateBetweenOrderByDateAsc(LocalDate start, LocalDate end);
}
