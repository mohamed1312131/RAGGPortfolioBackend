package com.example.chatml.repository;

import com.example.chatml.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    
    Optional<UserSession> findBySessionId(String sessionId);
    
    @Query("SELECT COUNT(DISTINCT u.sessionId) FROM UserSession u WHERE u.firstVisit >= :startDate")
    Long countUniqueUsersAfter(@Param("startDate") LocalDateTime startDate);
}
