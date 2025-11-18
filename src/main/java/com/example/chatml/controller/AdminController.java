package com.example.chatml.controller;

import com.example.chatml.dto.*;
import com.example.chatml.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminController {
    
    private final AnalyticsService analyticsService;
    
    /**
     * Track any event (page_view, question, link_click)
     * POST /api/admin/track
     */
    @PostMapping("/track")
    public ResponseEntity<Map<String, Boolean>> trackEvent(@RequestBody TrackingRequestDTO request) {
        try {
            analyticsService.trackEvent(request);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false));
        }
    }
    
    /**
     * Get overall statistics
     * GET /api/admin/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponseDTO> getStats() {
        return ResponseEntity.ok(analyticsService.getStats());
    }
    
    /**
     * Get daily users data for chart
     * GET /api/admin/daily-users?startDate=2024-11-10&endDate=2024-11-16
     */
    @GetMapping("/daily-users")
    public ResponseEntity<List<DailyUserDTO>> getDailyUsers(
        @RequestParam String startDate,
        @RequestParam String endDate
    ) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return ResponseEntity.ok(analyticsService.getDailyUsers(start, end));
    }
    
    /**
     * Get top 5 most asked questions
     * GET /api/admin/questions/top
     */
    @GetMapping("/questions/top")
    public ResponseEntity<List<QuestionDTO>> getTopQuestions() {
        return ResponseEntity.ok(analyticsService.getTopQuestions());
    }
    
    /**
     * Get question history with optional filters
     * GET /api/admin/questions/history?search=skills&filter=today
     */
    @GetMapping("/questions/history")
    public ResponseEntity<List<QuestionHistoryDTO>> getQuestionHistory(
        @RequestParam(required = false) String search,
        @RequestParam(required = false, defaultValue = "all") String filter
    ) {
        return ResponseEntity.ok(analyticsService.getQuestionHistory(search, filter));
    }
}
