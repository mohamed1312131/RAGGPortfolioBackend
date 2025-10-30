package com.example.chatml.service;

import com.example.chatml.model.AzureChatCompletion;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AzureChatClient {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${azure.openai.base-url}")
    private String baseUrl;
    @Value("${azure.openai.apiKey}")
    private String apiKey;
    @Value("${azure.openai.chatApiVersion}")
    private String chatApiVersion;
    @Value("${azure.openai.chatDeployment}")
    private String chatDeployment;

    public String chat(String prompt) {
        String url = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                baseUrl, chatDeployment, chatApiVersion);

        Map<String, Object> body = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are a portfolio assistant. Answer strictly based on stored user information."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
        Map<String, Object> firstChoice = ((List<Map<String, Object>>) response.get("choices")).get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        return message.get("content").toString();
    }

    public AzureChatCompletion chatWithMessages(java.util.List<com.example.chatml.model.ChatMessage> messages) {
        String url = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                baseUrl, chatDeployment, chatApiVersion);

        java.util.List<java.util.Map<String, String>> wireMessages = new java.util.ArrayList<>();
        for (var m : messages) {
            wireMessages.add(java.util.Map.of("role", m.getRole(), "content", m.getContent()));
        }

        java.util.Map<String, Object> body = java.util.Map.of(
                "messages", wireMessages,
                "temperature", 0.2
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(body, headers);
        java.util.Map<String, Object> response = restTemplate.postForObject(url, entity, java.util.Map.class);

        if (response == null || response.get("choices") == null) {
            throw new RuntimeException("Azure chat model returned no response");
        }

        // 1. Extract Content
        java.util.Map<String, Object> firstChoice = ((java.util.List<java.util.Map<String, Object>>) response.get("choices")).get(0);
        java.util.Map<String, Object> message = (java.util.Map<String, Object>) firstChoice.get("message");
        String content = String.valueOf(message.get("content"));

        // 2. Extract Token Usage
        java.util.Map<String, Object> usage = (java.util.Map<String, Object>) response.get("usage");
        int totalTokens = 0;
        if (usage != null && usage.containsKey("total_tokens")) {
            // Note: Azure API returns this as an Integer
            totalTokens = (Integer) usage.get("total_tokens");
        }

        // 3. Return the composite object
        return new AzureChatCompletion(content, totalTokens);
    }
}
