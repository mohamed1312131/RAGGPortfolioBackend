package com.example.chatml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@RequiredArgsConstructor
public class ChromaClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${chroma.url}")
    private String chromaUrl;

    @Value("${chroma.collection}")
    private String collectionName;

    private String collectionId;

    /**
     * Ensures the collection exists and stores its UUID
     */
    public void getOrCreateCollection() {
        if (this.collectionId != null) {
            return;
        }

        String collectionUrl = chromaUrl + "/api/v1/collections/" + collectionName;
        try {
            Map<String, Object> response = restTemplate.getForObject(collectionUrl, Map.class);
            System.out.println("Collection '" + collectionName + "' already exists.");

            this.collectionId = (String) response.get("id");
            if (this.collectionId == null) {
                throw new RuntimeException("Collection response did not contain an 'id'");
            }

        } catch (HttpClientErrorException.NotFound e) {
            System.out.println("Collection '" + collectionName + "' not found (404). Creating...");
            createCollection();
        } catch (HttpServerErrorException.InternalServerError e) {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody != null && responseBody.contains("does not exist")) {
                System.out.println("Collection '" + collectionName + "' not found (500). Creating...");
                createCollection();
            } else {
                System.err.println("FATAL: An unexpected 500 error occurred: " + e.getMessage());
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            System.err.println("FATAL: An unexpected error occurred while checking for collection: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void createCollection() {
        try {
            String createUrl = chromaUrl + "/api/v1/collections";
            Map<String, String> createRequest = Map.of("name", collectionName);

            Map<String, Object> response = restTemplate.postForObject(createUrl, createRequest, Map.class);
            System.out.println("Successfully created collection: " + collectionName);

            this.collectionId = (String) response.get("id");
            if (this.collectionId == null) {
                throw new RuntimeException("Create collection response did not contain an 'id'");
            }
        } catch (Exception createError) {
            System.err.println("FATAL: Error creating collection: " + createError.getMessage());
            throw new RuntimeException(createError);
        }
    }

    /**
     * NEW: Upsert with metadata support
     */
    public void addOrUpdateEmbedding(String id, List<Double> embedding, String documentText, Map<String, Object> metadata) {
        getOrCreateCollection();
        String upsertUrl = chromaUrl + "/api/v1/collections/" + this.collectionId + "/upsert";

        List<Float> floatEmbedding = embedding.stream().map(Double::floatValue).toList();

        Map<String, Object> body = Map.of(
                "ids", List.of(id),
                "embeddings", List.of(floatEmbedding),
                "documents", List.of(documentText),
                "metadatas", List.of(metadata != null ? metadata : Map.of("source", "portfolio"))
        );

        try {
            restTemplate.postForObject(upsertUrl, body, Map.class);
            System.out.println("✓ Upserted: " + id);
        } catch (Exception e) {
            System.err.println("Error upserting embedding: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Backward compatibility: upsert without metadata
     */
    public void addOrUpdateEmbedding(String id, List<Double> embedding, String documentText) {
        addOrUpdateEmbedding(id, embedding, documentText, Map.of("source", "portfolio"));
    }

    /**
     * NEW: Query with metadata filtering and return structured results
     */
    public List<Map<String, Object>> querySimilar(List<Double> embedding, int topK, Map<String, Object> whereFilter) {
        getOrCreateCollection();
        String queryUrl = chromaUrl + "/api/v1/collections/" + this.collectionId + "/query";

        List<Float> floatEmbedding = embedding.stream().map(Double::floatValue).toList();

        Map<String, Object> queryRequest = new HashMap<>();
        queryRequest.put("query_embeddings", List.of(floatEmbedding));
        queryRequest.put("n_results", topK);
        queryRequest.put("include", List.of("documents", "distances", "metadatas"));

        // Add filter if provided
        if (whereFilter != null && !whereFilter.isEmpty()) {
            queryRequest.put("where", whereFilter);
        }

        try {
            Map<String, Object> response = restTemplate.postForObject(queryUrl, queryRequest, Map.class);

            if (response == null) {
                System.err.println("Query returned null response");
                return Collections.emptyList();
            }

            List<List<String>> docs = (List<List<String>>) response.get("documents");
            List<List<Map<String, Object>>> metadatas = (List<List<Map<String, Object>>>) response.get("metadatas");
            List<List<Double>> distances = (List<List<Double>>) response.get("distances");

            if (docs == null || docs.isEmpty() || docs.get(0).isEmpty()) {
                return Collections.emptyList();
            }

            // Combine results into structured objects
            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 0; i < docs.get(0).size(); i++) {
                Map<String, Object> result = new HashMap<>();
                result.put("document", docs.get(0).get(i));
                result.put("metadata", metadatas != null && !metadatas.isEmpty() ? metadatas.get(0).get(i) : null);
                result.put("distance", distances != null && !distances.isEmpty() ? distances.get(0).get(i) : null);
                results.add(result);
            }

            return results;

        } catch (Exception e) {
            System.err.println("Error querying embeddings: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Backward compatibility: query without filter
     */
    public List<String> querySimilar(List<Double> embedding, int topK) {
        return querySimilar(embedding, topK, null).stream()
                .map(r -> (String) r.get("document"))
                .toList();
    }
}