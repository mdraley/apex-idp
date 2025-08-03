package com.apex.idp.infrastructure.ai;

import com.apex.idp.config.OpenAIProperties;
import com.apex.idp.domain.document.Document;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for interacting with OpenAI APIs for document analysis and chat.
 */
@Slf4j
@Service
public class OpenAIServiceImpl implements OpenAIService {

    private final OpenAIProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OpenAiService openAiService;

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

    public OpenAIServiceImpl(OpenAIProperties properties,
                             WebClient.Builder webClientBuilder,
                             RestTemplate restTemplate,
                             ObjectMapper objectMapper,
                             @Value("${openai.api.key}") String apiKey) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public AnalysisResult analyzeBatch(List<Document> documents) throws AIServiceException {
        log.info("Analyzing batch with {} documents", documents.size());
        return executeWithErrorHandling("batch analysis", () -> {
            String content = extractContentFromDocuments(documents);
            return createMockAnalysisResult(documents.size());
        });
    }

    @Override
    public ChatResponse chat(String message, ChatContext context, List<ChatMessage> conversationHistory)
            throws AIServiceException {
        log.debug("Processing chat message: {}", message);
        return executeWithErrorHandling("chat processing", () -> createMockChatResponse(message));
    }

    @Override
    public InvoiceExtractionResult extractInvoiceData(String ocrText) throws AIServiceException {
        log.debug("Extracting invoice data from OCR text");
        return executeWithErrorHandling("invoice data extraction", this::createMockInvoiceExtractionResult);
    }

    @Override
    public ClassificationResult classifyDocument(Document document, List<String> categories)
            throws AIServiceException {
        log.debug("Classifying document: {}", document.getId());
        return executeWithErrorHandling("document classification", this::createMockClassificationResult);
    }

    @Override
    public InvoiceSummary generateInvoiceSummary(Map<String, Object> invoiceData)
            throws AIServiceException {
        log.debug("Generating invoice summary");
        return executeWithErrorHandling("invoice summary generation", this::createMockInvoiceSummary);
    }

    @Override
    public Map<String, Object> extractStructuredData(Document document, Map<String, String> extractionSchema)
            throws AIServiceException {
        log.debug("Extracting structured data from document: {}", document.getId());
        return executeWithErrorHandling("structured data extraction", () -> {
            Map<String, Object> extractedData = new HashMap<>();
            extractionSchema.forEach((key, value) -> extractedData.put(key, "Extracted " + key));
            return extractedData;
        });
    }

    @Override
    public ChatContext createChatContext(Long documentId, Long batchId,
                                         Map<String, Object> additionalContext,
                                         String userId) {
        List<String> relevantDocumentIds = null;
        if (additionalContext != null && additionalContext.containsKey("relevantDocumentIds")) {
            relevantDocumentIds = (List<String>) additionalContext.get("relevantDocumentIds");
        }
        return new ChatContext(documentId, batchId, additionalContext, userId, relevantDocumentIds);
    }

    @Override
    public List<ChatMessage> createChatHistory(String role, String content, long timestamp) {
        return List.of(ChatMessage.builder()
                .role(role)
                .content(content)
                .timestamp(timestamp)
                .build());
    }

