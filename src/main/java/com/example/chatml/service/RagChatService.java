package com.example.chatml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RagChatService {

    private final AzureEmbeddingClient embeddingClient;
    private final ChromaClient chromaClient;
    private final AzureChatClient chatClient;

    public String answer(String question) {
        System.out.println("\n=== RAG Query Processing ===");
        System.out.println("Question: " + question);

        // 1. Get embedding for the question
        List<Double> queryEmbedding = embeddingClient.getEmbedding(question);

        // 2. Detect if this is a temporal query (last, recent, current, etc.)
        boolean isTemporalQuery = isTemporalQuery(question);
        System.out.println("Temporal query detected: " + isTemporalQuery);

        // 3. Determine category filter based on question content
        Map<String, Object> filter = null;
        String lowerQuestion = question.toLowerCase();

        if (lowerQuestion.contains("experience") || lowerQuestion.contains("work") ||
                lowerQuestion.contains("job") || lowerQuestion.contains("professional")) {
            filter = Map.of("category", "Experience");
            System.out.println("Filter: Experience only");
        } else if (lowerQuestion.contains("project")) {
            filter = Map.of("category", "Project");
            System.out.println("Filter: Project only");
        } else if (lowerQuestion.contains("education") || lowerQuestion.contains("study") ||
                lowerQuestion.contains("degree") || lowerQuestion.contains("university")) {
            filter = Map.of("category", "Education");
            System.out.println("Filter: Education only");
        }

        // 4. Retrieve documents with metadata
        // If asking for "all" experiences/projects, get more results
        int retrievalCount = 5;
        if (lowerQuestion.contains("all") || lowerQuestion.contains("list") ||
                lowerQuestion.contains("what are")) {
            retrievalCount = 10; // Get more when user wants everything
            System.out.println("Comprehensive query detected - retrieving more results");
        }

        List<Map<String, Object>> results = chromaClient.querySimilar(queryEmbedding, retrievalCount, filter);
        System.out.println("Results found: " + results.size());

        if (results.isEmpty()) {
            System.out.println("No results - trying without filter...");
            results = chromaClient.querySimilar(queryEmbedding, 5, null);
        }

        if (results.isEmpty()) {
            return "I don't have enough information to answer that question based on my portfolio data.";
        }

        // 5. Sort by rank and year if temporal query
        if (isTemporalQuery) {
            results = sortByRelevance(results);
            System.out.println("Results sorted by recency/rank");
        }

        // 6. Build context from top results
        StringBuilder contextBuilder = new StringBuilder();

        // If asking for "all", include all filtered results (up to 8)
        // Otherwise, limit to top 3 most relevant
        int limit = (lowerQuestion.contains("all") || lowerQuestion.contains("list") ||
                lowerQuestion.contains("what are")) ?
                Math.min(8, results.size()) : Math.min(3, results.size());

        System.out.println("Including " + limit + " results in context");

        for (int i = 0; i < limit; i++) {
            Map<String, Object> result = results.get(i);
            String doc = (String) result.get("document");
            Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");

            contextBuilder.append("=== SOURCE ").append(i + 1).append(" ===\n");
            contextBuilder.append(doc);

            // Add year hint if available (helps GPT understand recency)
            if (metadata != null && metadata.containsKey("year")) {
                contextBuilder.append("\n[Year: ").append(metadata.get("year")).append("]");
            }
            if (metadata != null && metadata.containsKey("rank")) {
                contextBuilder.append(" [Priority: ").append(metadata.get("rank")).append("]");
            }
            contextBuilder.append("\n\n");
        }

        String context = contextBuilder.toString();

        // Limit context length to avoid content filter issues
        if (context.length() > 4000) {
            context = context.substring(0, 4000) + "\n[Content truncated...]";
        }

        System.out.println("Context built successfully (" + context.length() + " chars)");
        System.out.println("=== End RAG Processing ===\n");

        // 7. Build the final prompt (Azure filter-friendly)
        String prompt = """
                You are Mohamed's portfolio assistant.
                
                Use the context below to answer the question accurately.
                If the question asks about "last" or "recent" experience, use the entry with the most recent year.
                If the question asks about "all" experiences, list ALL the experiences provided in chronological order (most recent first).
                Keep your answer natural and well-structured.
                
                CONTEXT:
                %s
                
                QUESTION: %s
                
                ANSWER:
                """.formatted(context, question);

        // 8. Send to Azure Chat
        return chatClient.chat(prompt);
    }

    /**
     * Check if query asks for temporal information (last, recent, current, etc.)
     */
    private boolean isTemporalQuery(String query) {
        String lower = query.toLowerCase();
        return lower.contains("last") ||
                lower.contains("recent") ||
                lower.contains("latest") ||
                lower.contains("current") ||
                lower.contains("newest") ||
                lower.contains("most recent");
    }

    /**
     * Sort results by rank (ascending) and year (descending)
     * Lower rank = higher priority (rank 1 is best)
     * Higher year = more recent
     */
    private List<Map<String, Object>> sortByRelevance(List<Map<String, Object>> results) {
        results.sort((r1, r2) -> {
            // First: Sort by rank (lower is better)
            Integer rank1 = extractRank(r1);
            Integer rank2 = extractRank(r2);

            if (rank1 != null && rank2 != null && !rank1.equals(rank2)) {
                return rank1.compareTo(rank2); // Lower rank first
            }

            // Second: Sort by year (higher/more recent is better)
            Integer year1 = extractYear(r1);
            Integer year2 = extractYear(r2);

            if (year1 == null && year2 == null) return 0;
            if (year1 == null) return 1;
            if (year2 == null) return -1;

            return year2.compareTo(year1); // More recent first
        });
        return results;
    }

    /**
     * Extract rank from metadata
     */
    private Integer extractRank(Map<String, Object> result) {
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        if (metadata != null && metadata.containsKey("rank")) {
            try {
                Object rank = metadata.get("rank");
                if (rank instanceof Integer) return (Integer) rank;
                if (rank instanceof String) return Integer.parseInt((String) rank);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * Extract year from metadata (handles "2024-Present", "2022-2024", etc.)
     */
    private Integer extractYear(Map<String, Object> result) {
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");

        if (metadata != null) {
            // Try start_year first (most reliable)
            if (metadata.containsKey("start_year")) {
                try {
                    Object startYear = metadata.get("start_year");
                    return Integer.parseInt(startYear.toString());
                } catch (NumberFormatException ignored) {}
            }

            // Fallback to year field
            if (metadata.containsKey("year")) {
                String yearStr = metadata.get("year").toString();

                // "Present" = current year (2024)
                if (yearStr.toLowerCase().contains("present")) {
                    return 2024;
                }

                // Extract first 4-digit year
                Pattern pattern = Pattern.compile("(\\d{4})");
                Matcher matcher = pattern.matcher(yearStr);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        }

        return null;
    }
}