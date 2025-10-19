package com.example.chatml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PortfolioIngestionService {

    private final AzureEmbeddingClient embeddingClient;
    private final ChromaClient chromaClient;

    /**
     * Ingests a single portfolio item (Map) into ChromaDB.
     * Now includes metadata for better filtering and ranking.
     */
    public void ingestPortfolio(Map<String, Object> portfolio) {
        // 1. Validate and extract the unique ID
        String portfolioId = (String) portfolio.get("id");
        if (portfolioId == null || portfolioId.isBlank()) {
            System.err.println("Portfolio item is missing a unique 'id' field. Skipping ingestion.");
            return;
        }

        // 2. BUILD RICH DOCUMENT TEXT
        StringBuilder doc = new StringBuilder();

        // Category and Title
        doc.append("CATEGORY: ").append(portfolio.getOrDefault("category", "N/A")).append(". ");
        doc.append("TITLE: ").append(portfolio.getOrDefault("title", "N/A")).append(". ");

        // For Experience entries, add company and position
        if ("Experience".equals(portfolio.get("category"))) {
            if (portfolio.containsKey("company")) {
                doc.append("COMPANY: ").append(portfolio.get("company")).append(". ");
            }
            if (portfolio.containsKey("position")) {
                doc.append("POSITION: ").append(portfolio.get("position")).append(". ");
            }
            if (portfolio.containsKey("duration")) {
                doc.append("DURATION: ").append(portfolio.get("duration")).append(". ");
            }
        }

        // Summary
        doc.append("SUMMARY: ").append(portfolio.getOrDefault("summary", "N/A")).append(". ");

        // Main Content
        Object contentObj = portfolio.get("content");
        if (contentObj instanceof String content && !content.isBlank()) {
            String cleanContent = content.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            doc.append("DETAILS: ").append(cleanContent).append(". ");
        }

        // Responsibilities (for experience)
        if (portfolio.containsKey("responsibilities")) {
            List<String> responsibilities = (List<String>) portfolio.get("responsibilities");
            if (responsibilities != null && !responsibilities.isEmpty()) {
                doc.append("RESPONSIBILITIES: ").append(String.join(", ", responsibilities)).append(". ");
            }
        }

        // Achievements
        if (portfolio.containsKey("achievements")) {
            List<String> achievements = (List<String>) portfolio.get("achievements");
            if (achievements != null && !achievements.isEmpty()) {
                doc.append("ACHIEVEMENTS: ").append(String.join(", ", achievements)).append(". ");
            }
        }

        // Technologies
        if (portfolio.containsKey("technologies")) {
            List<String> tech = (List<String>) portfolio.get("technologies");
            if (tech != null && !tech.isEmpty()) {
                doc.append("TECHNOLOGIES: ").append(String.join(", ", tech)).append(". ");
            }
        }

        String documentText = doc.toString().trim();

        if (documentText.length() < 50) {
            System.err.println("Document text is too short to be meaningful for ID: " + portfolioId + ". Skipping.");
            return;
        }

        // 3. Generate embedding
        List<Double> embedding = embeddingClient.getEmbedding(documentText);

        // 4. BUILD METADATA FOR FILTERING AND RANKING
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "portfolio");
        metadata.put("category", portfolio.getOrDefault("category", "Unknown"));

        // Add rank (CRITICAL for ordering) - convert to string for ChromaDB
        if (portfolio.containsKey("rank")) {
            Object rankObj = portfolio.get("rank");
            metadata.put("rank", rankObj.toString());
        }

        // Add tags as comma-separated string
        if (portfolio.containsKey("tags")) {
            List<String> tags = (List<String>) portfolio.get("tags");
            if (tags != null && !tags.isEmpty()) {
                metadata.put("tags", String.join(",", tags));
            }
        }

        // Extract year metadata from nested metadata object
        Object metadataObj = portfolio.get("metadata");
        if (metadataObj instanceof Map metaMap) {
            if (metaMap.containsKey("year")) {
                metadata.put("year", metaMap.get("year").toString());
            }
            if (metaMap.containsKey("start_year")) {
                metadata.put("start_year", metaMap.get("start_year").toString());
            }
            if (metaMap.containsKey("end_year")) {
                metadata.put("end_year", metaMap.get("end_year").toString());
            }
            if (metaMap.containsKey("is_current")) {
                metadata.put("is_current", metaMap.get("is_current").toString());
            }
            if (metaMap.containsKey("employment_type")) {
                metadata.put("employment_type", metaMap.get("employment_type").toString());
            }
        }

        // If no start_year but we have duration, extract it
        if (!metadata.containsKey("start_year") && portfolio.containsKey("duration")) {
            String duration = (String) portfolio.get("duration");
            String startYear = extractStartYear(duration);
            if (startYear != null) {
                metadata.put("start_year", startYear);
            }
        }

        System.out.println("✓ Ingesting [" + portfolioId + "] " + portfolio.get("title"));
        System.out.println("  Rank: " + metadata.get("rank") + " | Year: " + metadata.get("year"));

        // 5. Add to ChromaDB with metadata
        chromaClient.addOrUpdateEmbedding(portfolioId, embedding, documentText, metadata);
    }

    /**
     * Extracts start year from duration strings like "2024 - Present", "2022 - 2024", "2019"
     */
    private String extractStartYear(String duration) {
        if (duration == null || duration.isBlank()) return null;

        String[] parts = duration.split("[-–—]");
        String yearPart = parts[0].trim();

        if (yearPart.matches(".*\\d{4}.*")) {
            return yearPart.replaceAll("\\D", "");
        }
        return null;
    }
}