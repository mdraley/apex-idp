package com.apex.idp.infrastructure.ai;

import com.apex.idp.domain.document.Document;

import java.util.List;
import java.util.Map;

/**
 * Service interface for AI-powered document analysis and chat interactions.
 * Abstracts the underlying AI implementation (OpenAI, Claude, etc.)
 */
public interface OpenAIService {

    /**
     * Analyzes a batch of documents and generates a summary with recommendations.
     *
     * @param documents List of documents to analyze
     * @return Analysis result containing summary and recommendations
     * @throws AIServiceException if analysis fails
     */
    AnalysisResult analyzeBatch(List<Document> documents) throws AIServiceException;

    /**
     * Analyzes a single document and extracts structured data.
     *
     * @param document The document to analyze
     * @param extractionSchema Optional schema defining what data to extract
     * @return Extracted data as a map
     * @throws AIServiceException if extraction fails
     */
    Map<String, Object> extractDocumentData(Document document,
                                            ExtractionSchema extractionSchema)
            throws AIServiceException;

    /**
     * Handles a chat interaction about a specific document or batch.
     *
     * @param message User's chat message
     * @param context Context object containing document/batch information
     * @param conversationHistory Previous messages in the conversation
     * @return AI-generated response
     * @throws AIServiceException if chat interaction fails
     */
    ChatResponse chat(String message, ChatContext context,
                      List<ChatMessage> conversationHistory) throws AIServiceException;

    /**
     * Classifies a document into predefined categories.
     *
     * @param document The document to classify
     * @param categories Available categories for classification
     * @return Classification result with confidence scores
     * @throws AIServiceException if classification fails
     */
    ClassificationResult classifyDocument(Document document, List<String> categories)
            throws AIServiceException;

    /**
     * Validates extracted data against business rules using AI.
     *
     * @param extractedData The data to validate
     * @param validationRules Business rules for validation
     * @return Validation result with any issues found
     * @throws AIServiceException if validation fails
     */
    ValidationResult validateExtractedData(Map<String, Object> extractedData,
                                           List<ValidationRule> validationRules)
            throws AIServiceException;

    /**
     * Generates a structured summary of invoice data.
     *
     * @param invoiceData Extracted invoice information
     * @return Formatted invoice summary
     * @throws AIServiceException if summary generation fails
     */
    InvoiceSummary generateInvoiceSummary(Map<String, Object> invoiceData)
            throws AIServiceException;

    // Result classes

    class AnalysisResult {
        private final String summary;
        private final String recommendations;
        private final Map<String, Object> metadata;

        public AnalysisResult(String summary, String recommendations,
                              Map<String, Object> metadata) {
            this.summary = summary;
            this.recommendations = recommendations;
            this.metadata = metadata;
        }

