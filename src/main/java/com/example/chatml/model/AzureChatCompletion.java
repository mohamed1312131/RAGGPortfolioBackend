package com.example.chatml.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AzureChatCompletion {
    private String content;
    private int totalTokens;
}