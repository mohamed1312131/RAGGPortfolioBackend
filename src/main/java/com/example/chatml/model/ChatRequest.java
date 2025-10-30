package com.example.chatml.model;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private List<ChatMessage> history;
    private int totalTokensUsedSoFar = 0;
}