        // Getters
        public String getSummary() { return summary; }
        public String getRecommendations() { return recommendations; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    class ChatResponse {
        private final String message;
        private final List<DocumentReference> references;
        private final Map<String, Object> metadata;

        public ChatResponse(String message, List<DocumentReference> references,
                            Map<String, Object> metadata) {
            this.message = message;
            this.references = references;
            this.metadata = metadata;
        }

        // Getters
        public String getMessage() { return message; }
        public List<DocumentReference> getReferences() { return references; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    class ClassificationResult {
        private final String primaryCategory;
        private final Map<String, Double> confidenceScores;

        public ClassificationResult(String primaryCategory,
                                    Map<String, Double> confidenceScores) {
            this.primaryCategory = primaryCategory;
            this.confidenceScores = confidenceScores;
        }

        // Getters
        public String getPrimaryCategory() { return primaryCategory; }
        public Map<String, Double> getConfidenceScores() { return confidenceScores; }
    }

    class ValidationResult {
        private final boolean isValid;
        private final List<ValidationIssue> issues;

        public ValidationResult(boolean isValid, List<ValidationIssue> issues) {
            this.isValid = isValid;
            this.issues = issues;
        }

        // Getters
        public boolean isValid() { return isValid; }
        public List<ValidationIssue> getIssues() { return issues; }
    }

    class InvoiceSummary {
        private final String vendorName;
        private final String invoiceNumber;
        private final String invoiceDate;
        private final Double totalAmount;
        private final List<LineItem> lineItems;
        private final Map<String, Object> additionalData;

        public InvoiceSummary(String vendorName, String invoiceNumber,
                              String invoiceDate, Double totalAmount,
                              List<LineItem> lineItems, Map<String, Object> additionalData) {
            this.vendorName = vendorName;
            this.invoiceNumber = invoiceNumber;
            this.invoiceDate = invoiceDate;
            this.totalAmount = totalAmount;
            this.lineItems = lineItems;
            this.additionalData = additionalData;
        }

        // Getters
        public String getVendorName() { return vendorName; }
        public String getInvoiceNumber() { return invoiceNumber; }
        public String getInvoiceDate() { return invoiceDate; }
        public Double getTotalAmount() { return totalAmount; }
        public List<LineItem> getLineItems() { return lineItems; }
        public Map<String, Object> getAdditionalData() { return additionalData; }
    }

    // Supporting classes

    class ExtractionSchema {
        private final Map<String, FieldDefinition> fields;

        public ExtractionSchema(Map<String, FieldDefinition> fields) {
            this.fields = fields;
        }

        public Map<String, FieldDefinition> getFields() { return fields; }
    }

    class FieldDefinition {
        private final String name;
        private final String type;
        private final String description;
        private final boolean required;

        public FieldDefinition(String name, String type, String description,
                               boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
        }

        // Getters
        public String getName() { return name; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
    }

    class ChatContext {
        private final Long documentId;
        private final Long batchId;
        private final Map<String, Object> additionalContext;

        public ChatContext(Long documentId, Long batchId,
                           Map<String, Object> additionalContext) {
            this.documentId = documentId;
            this.batchId = batchId;
            this.additionalContext = additionalContext;
        }

        // Getters
        public Long getDocumentId() { return documentId; }
        public Long getBatchId() { return batchId; }
        public Map<String, Object> getAdditionalContext() { return additionalContext; }
    }

    class ChatMessage {
        private final String role; // "user" or "assistant"
        private final String content;
        private final Long timestamp;

        public ChatMessage(String role, String content, Long timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }

        // Getters
        public String getRole() { return role; }
        public String getContent() { return content; }
        public Long getTimestamp() { return timestamp; }
    }

    class DocumentReference {
        private final Long documentId;
        private final String documentName;
        private final Integer pageNumber;
        private final String snippet;

        public DocumentReference(Long documentId, String documentName,
                                 Integer pageNumber, String snippet) {
            this.documentId = documentId;
            this.documentName = documentName;
            this.pageNumber = pageNumber;
            this.snippet = snippet;
        }

        // Getters
        public Long getDocumentId() { return documentId; }
        public String getDocumentName() { return documentName; }
        public Integer getPageNumber() { return pageNumber; }
        public String getSnippet() { return snippet; }
    }

    class ValidationRule {
        private final String fieldName;
        private final String rule;
        private final String errorMessage;

        public ValidationRule(String fieldName, String rule, String errorMessage) {
            this.fieldName = fieldName;
            this.rule = rule;
            this.errorMessage = errorMessage;
        }

        // Getters
        public String getFieldName() { return fieldName; }
        public String getRule() { return rule; }
        public String getErrorMessage() { return errorMessage; }
    }

    class ValidationIssue {
        private final String fieldName;
        private final String issue;
        private final String severity; // "error", "warning", "info"

        public ValidationIssue(String fieldName, String issue, String severity) {
            this.fieldName = fieldName;
            this.issue = issue;
            this.severity = severity;
        }

        // Getters
        public String getFieldName() { return fieldName; }
        public String getIssue() { return issue; }
        public String getSeverity() { return severity; }
    }

    class LineItem {
        private final String description;
        private final Integer quantity;
        private final Double unitPrice;
        private final Double totalPrice;

        public LineItem(String description, Integer quantity,
                        Double unitPrice, Double totalPrice) {
            this.description = description;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
        }

        // Getters
        public String getDescription() { return description; }
        public Integer getQuantity() { return quantity; }
        public Double getUnitPrice() { return unitPrice; }
        public Double getTotalPrice() { return totalPrice; }
    }

    /**
     * AI service exception for handling AI-related errors.
     */
    class AIServiceException extends Exception {
        public AIServiceException(String message) {
            super(message);
        }

        public AIServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}