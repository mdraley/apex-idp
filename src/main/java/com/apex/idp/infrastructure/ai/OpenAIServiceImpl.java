package com.apex.idp.infrastructure.ai;

import com.apex.idp.domain.document.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of OpenAI service for document analysis and AI interactions.
 * Uses OpenAI GPT models for various NLP tasks.
 */
@Slf4j
@Service
public class OpenAIServiceImpl implements OpenAIService {

    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    @Value("${openai.model:gpt-3.5-turbo}")
    private String model;

    @Value("${openai.temperature:0.7}")
    private double temperature;

    @Value("${openai.max-tokens:2000}")
    private int maxTokens;

    @Value("${openai.timeout:60}")
    private int timeoutSeconds;

    @Value("${openai.max-content-length:4000}")
    private int maxContentLength;

    // Patterns for extracting document references
    private static final Pattern DOC_REF_PATTERN = Pattern.compile("\\[doc:(\\d+)\\]");
    private static final Pattern PAGE_REF_PATTERN = Pattern.compile("\\(page\\s+(\\d+)\\)");

    public OpenAIServiceImpl(@Value("${openai.api.key}") String apiKey,
                             ObjectMapper objectMapper) {
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(timeoutSeconds));
        this.objectMapper = objectMapper;
    }

    @Override
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public AnalysisResult analyzeBatch(List<Document> documents) throws AIServiceException {
        log.info("Analyzing batch of {} documents", documents.size());

        try {
            // Prepare context from documents
            String context = prepareContext(documents);

            String systemPrompt = """
                You are an AI assistant specialized in analyzing business documents.
                Analyze the provided documents and provide:
                1. A comprehensive summary
                2. Key insights and patterns
                3. Actionable recommendations
                4. Risk factors or concerns
                5. Overall confidence score (0.0-1.0)
                
                Format your response as JSON with the following structure:
                {
                  "summary": "...",
                  "keyInsights": ["...", "..."],
                  "recommendations": ["...", "..."],
                  "riskFactors": ["...", "..."],
                  "confidenceScore": 0.0,
                  "metadata": {}
                }
                """;

            String userPrompt = String.format("""
                Please analyze these %d documents:
                
                %s
                
                Provide your analysis in the specified JSON format.
                """, documents.size(), context);

            String response = generateResponse(systemPrompt, userPrompt);

            // Parse JSON response
            JsonNode jsonResponse = objectMapper.readTree(response);

            return AnalysisResult.builder()
                    .summary(jsonResponse.path("summary").asText())
                    .keyInsights(parseJsonArray(jsonResponse, "keyInsights"))
                    .recommendations(parseJsonArray(jsonResponse, "recommendations"))
                    .riskFactors(parseJsonArray(jsonResponse, "riskFactors"))
                    .confidenceScore(jsonResponse.path("confidenceScore").asDouble(0.8))
                    .metadata(Map.of(
                            "documentsAnalyzed", documents.size(),
                            "model", model,
                            "timestamp", System.currentTimeMillis()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Failed to analyze batch", e);
            throw new AIServiceException("Batch analysis failed", e);
        }
    }

    @Override
    public ChatResponse chat(String message, ChatContext context,
                             List<ChatMessage> conversationHistory) throws AIServiceException {
        log.debug("Processing chat message with context");

        try {
            // Build context string from ChatContext
            String contextString = buildContextString(context);

            String systemPrompt = """
                You are an AI assistant helping with document processing and analysis.
                Use the provided context to answer questions accurately.
                Be concise but thorough in your responses.
                If you reference specific documents or invoices, use the format [doc:ID] for references.
                If you're unsure about something, say so rather than guessing.
                """;

            // Build conversation messages
            List<com.theokanning.openai.completion.chat.ChatMessage> messages = new ArrayList<>();
            messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
                    ChatMessageRole.SYSTEM.value(), systemPrompt));

            // Add conversation history
            for (ChatMessage historyMsg : conversationHistory) {
                messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
                        historyMsg.getRole(), historyMsg.getContent()));
            }

            // Add context as a system message
            if (!contextString.isEmpty()) {
                messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
                        ChatMessageRole.SYSTEM.value(), "Context: " + contextString));
            }

            // Add current user message
            messages.add(new com.theokanning.openai.completion.chat.ChatMessage(
                    ChatMessageRole.USER.value(), message));

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String responseContent = result.getChoices().get(0).getMessage().getContent();

            // Extract document references from response
            List<DocumentReference> references = extractDocumentReferences(responseContent, contextString);

            return ChatResponse.builder()
                    .message(responseContent)
                    .references(references)
                    .conversationId(context.getAdditionalContext()
                            .getOrDefault("conversationId", UUID.randomUUID().toString()).toString())
                    .timestamp(System.currentTimeMillis())
                    .metadata(Map.of(
                            "model", model,
                            "contextLength", contextString.length(),
                            "tokensUsed", result.getUsage().getTotalTokens()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Chat request failed", e);
            throw new AIServiceException("Failed to process chat request", e);
        }
    }

    @Override
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public InvoiceExtractionResult extractInvoiceData(String ocrText) throws AIServiceException {
        log.debug("Extracting invoice data from OCR text");

        try {
            String systemPrompt = """
                Extract invoice information from the provided text.
                Return the data as JSON with this exact structure:
                {
                  "invoiceNumber": "...",
                  "invoiceDate": "YYYY-MM-DD",
                  "dueDate": "YYYY-MM-DD",
                  "vendorName": "...",
                  "vendorAddress": "...",
                  "totalAmount": "0.00",
                  "currency": "USD",
                  "lineItems": [
                    {
                      "description": "...",
                      "quantity": 1,
                      "unitPrice": "0.00",
                      "totalPrice": "0.00",
                      "itemCode": "..."
                    }
                  ],
                  "extractionConfidence": 0.0
                }
                
                Use null for missing fields. Be precise with dates and amounts.
                """;

            String userPrompt = "Extract invoice data from:\n\n" +
                    truncateContent(ocrText, maxContentLength);

            String response = generateResponse(systemPrompt, userPrompt);
            JsonNode jsonResponse = objectMapper.readTree(response);

            // Parse line items
            List<LineItemData> lineItems = new ArrayList<>();
            JsonNode lineItemsNode = jsonResponse.path("lineItems");
            if (lineItemsNode.isArray()) {
                for (JsonNode item : lineItemsNode) {
                    lineItems.add(LineItemData.builder()
                            .description(item.path("description").asText())
                            .quantity(item.path("quantity").asInt(1))
                            .unitPrice(item.path("unitPrice").asText())
                            .totalPrice(item.path("totalPrice").asText())
                            .itemCode(item.path("itemCode").asText())
                            .build());
                }
            }

            return InvoiceExtractionResult.builder()
                    .invoiceNumber(jsonResponse.path("invoiceNumber").asText())
                    .invoiceDate(jsonResponse.path("invoiceDate").asText())
                    .dueDate(jsonResponse.path("dueDate").asText())
                    .vendorName(jsonResponse.path("vendorName").asText())
                    .vendorAddress(jsonResponse.path("vendorAddress").asText())
                    .totalAmount(jsonResponse.path("totalAmount").asText())
                    .currency(jsonResponse.path("currency").asText("USD"))
                    .lineItems(lineItems)
                    .extractionConfidence(jsonResponse.path("extractionConfidence").asDouble(0.8))
                    .additionalFields(new HashMap<>())
                    .build();

        } catch (Exception e) {
            log.error("Failed to extract invoice data", e);
            throw new AIServiceException("Invoice data extraction failed", e);
        }
    }

    @Override
    public ClassificationResult classifyDocument(Document document, List<String> categories)
            throws AIServiceException {
        log.debug("Classifying document with categories: {}", categories);

        try {
            String categoriesList = String.join(", ", categories);
            String systemPrompt = String.format("""
                Classify the document into one of these categories: %s
                
                Return JSON with format:
                {
                  "category": "CATEGORY_NAME",
                  "confidence": 0.0-1.0,
                  "categoryScores": {"category1": 0.0, "category2": 0.0, ...},
                  "reasoning": "Brief explanation"
                }
                """, categoriesList);

            String userPrompt = "Classify this document:\n" +
                    truncateContent(document.getExtractedText(), maxContentLength);

            String response = generateResponse(systemPrompt, userPrompt, 0.1); // Lower temperature for classification
            JsonNode jsonResponse = objectMapper.readTree(response);

            // Parse category scores
            Map<String, Double> categoryScores = new HashMap<>();
            JsonNode scoresNode = jsonResponse.path("categoryScores");
            if (scoresNode.isObject()) {
                scoresNode.fields().forEachRemaining(entry ->
                        categoryScores.put(entry.getKey(), entry.getValue().asDouble()));
            }

            return ClassificationResult.builder()
                    .category(jsonResponse.path("category").asText())
                    .confidence(jsonResponse.path("confidence").asDouble())
                    .categoryScores(categoryScores)
                    .reasoning(jsonResponse.path("reasoning").asText())
                    .build();

        } catch (Exception e) {
            log.error("Document classification failed", e);
            throw new AIServiceException("Failed to classify document", e);
        }
    }

    @Override
    public InvoiceSummary generateInvoiceSummary(Map<String, Object> invoiceData)
            throws AIServiceException {
        log.debug("Generating invoice summary");

        try {
            String systemPrompt = """
                Generate a concise summary of the invoice data.
                Focus on: vendor, invoice number, date, total amount, and key items.
                Return as JSON:
                {
                  "vendorName": "...",
                  "invoiceNumber": "...",
                  "date": "...",
                  "totalAmount": 0.00,
                  "keyItems": ["item1", "item2"],
                  "metadata": {}
                }
                """;

            String userPrompt = "Invoice data:\n" + objectMapper.writeValueAsString(invoiceData);
            String response = generateResponse(systemPrompt, userPrompt);
            JsonNode jsonResponse = objectMapper.readTree(response);

            return InvoiceSummary.builder()
                    .vendorName(jsonResponse.path("vendorName").asText())
                    .invoiceNumber(jsonResponse.path("invoiceNumber").asText())
                    .date(jsonResponse.path("date").asText())
                    .totalAmount(jsonResponse.path("totalAmount").asDouble())
                    .keyItems(parseJsonArray(jsonResponse, "keyItems"))
                    .metadata(Map.of("generated", true))
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate invoice summary", e);
            throw new AIServiceException("Failed to generate invoice summary", e);
        }
    }

    @Override
    public Map<String, Object> extractStructuredData(Document document,
                                                     Map<String, String> extractionSchema)
            throws AIServiceException {
        log.debug("Extracting structured data with schema");

        try {
            String schemaDescription = extractionSchema.entrySet().stream()
                    .map(e -> String.format("%s: %s", e.getKey(), e.getValue()))
                    .collect(Collectors.joining("\n"));

            String systemPrompt = """
                Extract data from the document according to this schema.
                Return the data as valid JSON matching the schema exactly.
                For missing fields, use null.
                """;

            String userPrompt = String.format("""
                Schema:
                %s
                
                Document:
                %s
                
                Extract the data as JSON.
                """, schemaDescription, truncateContent(document.getExtractedText(), maxContentLength));

            String response = generateResponse(systemPrompt, userPrompt, 0.1);
            return objectMapper.readValue(response, Map.class);

        } catch (Exception e) {
            log.error("Structured data extraction failed", e);
            throw new AIServiceException("Failed to extract structured data", e);
        }
    }

    // Helper methods

    private String generateResponse(String systemPrompt, String userPrompt) throws AIServiceException {
        return generateResponse(systemPrompt, userPrompt, temperature);
    }

    private String generateResponse(String systemPrompt, String userPrompt, double temp)
            throws AIServiceException {
        try {
            ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
            ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), userPrompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(Arrays.asList(systemMessage, userMessage))
                    .temperature(temp)
                    .maxTokens(maxTokens)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            return result.getChoices().get(0).getMessage().getContent();

        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            throw new AIServiceException("Failed to generate response", e);
        }
    }

    private String prepareContext(List<Document> documents) {
        return documents.stream()
                .limit(10) // Limit to prevent token overflow
                .map(doc -> String.format("Document: %s\nType: %s\nContent: %s\n",
                        doc.getFileName(),
                        doc.getContentType(),
                        truncateContent(doc.getExtractedText(), 500)))
                .collect(Collectors.joining("\n---\n"));
    }

    private String buildContextString(ChatContext context) {
        StringBuilder sb = new StringBuilder();

        if (context.getBatchId() != null) {
            sb.append("Batch ID: ").append(context.getBatchId()).append("\n");
        }

        if (context.getDocumentId() != null) {
            sb.append("Document ID: ").append(context.getDocumentId()).append("\n");
        }

        if (context.getRelevantDocumentIds() != null && !context.getRelevantDocumentIds().isEmpty()) {
            sb.append("Related Documents: ").append(String.join(", ", context.getRelevantDocumentIds())).append("\n");
        }

        if (context.getAdditionalContext() != null) {
            context.getAdditionalContext().forEach((key, value) ->
                    sb.append(key).append(": ").append(value).append("\n"));
        }

        return sb.toString();
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    private List<String> parseJsonArray(JsonNode node, String fieldName) {
        List<String> result = new ArrayList<>();
        JsonNode arrayNode = node.path(fieldName);
        if (arrayNode.isArray()) {
            arrayNode.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    private List<DocumentReference> extractDocumentReferences(String text, String context) {
        List<DocumentReference> references = new ArrayList<>();

        // Extract [doc:ID] references
        Matcher docMatcher = DOC_REF_PATTERN.matcher(text);
        while (docMatcher.find()) {
            String docId = docMatcher.group(1);
            references.add(DocumentReference.builder()
                    .documentId(docId)
                    .relevanceScore(0.9)
                    .build());
        }

        // Extract page references
        Matcher pageMatcher = PAGE_REF_PATTERN.matcher(text);
        while (pageMatcher.find()) {
            Integer pageNum = Integer.parseInt(pageMatcher.group(1));
            if (!references.isEmpty()) {
                references.get(references.size() - 1).builder()
                        .pageNumber(pageNum)
                        .build();
            }
        }

        return references;
    }
}