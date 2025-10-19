package com.example.chatml.service;


import com.example.chatml.model.ChatMessage; // Import the model
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

    /**
     * UPDATED: The method now accepts the entire chat history.
     */
    public String answer(List<ChatMessage> history) {
        System.out.println("\n=== RAG Query Processing ===");

        if (history == null || history.isEmpty()) {
            return "I'm sorry, I didn't receive a question.";
        }

        // 1. Get the most recent question from the history
        ChatMessage lastUserMessage = history.get(history.size() - 1);
        if (!"user".equalsIgnoreCase(lastUserMessage.getRole())) {
            // This shouldn't happen if the client is built correctly
            return "I'm sorry, the last message was not from a user.";
        }
        String question = lastUserMessage.getContent();
        System.out.println("Current Question: " + question);

        // 2. Get embedding for the *current* question
        String textToEmbed = buildEmbeddingQuery(history);
        System.out.println("Text to Embed: \n" + textToEmbed);
        List<Double> queryEmbedding = embeddingClient.getEmbedding(textToEmbed);

        // 3. Detect if this is a temporal query
        boolean isTemporalQuery = isTemporalQuery(question);
        System.out.println("Temporal query detected: " + isTemporalQuery);

        // 4. Determine category filter based on question content
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

        // 5. Retrieve documents with metadata
        int retrievalCount = 5;
        if (lowerQuestion.contains("all") || lowerQuestion.contains("list") ||
                lowerQuestion.contains("what are")) {
            retrievalCount = 10;
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

        // 6. Sort by rank and year if temporal query
        if (isTemporalQuery) {
            results = sortByRelevance(results);
            System.out.println("Results sorted by recency/rank");
        }

        // 7. Build context from top results
        StringBuilder contextBuilder = new StringBuilder();
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
            if (metadata != null && metadata.containsKey("year")) {
                contextBuilder.append("\n[Year: ").append(metadata.get("year")).append("]");
            }
            if (metadata != null && metadata.containsKey("rank")) {
                contextBuilder.append(" [Priority: ").append(metadata.get("rank")).append("]");
            }
            contextBuilder.append("\n\n");
        }

        String context = contextBuilder.toString();
        if (context.length() > 4000) {
            context = context.substring(0, 4000) + "\n[Content truncated...]";
        }

        System.out.println("Context built successfully (" + context.length() + " chars)");
        System.out.println("=== End RAG Processing ===\n");

        // 8. Build the FINAL MESSAGE LIST for the LLM (THE KEY CHANGE)

        // 8a. Define the System Prompt.
        // This is much better than the one in your simple `chatClient.chat(prompt)`
        String systemPrompt = """
                You are Mohamed's portfolio assistant.
                
                You MUST follow these rules:
                1.  Answer the user's question based *only* on the provided CONTEXT.
                2.  If the CONTEXT is not sufficient, say "I don't have enough information to answer that question based on my portfolio data."
                3.  Use the chat history for conversational flow (e.g., if they say "tell me more"), but use the new CONTEXT for the facts.
                4.  If the question asks about "last" or "recent" experience, use the entry with the most recent year from the CONTEXT.
                5.  If the question asks about "all" experiences, list ALL the experiences provided in the CONTEXT in chronological order (most recent first).
                6.  Keep your answer natural and well-structured.
                """;

        // 8b. Create the new list of messages to send to Azure
        List<ChatMessage> messagesForAzure = new ArrayList<>();
        messagesForAzure.add(new ChatMessage("system", systemPrompt));

        // 8c. Add all *previous* history (everything *except* the last user message)
        if (history.size() > 1) {
            messagesForAzure.addAll(history.subList(0, history.size() - 1));
        }

        // 8d. Create the new, context-injected user message
        String userPromptWithContext = """
                CONTEXT:
                %s
                
                QUESTION: %s
                """.formatted(context, question);

        messagesForAzure.add(new ChatMessage("user", userPromptWithContext));

        // 9. Send to Azure Chat using the stateful method
        //    (This replaces the old `chatClient.chat(prompt)` call)
        return chatClient.chatWithMessages(messagesForAzure);
    }

    //
    // --- ALL HELPER METHODS BELOW ARE UNCHANGED ---
    //

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
     */
    private List<Map<String, Object>> sortByRelevance(List<Map<String, Object>> results) {
        results.sort((r1, r2) -> {
            Integer rank1 = extractRank(r1);
            Integer rank2 = extractRank(r2);
            if (rank1 != null && rank2 != null && !rank1.equals(rank2)) {
                return rank1.compareTo(rank2);
            }
            Integer year1 = extractYear(r1);
            Integer year2 = extractYear(r2);
            if (year1 == null && year2 == null) return 0;
            if (year1 == null) return 1;
            if (year2 == null) return -1;
            return year2.compareTo(year1);
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
     * Extract year from metadata
     */
    private Integer extractYear(Map<String, Object> result) {
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        if (metadata != null) {
            if (metadata.containsKey("start_year")) {
                try {
                    Object startYear = metadata.get("start_year");
                    return Integer.parseInt(startYear.toString());
                } catch (NumberFormatException ignored) {}
            }
            if (metadata.containsKey("year")) {
                String yearStr = metadata.get("year").toString();
                if (yearStr.toLowerCase().contains("present")) {
                    return 2024; // Or use Calendar.getInstance().get(Calendar.YEAR)
                }
                Pattern pattern = Pattern.compile("(\\d{4})");
                Matcher matcher = pattern.matcher(yearStr);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        }
        return null;
    }
    /**
     * Creates a context-aware string for embedding.
     * This includes the last few messages to help RAG
     * understand follow-up questions.
     */
    private String buildEmbeddingQuery(List<ChatMessage> history) {
        StringBuilder queryBuilder = new StringBuilder();

        // Take up to the last 3 messages to build context
        int historySize = history.size();
        // Start from the 3rd-to-last message, or the beginning if history is short
        int startIndex = Math.max(0, historySize - 3);

        for (int i = startIndex; i < historySize; i++) {
            ChatMessage msg = history.get(i);
            queryBuilder.append(msg.getRole())
                    .append(": ")
                    .append(msg.getContent())
                    .append("\n"); // Separate messages with a newline
        }

        // If history is just one message (the question), this just returns "user: [question]"
        return queryBuilder.toString().trim();
    }
}