package com.apex.idp.infrastructure.ai;

import com.apex.idp.domain.document.Document;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for interacting with OpenAI API.
 * Handles document analysis and chat interactions using GPT models.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.model:gpt-4}")
    private String model;

    @Value("${openai.max-tokens:2000}")
    private int maxTokens;

    @Value("${openai.temperature:0.7}")
    private double temperature;

    private final ObjectMapper objectMapper;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Analyzes a batch of documents using OpenAI
     */
    public AnalysisResult analyzeBatch(List<Document> documents) throws Exception {
        log.info("Analyzing batch of {} documents with OpenAI", documents.size());

        // Prepare context from documents
        String context = prepareDocumentContext(documents);

        // Create analysis prompt
        String prompt = createAnalysisPrompt(context);

        // Call OpenAI API
        ChatResponse response = callChatAPI(prompt, "system");

        // Parse and structure the response
        return parseAnalysisResponse(response.getContent());
    }

    /**
     * Handles chat interaction about a batch
     */
    public String chat(String message, String batchContext) throws Exception {
        log.debug("Processing chat message with batch context");

        String systemPrompt = "You are an AI assistant helping analyze financial documents. " +
                "Context: " + batchContext;

        ChatResponse response = callChatAPI(message, systemPrompt);
        return response.getContent();
    }

    /**
     * Extracts structured data from invoice text
     */
    public InvoiceExtractionResult extractInvoiceData(String ocrText) throws Exception {
        log.debug("Extracting invoice data using OpenAI");

        String prompt = createExtractionPrompt(ocrText);
        ChatResponse response = callChatAPI(prompt, "system");

        return parseExtractionResponse(response.getContent());
    }

    /**
     * Calls OpenAI Chat API
     */
    private ChatResponse callChatAPI(String userMessage, String systemMessage) throws Exception {
        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(Arrays.asList(
                        ChatMessage.builder()
                                .role("system")
                                .content(systemMessage)
                                .build(),
                        ChatMessage.builder()
                                .role("user")
                                .content(userMessage)
                                .build()
                ))
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        String jsonBody = objectMapper.writeValueAsString(request);

        Request httpRequest = new Request.Builder()
                .url(apiUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response httpResponse = httpClient.newCall(httpRequest).execute()) {
            if (!httpResponse.isSuccessful()) {
                throw new AIServiceException("OpenAI API call failed: " + httpResponse.code());
            }

            String responseBody = httpResponse.body().string();
            ChatCompletionResponse completionResponse = objectMapper.readValue(
                    responseBody, ChatCompletionResponse.class);

            if (completionResponse.getChoices().isEmpty()) {
                throw new AIServiceException("No response from OpenAI");
            }

            return completionResponse.getChoices().get(0).getMessage();
        }
    }

    /**
     * Prepares document context for analysis
     */
    private String prepareDocumentContext(List<Document> documents) {
        return documents.stream()
                .map(doc -> String.format("Document: %s\nContent: %s\n",
                        doc.getFileName(),
                        doc.getExtractedText() != null ?
                                doc.getExtractedText().substring(0, Math.min(500, doc.getExtractedText().length()))
                                : "No text extracted"))
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * Creates analysis prompt
     */
    private String createAnalysisPrompt(String context) {
        return String.format(
                "Analyze the following batch of financial documents and provide:\n" +
                        "1. A summary of the key information\n" +
                        "2. Total amounts and invoice counts\n" +
                        "3. Key vendors identified\n" +
                        "4. Any anomalies or issues found\n" +
                        "5. Recommendations for processing\n\n" +
                        "Documents:\n%s", context);
    }

    /**
     * Creates extraction prompt
     */
    private String createExtractionPrompt(String ocrText) {
        return String.format(
                "Extract the following information from this invoice text:\n" +
                        "- Invoice Number\n" +
                        "- Invoice Date\n" +
                        "- Due Date\n" +
                        "- Vendor Name\n" +
                        "- Total Amount\n" +
                        "- Line Items (if any)\n\n" +
                        "Return the data in JSON format.\n\n" +
                        "Invoice Text:\n%s", ocrText);
    }

    /**
     * Parses analysis response
     */
    private AnalysisResult parseAnalysisResponse(String content) {
        // Simple parsing - in production, use more sophisticated parsing
        return AnalysisResult.builder()
                .summary(content)
                .recommendations(Arrays.asList(content.split("\n")))
                .metadata(Map.of(
                        "model", model,
                        "timestamp", System.currentTimeMillis()
                ))
                .build();
    }

    /**
     * Parses extraction response
     */
    private InvoiceExtractionResult parseExtractionResponse(String content) {
        try {
            return objectMapper.readValue(content, InvoiceExtractionResult.class);
        } catch (Exception e) {
            log.error("Failed to parse extraction response", e);
            return InvoiceExtractionResult.builder().build();
        }
    }

    /**
     * Result classes
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisResult {
        private String summary;
        private List<String> recommendations;
        private Map<String, Object> metadata;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceExtractionResult {
        private String invoiceNumber;
        private String invoiceDate;
        private String dueDate;
        private String vendorName;
        private String totalAmount;
        private List<Map<String, String>> lineItems;
    }

    /**
     * Request/Response classes for OpenAI API
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ChatRequest {
        private String model;
        private List<ChatMessage> messages;
        private double temperature;
        @JsonProperty("max_tokens")
        private int maxTokens;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ChatMessage {
        private String role;
        private String content;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class ChatCompletionResponse {
        private List<Choice> choices;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class Choice {
        private ChatMessage message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ChatResponse {
        private String content;
    }

    /**
     * Custom exception for AI service errors
     */
    public static class AIServiceException extends Exception {
        public AIServiceException(String message) {
            super(message);
        }

        public AIServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

// Add missing import
import com.fasterxml.jackson.annotation.JsonProperty;