    private <T> T executeWithErrorHandling(String operation, ThrowableSupplier<T> supplier) throws AIServiceException {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.error("Error during {}", operation, e);
            throw new AIServiceException("Failed to " + operation.replace(" ", " "), e);
        }
    }

    private String extractContentFromDocuments(List<Document> documents) {
        StringBuilder contentBuilder = new StringBuilder();
        for (Document doc : documents) {
            if (doc.getExtractedText() != null) {
                contentBuilder.append(doc.getFileName()).append(": ");
                contentBuilder.append(doc.getExtractedText(), 0,
                        Math.min(500, doc.getExtractedText().length()));
                contentBuilder.append("\n\n");
            }
        }
        String content = contentBuilder.toString();
        if (content.isEmpty()) {
            throw new IllegalStateException("No extractable content in documents");
        }
        return content;
    }

    private AnalysisResult createMockAnalysisResult(int documentCount) {
        return AnalysisResult.builder()
                .summary("This is a batch of " + documentCount + " documents, including invoices and supporting documents.")
                .recommendations(List.of(
                        "Verify vendor information for consistency",
                        "Cross-check invoice amounts against purchase orders",
                        "Review payment terms for any discrepancies"))
                .metadata(createTimestampMetadata("documentCount", documentCount, "confidence", 0.85))
                .confidenceScore(0.85)
                .keyInsights(List.of("Key insight 1", "Key insight 2"))
                .riskFactors(List.of("Risk factor 1", "Risk factor 2"))
                .build();
    }

    private ChatResponse createMockChatResponse(String message) {
        return ChatResponse.builder()
                .message("This is a mock response to: " + message)
                .references(createMockDocumentReferences())
                .metadata(createTimestampMetadata("responseTime", "120ms"))
                .conversationId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private InvoiceExtractionResult createMockInvoiceExtractionResult() {
        return InvoiceExtractionResult.builder()
                .invoiceNumber("INV-2023-001")
                .invoiceDate("2023-07-15")
                .dueDate("2023-08-15")
                .vendorName("Acme Supplies")
                .vendorAddress("123 Business St, Commerce City")
                .totalAmount("1250.00")
                .currency("USD")
                .lineItems(createMockLineItems())
                .additionalFields(Map.of("taxId", "TX-987654321", "poNumber", "PO-2023-123"))
                .extractionConfidence(0.92)
                .build();
    }

    private ClassificationResult createMockClassificationResult() {
        Map<String, Double> categoryScores = new HashMap<>();
        categoryScores.put("Invoice", 0.85);
        categoryScores.put("Receipt", 0.15);
        categoryScores.put("Contract", 0.05);

        return ClassificationResult.builder()
                .category("Invoice")
                .confidence(0.85)
                .categoryScores(categoryScores)
                .reasoning("Document contains invoice number, due date, and line items typical of invoices.")
                .build();
    }

    private InvoiceSummary createMockInvoiceSummary() {
        return InvoiceSummary.builder()
                .vendorName("Acme Supplies")
                .invoiceNumber("INV-2023-001")
                .date("2023-07-15")
                .totalAmount(1250.00)
                .keyItems(List.of("Office Supplies", "Software License"))
                .metadata(Map.of("currency", "USD", "paymentTerms", "Net 30"))
                .build();
    }

    private List<DocumentReference> createMockDocumentReferences() {
        return List.of(
                DocumentReference.builder()
                        .documentId("doc1")
                        .fileName("Document 1")
                        .excerpt("Excerpt from document 1")
                        .pageNumber(1)
                        .relevanceScore(0.9)
                        .build(),
                DocumentReference.builder()
                        .documentId("doc2")
                        .fileName("Document 2")
                        .excerpt("Excerpt from document 2")
                        .pageNumber(2)
                        .relevanceScore(0.8)
                        .build());
    }

    private List<LineItemData> createMockLineItems() {
        return List.of(
                LineItemData.builder()
                        .description("Office Supplies")
                        .quantity(5)
                        .unitPrice("50.00")
                        .totalPrice("250.00")
                        .itemCode("OS-001")
                        .build(),
                LineItemData.builder()
                        .description("Software License")
                        .quantity(1)
                        .unitPrice("1000.00")
                        .totalPrice("1000.00")
                        .itemCode("SL-002")
                        .build());
    }

    private Map<String, Object> createTimestampMetadata(Object... keyValuePairs) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("processedAt", new Date().toString());

        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i + 1 < keyValuePairs.length) {
                metadata.put(keyValuePairs[i].toString(), keyValuePairs[i + 1]);
            }
        }
        return metadata;
    }

    @FunctionalInterface
    private interface ThrowableSupplier<T> {
        T get() throws Exception;
    }
}