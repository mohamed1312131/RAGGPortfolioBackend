package com.example.chatml.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {
    private String answer;
    private int totalTokensUsed;
    private boolean limitReached;
}