Mohamed's AI Portfolio Assistant (Backend)
This is the Spring Boot backend that serves as the "brain" for my AI portfolio assistant. It implements a complete Retrieval-Augmented Generation (RAG) pipeline, connecting my personal data in a vector store to a powerful LLM hosted on Azure.

Core Purpose
This API's job is to provide accurate, context-aware answers to questions about my professional profile. It transforms simple questions into rich, data-driven answers by retrieving relevant information from my portfolio and using an LLM to synthesize a natural response.

This backend handles:

Data Ingestion & Embedding

Conversational RAG Logic

LLM Prompt Engineering

Token-based Cost Management

This is an excellent idea. A strong backend README is just as important as the frontend one, as it showcases your system architecture, design choices, and API-building skills.

Here is a comprehensive README.md for your Spring Boot backend, based on all the code you've provided.

Mohamed's AI Portfolio Assistant (Backend)
This is the Spring Boot backend that serves as the "brain" for my AI portfolio assistant. It implements a complete Retrieval-Augmented Generation (RAG) pipeline, connecting my personal data in a vector store to a powerful LLM hosted on Azure.

It is a stateless, robust API designed to serve the Next.js Frontend.

Core Purpose
This API's job is to provide accurate, context-aware answers to questions about my professional profile. It transforms simple questions into rich, data-driven answers by retrieving relevant information from my portfolio and using an LLM to synthesize a natural response.

This backend handles:

Data Ingestion & Embedding

Conversational RAG Logic

LLM Prompt Engineering

Token-based Cost Management

Architecture & Request Flow
The entire system is designed to be intelligent and context-aware. Here is the step-by-step flow for a single query:

Receive Request: The ChatRagController receives a POST /api/chat/query request containing the ChatRequest (full history + totalTokensUsedSoFar).

Token Limit Check: RagChatService immediately checks if totalTokensUsedSoFar exceeds the CONVERSATION_TOKEN_LIMIT (e.g., 3000 tokens). If it has, the request is blocked to prevent abuse.

Contextual Query Embedding (The "Memory"):

To solve ambiguous follow-up questions (e.g., "What technologies did he use there?"), the service does not just embed the last question.

The buildEmbeddingQuery helper creates a context-aware string from the last 3 messages (e.g., user: ... assistant: ... user: ...).

This rich string is sent to the AzureEmbeddingClient.

Call Azure Embedding: The client POSTs the string to the Azure OpenAI text-embedding-ada-002 deployment, which returns a 1536-dimension vector.

Smart Vector Search:

RagChatService analyzes the raw question for keywords (e.g., "experience", "project").

It passes the vector and a metadata filter (e.g., {"category": "Project"}) to the ChromaClient.

ChromaClient queries the ChromaDB instance to find the top K semantically similar and filtered documents.

Context Building & Sorting:

If the query was "temporal" ("most recent job"), the results are sorted by rank (priority) and year (recency).

The top 3-5 documents are formatted into a CONTEXT: block.

Final Prompt Engineering: A complex prompt is assembled for the LLM, including:

System Prompt: (Defines the rules and persona: "You are Mohamed's assistant...").

Chat History: (The full history, for conversational flow).

Context & Question: (The newly retrieved CONTEXT block and the user's last QUESTION).

Call Azure Chat: AzureChatClient sends this full payload to the Azure OpenAI gpt-35-turbo deployment.

Token Counting & Response:

The client parses the usage.total_tokens field from the Azure response.

RagChatService calculates the newTotalTokens = totalTokensUsedSoFar + tokensThisTurn.

The final ChatResponse object (containing the answer, newTotalTokens, and limitReached flag) is returned to the frontend.

Tech Stack & Tools
Framework: Spring Boot 3 (Java 17)

Used for its robust, production-grade REST controllers, dependency injection, and simple configuration.

LLM Service: Azure OpenAI (gpt-35-turbo)

A powerful chat model deployed securely on my Azure instance, providing fast and coherent responses.

Embedding Service: Azure OpenAI (text-embedding-ada-002)

The industry-standard model for generating high-quality text embeddings, also deployed on Azure.

Vector Database: ChromaDB

An open-source vector store used to index and query my portfolio documents based on semantic similarity.

HTTP Client: Spring RestTemplate

Used in AzureChatClient and ChromaClient to communicate with the external Azure and ChromaDB APIs.

Data Ingestion: Jackson

Used by Spring to automatically parse the portfolio-data.json for the ingestion controller.
