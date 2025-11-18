package com.example.chatml.dto;

import lombok.Data;

@Data
public class StatsResponseDTO {
    private Long totalUsers;
    private Long todayUsers;
    private Long totalQuestions;
    private Long todayQuestions;
    private String avgSessionTime;
    private String topQuestion;
}
