package com.apex.idp.infrastructure.ai;

import com.apex.idp.domain.document.Document;
import com.apex.idp.infrastructure.ai.model.*;

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
     * @param message             User's message
     * @param context             Chat context containing relevant information
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
     * @param document   Document to classify
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
     * @param document         Document to process
     * @param extractionSchema Schema defining what to extract
     * @return Extracted data matching the schema
     * @throws AIServiceException if extraction fails
     */
    Map<String, Object> extractStructuredData(Document document, Map<String, String> extractionSchema)
            throws AIServiceException;

    /**
     * Creates a ChatContext object for the chat API.
     */
    ChatContext createChatContext(Long documentId, Long batchId,
                                  Map<String, Object> additionalContext,
                                  String userId);

    /**
     * Creates a list of chat messages for conversation history.
     */
    List<ChatMessage> createChatHistory(String role, String content, long timestamp);
}