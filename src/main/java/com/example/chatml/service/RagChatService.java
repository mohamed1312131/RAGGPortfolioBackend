package com.example.chatml.service;

// NEW: Import the required models for token management
import com.example.chatml.model.AzureChatCompletion;
import com.example.chatml.model.ChatRequest;
import com.example.chatml.model.ChatResponse;
import com.example.chatml.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RagChatService {

    // Token limit for a single "demo" conversation
    private static final int CONVERSATION_TOKEN_LIMIT = 3000;

    private final AzureEmbeddingClient embeddingClient;
    private final ChromaClient chromaClient;
    private final AzureChatClient chatClient;

    /**
     * UPDATED: Accepts a ChatRequest and returns a ChatResponse
     * (This includes history AND token count)
     */
    public ChatResponse answer(ChatRequest request) { // <-- UPDATED Signature
        System.out.println("\n=== RAG Query Processing ===");

        // 1. Get request data
        List<ChatMessage> history = request.getHistory();
        int totalTokensUsedSoFar = request.getTotalTokensUsedSoFar();
        System.out.println("Tokens used so far: " + totalTokensUsedSoFar);

        // 2. CHECK TOKEN LIMIT (PRE-FLIGHT)
        if (totalTokensUsedSoFar >= CONVERSATION_TOKEN_LIMIT) {
            System.out.println("TOKEN LIMIT REACHED. Blocking request.");
            return new ChatResponse(
                    "I'm sorry, this chat demo has reached its token limit. Please refresh to start a new conversation.",
                    totalTokensUsedSoFar,
                    true // limitReached = true
            );
        }

        // 3. Get the most recent question
        if (history == null || history.isEmpty()) {
            return new ChatResponse("I'm sorry, I didn't receive a question.", totalTokensUsedSoFar, false);
        }
        ChatMessage lastUserMessage = history.get(history.size() - 1);
        if (!"user".equalsIgnoreCase(lastUserMessage.getRole())) {
            return new ChatResponse("I'm sorry, the last message was not from a user.", totalTokensUsedSoFar, false);
        }
        String question = lastUserMessage.getContent();
        System.out.println("Current Question: " + question);

        // 4. Get embedding for the *context-aware* query
        String textToEmbed = buildEmbeddingQuery(history);
        System.out.println("Text to Embed: \n" + textToEmbed);
        List<Double> queryEmbedding = embeddingClient.getEmbedding(textToEmbed);

        // 5. Detect if this is a temporal query (using the raw question)
        boolean isTemporalQuery = isTemporalQuery(question);
        System.out.println("Temporal query detected: " + isTemporalQuery);

        // 6. Determine category filter based on question content
        // 6. Determine category filter based on question content
        Map<String, Object> filter = null;
        String lowerQuestion = question.toLowerCase();

        // --- START OF FIX ---
        // Prioritize "Project" if mentioned, as it's more specific.
        if (lowerQuestion.contains("project")) {
            filter = Map.of("category", "Project");
            System.out.println("Filter: Project only");
        }
        // If "project" isn't mentioned, *then* check for "experience".
        else if (lowerQuestion.contains("experience") || lowerQuestion.contains("work") ||
                lowerQuestion.contains("job") || lowerQuestion.contains("professional")) {
            filter = Map.of("category", "Experience");
            System.out.println("Filter: Experience only");
        }
        // Finally, check for "education".
        else if (lowerQuestion.contains("education") || lowerQuestion.contains("study") ||
                lowerQuestion.contains("degree") || lowerQuestion.contains("university")) {
            filter = Map.of("category", "Education");
            System.out.println("Filter: Education only");
        }

        // 7. Retrieve documents with metadata
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
            return new ChatResponse("I don't have enough information to answer that question based on my portfolio data.", totalTokensUsedSoFar, false);
        }

        // 8. Sort by rank and year if temporal query
        if (isTemporalQuery) {
            results = sortByRelevance(results);
            System.out.println("Results sorted by recency/rank");
        }

        // 9. Build context from top results
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

        // 10. Build the FINAL MESSAGE LIST for the LLM

        // 10a. Define the System Prompt.
        // ========== THIS IS THE MODIFIED SECTION ==========
        String systemPrompt = """
        You are Mohamed Salah Mechergui, a 28-year-old software engineer from Tunis, Tunisia.
        Answer all questions as yourself, using the first person ("I", "my", "am").
        Be professional, conversational, and authentic—like you're talking to a recruiter or tech lead.
        
        CORE RULES:
        1. Answer questions based PRIMARILY on the provided CONTEXT (your portfolio data).
        2. If the CONTEXT contains the answer, use it directly and confidently.
        3. If the CONTEXT is insufficient, say: "I don't have that specific information in my portfolio, but let me tell you what I do know..." and provide related info if available.
        4. For general technical questions (e.g., "What is Kafka?"), you may briefly explain the concept, then IMMEDIATELY connect it to how YOU used it in your experience from the CONTEXT.
        5. Use chat history for conversational flow (e.g., "tell me more", "what about X"), but always pull FACTS from the new CONTEXT provided.
        
        TEMPORAL QUERIES:
        - "last" / "recent" / "current" → Use the most recent entry by year/date from CONTEXT
        - "all" / "list" → Include ALL relevant entries from CONTEXT, ordered by recency
        - If asked "what are you doing now" or "current work" → prioritize entries with rank 0 or is_current=true
        
        PERSONALITY:
        - Be enthusiastic when discussing technical challenges or learning
        - Show confidence in Java/Spring Boot areas
        - Be honest about what you're still learning (e.g., Azure)
        - Mention your dedication (work + night school) naturally when relevant
        - Keep responses concise but informative (2-4 sentences for simple questions, more for complex ones)
        
        RECRUITER-FOCUSED:
        - Emphasize achievements with impact (e.g., "reduced costs", "improved efficiency")
        - Connect technical skills to real-world problems you've solved
        - Show eagerness for complex, challenging projects
        - Be humble but confident—don't undersell yourself
        
        AVOID:
        - Repeating the same info multiple times
        - Being overly formal or robotic
        - Saying "based on my portfolio data" repeatedly (they know you're a bot)
        - Long, unfocused answers—get to the point
        """;
        // ========== END OF MODIFIED SECTION ==========


        // 10b. Create the new list of messages to send to Azure
        List<ChatMessage> messagesForAzure = new ArrayList<>();
        messagesForAzure.add(new ChatMessage("system", systemPrompt));

        // 10c. Add all *previous* history (everything *except* the last user message)
        if (history.size() > 1) {
            messagesForAzure.addAll(history.subList(0, history.size() - 1));
        }

        // 10d. Create the new, context-injected user message
        String userPromptWithContext = """
                CONTEXT:
                %s
                
                QUESTION: %s
                """.formatted(context, question);

        messagesForAzure.add(new ChatMessage("user", userPromptWithContext));

        // 11. Send to Azure Chat and GET TOKEN COUNT
        //    (This replaces the old `return chatClient.chatWithMessages(...)` call)
        AzureChatCompletion completion = chatClient.chatWithMessages(messagesForAzure);

        String answer = completion.getContent();
        int tokensThisTurn = completion.getTotalTokens();
        System.out.println("Tokens THIS turn: " + tokensThisTurn);

        // 12. Calculate new total and final limit check
        int newTotalTokens = totalTokensUsedSoFar + tokensThisTurn;
        boolean limitReached = newTotalTokens >= CONVERSATION_TOKEN_LIMIT;

        System.out.println("New total tokens: " + newTotalTokens);
        if (limitReached) {
            System.out.println("TOKEN LIMIT WILL BE REACHED AFTER THIS RESPONSE.");
        }

        // 13. Return the full response object
        return new ChatResponse(answer, newTotalTokens, limitReached);
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
                    // Use Calendar to get the current year dynamically
                    return Calendar.getInstance().get(Calendar.YEAR);
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