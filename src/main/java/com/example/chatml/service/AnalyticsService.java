package com.example.chatml.service;

import com.example.chatml.dto.*;
import com.example.chatml.entity.*;
import com.example.chatml.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    
    private final UserSessionRepository sessionRepository;
    private final TrackingEventRepository eventRepository;
    private final QuestionRepository questionRepository;
    private final DailyStatsRepository dailyStatsRepository;
    
    /**
     * Track any event (page_view, question, link_click)
     */
    @Transactional
    public void trackEvent(TrackingRequestDTO request) {
        try {
            // Get or create session
            UserSession session = sessionRepository.findBySessionId(request.getSessionId())
                .orElseGet(() -> createNewSession(request));
            
            // Update last visit
            session.setLastVisit(LocalDateTime.now());
            
            // Create tracking event
            TrackingEvent event = new TrackingEvent();
            event.setSessionId(request.getSessionId());
            event.setEventType(request.getEventType());
            event.setTimestamp(parseTimestamp(request.getTimestamp()));
            event.setLocation(request.getLocation());
            event.setMetadata(request.getMetadata());
            eventRepository.save(event);
            
            // Handle specific event types
            switch (request.getEventType()) {
                case "page_view":
                    session.setTotalPageViews(session.getTotalPageViews() + 1);
                    break;
                case "question":
                    session.setTotalQuestions(session.getTotalQuestions() + 1);
                    saveQuestion(request);
                    break;
                case "link_click":
                    session.setTotalLinkClicks(session.getTotalLinkClicks() + 1);
                    break;
            }
            
            sessionRepository.save(session);
            updateDailyStats(LocalDate.now(), request.getEventType());
            
            log.info("Tracked event: {} for session: {}", request.getEventType(), request.getSessionId());
        } catch (Exception e) {
            log.error("Error tracking event: {}", e.getMessage(), e);
        }
    }
    
    private UserSession createNewSession(TrackingRequestDTO request) {
        UserSession session = new UserSession();
        session.setSessionId(request.getSessionId());
        session.setFirstVisit(LocalDateTime.now());
        session.setLastVisit(LocalDateTime.now());
        session.setLocation(request.getLocation());
        return sessionRepository.save(session);
    }
    
    private void saveQuestion(TrackingRequestDTO request) {
        Question question = new Question();
        question.setSessionId(request.getSessionId());
        question.setQuestion(request.getQuestion());
        question.setTimestamp(parseTimestamp(request.getTimestamp()));
        question.setLocation(request.getLocation());
        
        if (request.getMetadata() != null) {
            Object tokensUsed = request.getMetadata().get("tokensUsed");
            if (tokensUsed != null) {
                question.setTokensUsed(((Number) tokensUsed).intValue());
            }
        }
        
        questionRepository.save(question);
    }
    
    private void updateDailyStats(LocalDate date, String eventType) {
        DailyStats stats = dailyStatsRepository.findById(date)
            .orElseGet(() -> {
                DailyStats newStats = new DailyStats();
                newStats.setDate(date);
                return newStats;
            });
        
        switch (eventType) {
            case "page_view":
                stats.setPageViewCount(stats.getPageViewCount() + 1);
                // Count unique users for the day
                LocalDateTime startOfDay = date.atStartOfDay();
                Long uniqueUsers = sessionRepository.countUniqueUsersAfter(startOfDay);
                stats.setUserCount(uniqueUsers.intValue());
                break;
            case "question":
                stats.setQuestionCount(stats.getQuestionCount() + 1);
                break;
            case "link_click":
                stats.setLinkClickCount(stats.getLinkClickCount() + 1);
                break;
        }
        
        dailyStatsRepository.save(stats);
    }
    
    /**
     * Get overall statistics
     */
    public StatsResponseDTO getStats() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        
        Long totalUsers = sessionRepository.count();
        Long todayUsers = sessionRepository.countUniqueUsersAfter(today);
        Long totalQuestions = questionRepository.count();
        Long todayQuestions = eventRepository.countByTypeAfter("question", today);
        
        // Get top question
        List<Object[]> topQuestions = questionRepository.findTopQuestions(PageRequest.of(0, 1));
        String topQuestion = topQuestions.isEmpty() ? "No questions yet" : (String) topQuestions.get(0)[0];
        
        // Calculate average session time (simplified)
        String avgSessionTime = calculateAvgSessionTime();
        
        StatsResponseDTO response = new StatsResponseDTO();
        response.setTotalUsers(totalUsers);
        response.setTodayUsers(todayUsers);
        response.setTotalQuestions(totalQuestions);
        response.setTodayQuestions(todayQuestions);
        response.setAvgSessionTime(avgSessionTime);
        response.setTopQuestion(topQuestion);
        
        return response;
    }
    
    /**
     * Get daily users for chart
     */
    public List<DailyUserDTO> getDailyUsers(LocalDate startDate, LocalDate endDate) {
        List<DailyStats> stats = dailyStatsRepository.findByDateBetweenOrderByDateAsc(startDate, endDate);
        
        // Fill in missing dates with zero values
        List<LocalDate> allDates = startDate.datesUntil(endDate.plusDays(1)).collect(Collectors.toList());
        
        return allDates.stream()
            .map(date -> {
                DailyStats stat = stats.stream()
                    .filter(s -> s.getDate().equals(date))
                    .findFirst()
                    .orElse(null);
                
                String formattedDate = date.format(DateTimeFormatter.ofPattern("MMM dd"));
                int users = stat != null ? stat.getUserCount() : 0;
                int questions = stat != null ? stat.getQuestionCount() : 0;
                
                return new DailyUserDTO(formattedDate, users, questions);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get top 5 most asked questions
     */
    public List<QuestionDTO> getTopQuestions() {
        List<Object[]> results = questionRepository.findTopQuestions(PageRequest.of(0, 5));
        
        if (results.isEmpty()) {
            return List.of();
        }
        
        Long maxCount = (Long) results.get(0)[1];
        
        return IntStream.range(0, results.size())
            .mapToObj(i -> {
                Object[] row = results.get(i);
                String question = (String) row[0];
                Long count = (Long) row[1];
                int percentage = (int) ((count * 100.0) / maxCount);
                String trend = calculateTrend(question); // Simplified
                
                return new QuestionDTO(i + 1, question, count, percentage, trend);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get question history with optional filters
     */
    public List<QuestionHistoryDTO> getQuestionHistory(String search, String filter) {
        LocalDateTime startDate = getStartDateForFilter(filter);
        
        List<Question> questions;
        if (search != null && !search.isEmpty()) {
            questions = questionRepository.searchQuestions(search, PageRequest.of(0, 50));
        } else {
            questions = questionRepository.findRecentQuestions(startDate, PageRequest.of(0, 50));
        }
        
        return IntStream.range(0, questions.size())
            .mapToObj(i -> {
                Question q = questions.get(i);
                return new QuestionHistoryDTO(
                    i + 1,
                    q.getQuestion(),
                    formatRelativeTime(q.getTimestamp()),
                    q.getLocation() != null ? q.getLocation() : "Unknown",
                    "4m 32s" // Simplified - calculate actual duration if needed
                );
            })
            .collect(Collectors.toList());
    }
    
    // Helper methods
    
    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
    
    private LocalDateTime getStartDateForFilter(String filter) {
        LocalDate today = LocalDate.now();
        switch (filter) {
            case "today":
                return today.atStartOfDay();
            case "week":
                return today.minusDays(7).atStartOfDay();
            default:
                return today.minusMonths(1).atStartOfDay();
        }
    }
    
    private String formatRelativeTime(LocalDateTime timestamp) {
        Duration duration = Duration.between(timestamp, LocalDateTime.now());
        long minutes = duration.toMinutes();
        long hours = duration.toHours();
        long days = duration.toDays();
        
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        if (hours < 24) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        return days + " day" + (days > 1 ? "s" : "") + " ago";
    }
    
    private String calculateAvgSessionTime() {
        // Simplified - calculate actual average if needed
        return "4m 32s";
    }
    
    private String calculateTrend(String question) {
        // Simplified - calculate actual trend if needed
        return "+12%";
    }
}
