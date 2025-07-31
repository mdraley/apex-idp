package com.apex.idp.infrastructure.ai;

import com.apex.idp.domain.document.Document;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Interface for OpenAI service operations.
 * Defines contract for AI-powered document analysis and chat interactions.
 */
public interface OpenAIService {

    /**
     * Analyzes a batch of documents using OpenAI.
     *
     * @param documents List of documents to analyze
     * @return Analysis result containing summary and recommendations
     * @throws AIServiceException if analysis fails
     */
    AnalysisResult analyzeBatch(List<Document> documents) throws AIServiceException;

    /**
     * Handles chat interaction with context.
     *
     * @param message User's message
     * @param context Chat context containing relevant information
     * @param conversationHistory Previous messages in the conversation
     * @return Chat response with AI's reply and metadata
     * @throws AIServiceException if chat processing fails
     */
    ChatResponse chat(String message, ChatContext context, List<ChatMessage> conversationHistory)
            throws AIServiceException;

    /**
     * Extracts structured invoice data from text.
     *
     * @param ocrText OCR extracted text
     * @return Structured invoice data
     * @throws AIServiceException if extraction fails
     */
    InvoiceExtractionResult extractInvoiceData(String ocrText) throws AIServiceException;

    /**
     * Classifies a document into predefined categories.
     *
     * @param document Document to classify
     * @param categories List of possible categories
     * @return Classification result with confidence
     * @throws AIServiceException if classification fails
     */
    ClassificationResult classifyDocument(Document document, List<String> categories)
            throws AIServiceException;

    /**
     * Generates a summary for invoice data.
     *
     * @param invoiceData Map of invoice data
     * @return Invoice summary
     * @throws AIServiceException if summary generation fails
     */
    InvoiceSummary generateInvoiceSummary(Map<String, Object> invoiceData)
            throws AIServiceException;

    /**
     * Extracts structured data from document based on schema.
     *
     * @param document Document to process
     * @param extractionSchema Schema defining what to extract
     * @return Extracted data matching the schema
     * @throws AIServiceException if extraction fails
     */
    Map<String, Object> extractStructuredData(Document document, Map<String, String> extractionSchema)
            throws AIServiceException;

    /**
     * Result classes
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class AnalysisResult {
        private String summary;
        private List<String> recommendations;
        private Map<String, Object> metadata;
        private Double confidenceScore;
        private List<String> keyInsights;
        private List<String> riskFactors;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class InvoiceExtractionResult {
        private String invoiceNumber;
        private String invoiceDate;
        private String dueDate;
        private String vendorName;
        private String vendorAddress;
        private String totalAmount;
        private String currency;
        private List<LineItemData> lineItems;
        private Map<String, String> additionalFields;
        private Double extractionConfidence;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class LineItemData {
        private String description;
        private Integer quantity;
        private String unitPrice;
        private String totalPrice;
        private String itemCode;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ChatResponse {
        private String message;
        private List<DocumentReference> references;
        private Map<String, Object> metadata;
        private String conversationId;
        private Long timestamp;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ChatContext {
        private Long documentId;
        private Long batchId;
        private Map<String, Object> additionalContext;
        private String userId;
        private List<String> relevantDocumentIds;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ChatMessage {
        private String role; // "user" or "assistant"
        private String content;
        private Long timestamp;
        private Map<String, Object> metadata;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class DocumentReference {
        private String documentId;
        private String fileName;
        private String excerpt;
        private Integer pageNumber;
        private Double relevanceScore;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ClassificationResult {
        private String category;
        private Double confidence;
        private Map<String, Double> categoryScores;
        private String reasoning;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class InvoiceSummary {
        private String vendorName;
        private String invoiceNumber;
        private String date;
        private Double totalAmount;
        private List<String> keyItems;
        private Map<String, Object> metadata;
    }

    /**
     * Custom exception for AI service errors
     */
    class AIServiceException extends Exception {
        private final String errorCode;
        private final Map<String, Object> details;

        public AIServiceException(String message) {
            super(message);
            this.errorCode = "AI_ERROR";
            this.details = Map.of();
        }

        public AIServiceException(String message, Throwable cause) {
            super(message, cause);
            this.errorCode = "AI_ERROR";
            this.details = Map.of();
        }

        public AIServiceException(String message, String errorCode, Map<String, Object> details) {
            super(message);
            this.errorCode = errorCode;
            this.details = details;
        }

        public AIServiceException(String message, String errorCode, Map<String, Object> details, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
            this.details = details;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }
}