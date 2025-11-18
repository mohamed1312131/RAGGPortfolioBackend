package com.example.chatml.repository;

import com.example.chatml.entity.Question;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {
    
    @Query("SELECT q.question, COUNT(q) as count FROM Question q GROUP BY q.question ORDER BY count DESC")
    List<Object[]> findTopQuestions(Pageable pageable);
    
    @Query("SELECT q FROM Question q WHERE LOWER(q.question) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY q.timestamp DESC")
    List<Question> searchQuestions(@Param("search") String search, Pageable pageable);
    
    @Query("SELECT q FROM Question q WHERE q.timestamp >= :startDate ORDER BY q.timestamp DESC")
    List<Question> findRecentQuestions(@Param("startDate") LocalDateTime startDate, Pageable pageable);
}
