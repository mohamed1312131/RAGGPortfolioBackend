package com.example.chatml.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionHistoryDTO {
    private Integer id;
    private String question;
    private String timestamp;
    private String userLocation;
    private String sessionDuration;
}
