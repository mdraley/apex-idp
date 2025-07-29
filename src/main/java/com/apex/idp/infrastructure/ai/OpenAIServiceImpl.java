package com.apex.idp.infrastructure.ai;

import com.apex.idp.domain.document.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
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
                """;

            String userPrompt = String.format("""
                Please analyze these %d documents:
                
                %s
                
                Provide your analysis in a structured format.
                """, documents.size(), context);

            String response = generateResponse(systemPrompt, userPrompt);

            // Parse response and extract structured data
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentCount", documents.size());
            metadata.put("analysisType", "batch");
            metadata.put("model", model);

            return new AnalysisResult(
                    extractSummary(response),
                    extractRecommendations(response),
                    metadata
            );

        } catch (Exception e) {
            log.error("Batch analysis failed", e);
            throw new AIServiceException("Failed to analyze batch", e);
        }
    }

    @Override
    public Map<String, Object> extractDocumentData(Document document,
                                                   ExtractionSchema extractionSchema)
            throws AIServiceException {
        log.debug("Extracting structured data with schema");

        try {
            String schemaDescription = describeSchema(extractionSchema);

            String systemPrompt = """
                Extract structured data from the document according to the provided schema.
                Return the data as valid JSON matching the schema exactly.
                For missing fields, use null.
                """;

            String userPrompt = String.format("""
                Schema:
                %s
                
                Document:
                %s
                
                Extract the data as JSON.
                """,
                    schemaDescription,
                    truncateContent(document.getExtractedText(), maxContentLength)
            );

            com.theokanning.openai.completion.chat.ChatMessage systemMessage =
                    new com.theokanning.openai.completion.chat.ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
            com.theokanning.openai.completion.chat.ChatMessage userMessage =
                    new com.theokanning.openai.completion.chat.ChatMessage(ChatMessageRole.USER.value(), userPrompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(Arrays.asList(systemMessage, userMessage))
                    .temperature(0.1)
                    .maxTokens(1000)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String responseContent = result.getChoices().get(0).getMessage().getContent();

            Map<String, Object> extractedData = objectMapper.readValue(responseContent, Map.class);

            // Validate extracted data against schema
            validateExtractedData(extractedData, extractionSchema);

            return extractedData;

        } catch (Exception e) {
            log.error("Data extraction failed", e);
            throw new AIServiceException("Failed to extract structured data", e);
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
                If you reference specific documents or invoices, mention them clearly.
                If you're unsure about something, say so rather than guessing.
                """;

            String userPrompt = String.format("""
                Context:
                %s
                
                User Question: %s
                """,
                    truncateContent(contextString, maxContentLength * 2),
                    message
            );

            com.theokanning.openai.completion.chat.ChatMessage systemMessage =
                    new com.theokanning.openai.completion.chat.ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
            com.theokanning.openai.completion.chat.ChatMessage userMessage =
                    new com.theokanning.openai.completion.chat.ChatMessage(ChatMessageRole.USER.value(), userPrompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(Arrays.asList(systemMessage, userMessage))
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String responseContent = result.getChoices().get(0).getMessage().getContent();

            // Extract any document references from the response
            List<DocumentReference> references = extractDocumentReferences(responseContent, contextString);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("model", model);
            metadata.put("contextLength", contextString.length());

            return new ChatResponse(responseContent, references, metadata);

        } catch (Exception e) {
            log.error("Chat request failed", e);
            throw new AIServiceException("Failed to process chat request", e);
        }
    }

    // Overloaded method for backward compatibility
    public ChatResponse chat(String message, ChatContext context,
                             Map<String, Object> metadata) throws AIServiceException {
        return chat(message, context, new ArrayList<>());
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
                  "confidence": 0.0-1.0
                }
                """, categoriesList);

            String userPrompt = "Classify this document:\n" +
                    truncateContent(document.getExtractedText(), maxContentLength);

            com.theokanning.openai.completion.chat.ChatMessage systemMessage =
                    new com.theokanning.openai.completion.chat.ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
            com.theokanning.openai.completion.chat.ChatMessage userMessage =
                    new com.theokanning.openai.completion.chat.ChatMessage(ChatMessageRole.USER.value(), userPrompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(Arrays.asList(systemMessage, userMessage))
                    .temperature(0.1)
                    .maxTokens(200)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String responseContent = result.getChoices().get(0).getMessage().getContent();

            JsonNode jsonResponse = objectMapper.readTree(responseContent);

            String category = jsonResponse.get("category").asText();
            double confidence = jsonResponse.get("confidence").asDouble();

            Map<String, Double> confidenceScores = new HashMap<>();
            confidenceScores.put(category, confidence);

            return new ClassificationResult(category, confidenceScores);

        } catch (Exception e) {
            log.error("Document classification failed", e);
            throw new AIServiceException("Failed to classify document", e);
        }
    }

    @Override
    public ValidationResult validateExtractedData(Map<String, Object> extractedData,
                                                  List<ValidationRule> validationRules)
            throws AIServiceException {
        log.debug("Validating extracted data against {} rules", validationRules.size());

        List<ValidationIssue> issues = new ArrayList<>();

        for (ValidationRule rule : validationRules) {
            Object value = extractedData.get(rule.getFieldName());

            if (!evaluateRule(value, rule)) {
                issues.add(new ValidationIssue(
                        rule.getFieldName(),
                        rule.getErrorMessage(),
                        "error"
                ));
            }
        }

        boolean isValid = issues.isEmpty();
        log.debug("Validation result: {}, {} issues found", isValid, issues.size());

        return new ValidationResult(isValid, issues);
    }

    @Override
    public InvoiceSummary generateInvoiceSummary(Map<String, Object> invoiceData)
            throws AIServiceException {
        try {
            String systemPrompt = """
                Summarize the invoice data and extract key information.
                Focus on: vendor, invoice number, date, total amount, and line items.
                """;

            String userPrompt = "Invoice data:\n" + objectMapper.writeValueAsString(invoiceData);

            String response = generateResponse(systemPrompt, userPrompt);

            // Parse response to create InvoiceSummary
            String vendorName = extractValue(invoiceData, "vendor", "Unknown");
            String invoiceNumber = extractValue(invoiceData, "invoiceNumber", "");
            String invoiceDate = extractValue(invoiceData, "date", "");
            Double totalAmount = extractDoubleValue(invoiceData.get("amount"));

            return new InvoiceSummary(vendorName, invoiceNumber, invoiceDate,
                    totalAmount, new ArrayList<>(), invoiceData);

        } catch (Exception e) {
            log.error("Failed to generate invoice summary", e);
            throw new AIServiceException("Failed to generate invoice summary", e);
        }
    }

    // Private helper methods

    private String generateResponse(String systemPrompt, String userPrompt) throws AIServiceException {
        try {
            com.theokanning.openai.completion.chat.ChatMessage systemMessage =
                    new com.theokanning.openai.completion.chat.ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
            com.theokanning.openai.completion.chat.ChatMessage userMessage =
                    new com.theokanning.openai.completion.chat.ChatMessage(ChatMessageRole.USER.value(), userPrompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(Arrays.asList(systemMessage, userMessage))
                    .temperature(temperature)
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

        if (context.getDocumentId() != null) {
            sb.append("Document ID: ").append(context.getDocumentId()).append("\n");
        }

        if (context.getBatchId() != null) {
            sb.append("Batch ID: ").append(context.getBatchId()).append("\n");
        }

        if (context.getAdditionalContext() != null) {
            Object contextString = context.getAdditionalContext().get("contextString");
            if (contextString != null) {
                sb.append(contextString.toString());
            }
        }

        return sb.toString();
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    private String extractSummary(String response) {
        // Simple extraction - in production would use more sophisticated parsing
        int summaryStart = response.toLowerCase().indexOf("summary");
        if (summaryStart != -1) {
            int nextSection = response.indexOf("\n\n", summaryStart);
            if (nextSection != -1) {
                return response.substring(summaryStart, nextSection).trim();
            }
        }
        return response.length() > 200 ? response.substring(0, 200) + "..." : response;
    }

    private String extractRecommendations(String response) {
        List<String> recommendations = new ArrayList<>();

        // Look for numbered recommendations
        Pattern pattern = Pattern.compile("\\d+\\.\\s+(.+?)(?=\\d+\\.|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            recommendations.add(matcher.group(1).trim());
        }

        return String.join("\n", recommendations);
    }

    private String describeSchema(ExtractionSchema schema) {
        return schema.getFields().entrySet().stream()
                .map(entry -> {
                    FieldDefinition field = entry.getValue();
                    return String.format("%s (%s): %s%s",
                            field.getName(),
                            field.getType(),
                            field.getDescription(),
                            field.isRequired() ? " [REQUIRED]" : " [OPTIONAL]"
                    );
                })
                .collect(Collectors.joining("\n"));
    }

    private void validateExtractedData(Map<String, Object> data, ExtractionSchema schema)
            throws AIServiceException {
        for (Map.Entry<String, FieldDefinition> entry : schema.getFields().entrySet()) {
            String fieldName = entry.getKey();
            FieldDefinition fieldDef = entry.getValue();

            if (fieldDef.isRequired() && !data.containsKey(fieldName)) {
                throw new AIServiceException("Required field missing: " + fieldName);
            }
        }
    }

    private List<DocumentReference> extractDocumentReferences(String response, String context) {
        List<DocumentReference> references = new ArrayList<>();

        // Simple pattern matching for document references
        Pattern pattern = Pattern.compile("(?i)(document|invoice|file)\\s+([A-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            String refType = matcher.group(1);
            String refId = matcher.group(2);

            references.add(new DocumentReference(
                    0L, // Would need to resolve actual ID
                    refId,
                    null,
                    refType + " " + refId
            ));
        }

        return references;
    }

    private boolean evaluateRule(Object value, ValidationRule rule) {
        // Simple rule evaluation - would be expanded based on rule types
        if (rule.getRule().equals("required")) {
            return value != null && !value.toString().isEmpty();
        }

        if (rule.getRule().startsWith("min:")) {
            try {
                double minValue = Double.parseDouble(rule.getRule().substring(4));
                double actualValue = Double.parseDouble(value.toString());
                return actualValue >= minValue;
            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }

    private Double extractDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractValue(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}