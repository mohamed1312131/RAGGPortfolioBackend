package com.example.chatml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AzureEmbeddingClient {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${azure.openai.base-url}")
    private String baseUrl;
    @Value("${azure.openai.apiKey}")
    private String apiKey;
    @Value("${azure.openai.embeddingApiVersion}")
    private String embeddingApiVersion;
    @Value("${azure.openai.embeddingDeployment}")
    private String embeddingDeployment;

    public List<Double> getEmbedding(String text) {
        String url = String.format("%s/openai/deployments/%s/embeddings?api-version=%s",
                baseUrl, embeddingDeployment, embeddingApiVersion);
        System.out.println("Attempting embedding URL: " + url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(Map.of("input", text), headers);

        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
        Map<String, Object> data = ((List<Map<String, Object>>) response.get("data")).get(0);
        return (List<Double>) data.get("embedding");
    }
}
