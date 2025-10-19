package com.example.chatml.controller;

import com.example.chatml.service.PortfolioIngestionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

// 1. Class-level annotation for REST endpoint path
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataIngestionController {

    private final PortfolioIngestionService ingestionService;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper = new ObjectMapper(); // Ensure ObjectMapper is initialized

    // 2. Method-level annotation for the specific /load endpoint
    @PostMapping("/load")
    public String loadData() throws Exception {
        System.out.println("Starting portfolio data ingestion...");

        // Find the file in the classpath (src/main/resources)
        // Make sure you have renamed the file to 'portfolio-data.json'
        Resource resource = resourceLoader.getResource("classpath:portfolio-data.json");

        if (!resource.exists()) {
            throw new RuntimeException("Data file not found at src/main/resources/portfolio-data.json");
        }

        try (InputStream inputStream = resource.getInputStream()) {
            // Read the JSON file into a List of Maps
            List<Map<String, Object>> portfolios = objectMapper.readValue(inputStream, new TypeReference<>() {});

            int count = 0;
            for (Map<String, Object> portfolio : portfolios) {
                ingestionService.ingestPortfolio(portfolio);
                count++;
            }

            System.out.println("Ingestion completed.");
            return "Successfully ingested " + count + " documents into ChromaDB.";
        }
    }
}