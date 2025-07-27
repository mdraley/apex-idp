package com.apex.idp.infrastructure.ai;

import com.apex.idp.domain.document.Document;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenAI implementation of the AI service interface.
 * Provides document analysis, data extraction, and chat capabilities using GPT models.
 */
@Service
public class OpenAIServiceImpl implements OpenAIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIServiceImpl.class);

    private OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model:gpt-4}")
    private String model;

    @Value("${openai.temperature:0.3}")
    private Double temperature;

    @Value("${openai.max-tokens:2000}")
    private Integer maxTokens;

    @Value("${openai.timeout:60}")
    private Integer timeoutSeconds;

    public OpenAIServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(timeoutSeconds));
        log.info("OpenAI service initialized with model: {}", model);
    }

    @Override
    public AnalysisResult analyzeBatch(List<Document> documents) throws AIServiceException {
        try {
            // Prepare document summaries for analysis
            String documentSummaries = documents.stream()
                    .map(doc -> String.format("Document: %s\nType: %s\nContent: %s\n",
                            doc.getFileName(),
                            doc.getDocumentType(),
                            truncateContent(doc.getOcrText(), 500)))
                    .collect(Collectors.joining("\n---\n"));

            String systemPrompt = """
                You are an AI assistant specialized in analyzing accounts payable documents.
                Analyze the provided batch of documents and provide:
                1. A concise summary of the batch contents
                2. Actionable recommendations for the AP team
                3. Key insights about vendors, amounts, and dates
                
                Format your response as JSON with the following structure:
                {
                  "summary": "Brief overview of the batch",
                  "recommendations": "Actionable next steps",
                  "insights": {
                    "totalAmount": 0.0,
                    "vendorCount": 0,
                    "dateRange": "start to end",
                    "documentTypes": ["type1", "type2"]
                  }
                }
                """;

            String userPrompt = "Analyze this batch of documents:\n\n" + documentSummaries;

            ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
            ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), userPrompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(Arrays.asList(systemMessage, userMessage))
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String responseContent = result.getChoices().get(0).getMessage().getContent();

            // Parse JSON response
            Map<String, Object> parsedResponse = objectMapper.readValue(responseContent, Map.class);

            String summary = (String) parsedResponse.get("summary");
            String recommendations = (String) parsedResponse.get("recommendations");
            Map<String, Object> insights = (Map<String, Object>) parsedResponse.get("insights");

            log.info("Successfully analyzed batch of {} documents", documents.size());

            return new AnalysisResult(summary, recommendations, insights);

        } catch (Exception e) {
            log.error("Failed to analyze batch", e);
            throw new AIServiceException("Failed to analyze batch of documents", e);
        }
    }

    @Override
    public Map<String, Object> extractDocumentData(Document document,
                                                   ExtractionSchema extractionSchema)
            throws AIServiceException {
        try {
            String schemaDescription = extractionSchema != null
                    ? generateSchemaDescription(extractionSchema)
                    : getDefaultInvoiceSchema();

            String systemPrompt = String.format("""
                You are an AI assistant specialized in extracting structured data from documents.
                Extract the following information from the document and return it as JSON:
                
                %s
                
                If a field cannot be found, set its value to null.
                Ensure all monetary values are numbers, not strings.
                Dates should be in ISO format (YYYY-MM-DD).
                """, schemaDescription);

            String userPrompt = "Extract data from this document:\n\n" +
                    truncateContent(document.getOcrText(), 2000);

            ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
            ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), userPrompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(Arrays.asList(systemMessage, userMessage))
                    .temperature(0.1) // Lower temperature for more consistent extraction
                    .maxTokens(maxTokens)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String responseContent = result.getChoices().get(0).getMessage().getContent();

            Map<String, Object> extractedData = objectMapper.readValue(responseContent, Map.class);

            log.debug("Successfully extracted data from document: {}", document.getFileName());

            return extractedData;

        } catch (Exception e) {
            log.error("Failed to extract data from document: {}", document.getFileName(), e);
            throw new AIServiceException("Failed to extract document data", e);
        }
    }

    @Override
    public ChatResponse chat(String message, ChatContext context,
                             List<ChatMessage> conversationHistory) throws AIServiceException {
        try {
            // Build conversation context
            List<ChatMessage> messages = new ArrayList<>();

            // System message with context
            String systemPrompt = buildChatSystemPrompt(context);
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));

            // Add conversation history
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                for (ChatMessage historyMsg : conversationHistory) {
                    messages.add(new ChatMessage(
                            historyMsg.getRole().equals("user")
                                    ? ChatMessageRole.USER.value()
                                    : ChatMessageRole.ASSISTANT.value(),
                            historyMsg.getContent()
                    ));
                }
            }

            // Add current user message
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), message));

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String responseContent = result.getChoices().get(0).getMessage().getContent();

            // Extract any document references from the response
            List<DocumentReference> references = extractDocumentReferences(responseContent, context);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("model", model);
            metadata.put("timestamp", System.currentTimeMillis());

            log.debug("Chat response generated for message: {}",
                    message.substring(0, Math.min(message.length(), 50)) + "...");

            return new ChatResponse(responseContent, references, metadata);

        } catch (Exception e) {
            log.error("Failed to process chat message", e);
            throw new AIServiceException("Failed to process chat message", e);
        }
    }

    @Override
    @Cacheable(value = "documentClassifications", key = "#document.id")
    public ClassificationResult classifyDocument(Document document, List<String> categories)
            throws AIServiceException {
        try {
            String categoriesList = String.join(", ", categories);

            String systemPrompt = String.format("""
                You are an AI assistant specialized in document classification.
                Classify the given document into one of these categories: %s
                
                Return your response as JSON with the following structure:
                {
                  "primaryCategory": "selected category",
                  "confidenceScores": {
                    "category1": 0.95,
                    "category2": 0.03,
                    ...
                  }
                }
                
                Confidence scores should sum to 1.0.
                """, categoriesList);

            String userPrompt = "Classify this document:\n\n" +
                    truncateContent(document.getOcrText(), 1000);

            ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
            ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), userPrompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(Arrays.asList(systemMessage, userMessage))
                    .temperature(0.1)
                    .maxTokens(500)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String responseContent = result.getChoices().get(0).getMessage().getContent();

            Map<String, Object> parsedResponse = objectMapper.readValue(responseContent, Map.class);
            String primaryCategory = (String) parsedResponse.get("primaryCategory");
            Map<String, Double> confidenceScores = (Map<String, Double>) parsedResponse.get("confidenceScores");

            log.debug("Document classified as: {} with confidence: {}",
                    primaryCategory, confidenceScores.get(primaryCategory));

            return new ClassificationResult(primaryCategory, confidenceScores);

        } catch (Exception e) {
            log.error("Failed to classify document", e);
            throw new AIServiceException("Failed to classify document", e);
        }
    }

    @Override
    public ValidationResult validateExtractedData(Map<String, Object> extractedData,
                                                  List<ValidationRule> validationRules)
            throws AIServiceException {
        try {
            String rulesDescription = validationRules.stream()
                    .map(rule -> String.format("- %s: %s", rule.getFieldName(), rule.getRule()))
                    .collect(Collectors.joining("\n"));

            String dataJson = objectMapper.writeValueAsString(extractedData);

            String systemPrompt = """
                You are an AI assistant specialized in data validation for accounts payable.
                Validate the extracted data against the provided business rules.
                
                Return your response as JSON with the following structure:
                {
                  "isValid": true/false,
                  "issues": [
                    {
                      "fieldName": "field",
                      "issue": "description of the issue",
                      "severity": "error|warning|info"
                    }
                  ]
                }
                """;

            String userPrompt = String.format("""
                Validate this data:
                %s
                
                Against these rules:
                %s
                """, dataJson, rulesDescription);

            ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
            ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), userPrompt);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(Arrays.asList(systemMessage, userMessage))
                    .temperature(0.1)
                    .maxTokens(1000)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            String responseContent = result.getChoices().get(0).getMessage().getContent();

            Map<String, Object> parsedResponse = objectMapper.readValue(responseContent, Map.class);
            boolean isValid = (boolean) parsedResponse.get("isValid");
            List<Map<String, String>> issuesList = (List<Map<String, String>>) parsedResponse.get("issues");

            List<ValidationIssue> issues = issuesList.stream()
                    .map(issueMap -> new ValidationIssue(
                            issueMap.get("fieldName"),
                            issueMap.get("issue"),
                            issueMap.get("severity")
                    ))
                    .collect(Collectors.toList());

            log.debug("Validation completed. Valid: {}, Issues found: {}", isValid, issues.size());

            return new ValidationResult(isValid, issues);

        } catch (Exception e) {
            log.error("Failed to validate extracted data", e);
            throw new AIServiceException("Failed to validate extracted data", e);
        }
    }

    @Override
    public InvoiceSummary generateInvoiceSummary(Map<String, Object> invoiceData)
            throws AIServiceException {
        try {
            // Extract key invoice fields
            String vendorName = (String) invoiceData.get("vendorName");
            String invoiceNumber = (String) invoiceData.get("invoiceNumber");
            String invoiceDate = (String) invoiceData.get("invoiceDate");
            Double totalAmount = extractDoubleValue(invoiceData.get("totalAmount"));

            // Extract line items if present
            List<LineItem> lineItems = extractLineItems(invoiceData);

            // Additional data
            Map<String, Object> additionalData = new HashMap<>(invoiceData);
            additionalData.remove("vendorName");
            additionalData.remove("invoiceNumber");
            additionalData.remove("invoiceDate");
            additionalData.remove("totalAmount");
            additionalData.remove("lineItems");

            log.debug("Generated invoice summary for invoice: {}", invoiceNumber);

            return new InvoiceSummary(
                    vendorName,
                    invoiceNumber,
                    invoiceDate,
                    totalAmount,
                    lineItems,
                    additionalData
            );

        } catch (Exception e) {
            log.error("Failed to generate invoice summary", e);
            throw new AIServiceException("Failed to generate invoice summary", e);
        }
    }

    // Helper methods

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        return content.length() > maxLength
                ? content.substring(0, maxLength) + "..."
                : content;
    }

    private String generateSchemaDescription(ExtractionSchema schema) {
        return schema.getFields().entrySet().stream()
                .map(entry -> {
                    FieldDefinition field = entry.getValue();
                    return String.format("%s (%s): %s %s",
                            field.getName(),
                            field.getType(),
                            field.getDescription(),
                            field.isRequired() ? "[REQUIRED]" : "[OPTIONAL]"
                    );
                })
                .collect(Collectors.joining("\n"));
    }

    private String getDefaultInvoiceSchema() {
        return """
            vendorName (string): Name of the vendor/supplier [REQUIRED]
            invoiceNumber (string): Invoice or document number [REQUIRED]
            invoiceDate (string): Date of the invoice in ISO format [REQUIRED]
            dueDate (string): Payment due date in ISO format [OPTIONAL]
            totalAmount (number): Total amount of the invoice [REQUIRED]
            taxAmount (number): Tax amount if applicable [OPTIONAL]
            currency (string): Currency code (e.g., USD) [OPTIONAL]
            paymentTerms (string): Payment terms (e.g., Net 30) [OPTIONAL]
            lineItems (array): Array of line items with description, quantity, unitPrice, totalPrice [OPTIONAL]
            """;
    }

    private String buildChatSystemPrompt(ChatContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI assistant helping with accounts payable document analysis. ");
        prompt.append("You have access to document and batch information. ");
        prompt.append("Provide helpful, accurate responses based on the context. ");
        prompt.append("When referencing specific information, cite the source document.\n\n");

        if (context.getDocumentId() != null) {
            prompt.append("Current document ID: ").append(context.getDocumentId()).append("\n");
        }
        if (context.getBatchId() != null) {
            prompt.append("Current batch ID: ").append(context.getBatchId()).append("\n");
        }
        if (context.getAdditionalContext() != null && !context.getAdditionalContext().isEmpty()) {
            prompt.append("Additional context: ")
                    .append(objectMapper.writeValueAsString(context.getAdditionalContext()))
                    .append("\n");
        }

        return prompt.toString();
    }

    private List<DocumentReference> extractDocumentReferences(String response, ChatContext context) {
        // Simple implementation - could be enhanced with more sophisticated parsing
        List<DocumentReference> references = new ArrayList<>();

        if (context.getDocumentId() != null && response.toLowerCase().contains("document")) {
            references.add(new DocumentReference(
                    context.getDocumentId(),
                    "Current Document",
                    null,
                    null
            ));
        }

        return references;
    }

    private Double extractDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private List<LineItem> extractLineItems(Map<String, Object> invoiceData) {
        Object lineItemsObj = invoiceData.get("lineItems");
        if (!(lineItemsObj instanceof List)) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> lineItemsList = (List<Map<String, Object>>) lineItemsObj;

        return lineItemsList.stream()
                .map(item -> new LineItem(
                        (String) item.get("description"),
                        item.get("quantity") instanceof Number ? ((Number) item.get("quantity")).intValue() : null,
                        extractDoubleValue(item.get("unitPrice")),
                        extractDoubleValue(item.get("totalPrice"))
                ))
                .collect(Collectors.toList());
    }
}