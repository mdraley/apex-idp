package com.apex.idp.application.service;

import com.apex.idp.domain.analysis.Analysis;
import com.apex.idp.domain.analysis.AnalysisRepository;
import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.batch.BatchRepository;
import com.apex.idp.domain.document.Document;
import com.apex.idp.infrastructure.ai.OpenAIService;
import com.apex.idp.infrastructure.websocket.WebSocketNotificationService;
import com.apex.idp.interfaces.dto.AnalysisDTO;
import com.apex.idp.interfaces.dto.ChatRequestDTO;
import com.apex.idp.interfaces.dto.ChatResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final BatchRepository batchRepository;
    private final OpenAIService openAIService;
    private final WebSocketNotificationService notificationService;

    /**
     * Performs AI analysis on a batch of documents.
     */
    @Transactional
    public Analysis analyzeBatch(String batchId) {
        log.info("Analyzing batch: {}", batchId);

        Batch batch = batchRepository.findByIdWithDocuments(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        try {
            // Prepare documents for analysis
            List<Document> documents = batch.getDocuments();
            if (documents.isEmpty()) {
                throw new IllegalStateException("No documents to analyze in batch: " + batchId);
            }

            // Perform AI analysis
            OpenAIService.AnalysisResult result = openAIService.analyzeBatch(documents);

            // Create and save analysis
            Analysis analysis = Analysis.create(batch, result.getSummary(), result.getRecommendations());
            analysis.setMetadata(result.getMetadata());

            Analysis savedAnalysis = analysisRepository.save(analysis);

            // Update batch status
            batch.completeAnalysis();
            batchRepository.save(batch);

            // Send WebSocket notification
            notificationService.notifyBatchStatusUpdate(batchId, "ANALYSIS_COMPLETED");

            log.info("Batch analysis completed for: {}", batchId);
            return savedAnalysis;

        } catch (Exception e) {
            log.error("Error analyzing batch: {}", batchId, e);
            batch.failAnalysis(e.getMessage());
            batchRepository.save(batch);
            throw new RuntimeException("Failed to analyze batch", e);
        }
    }

    /**
     * Handles chat interaction with AI for a specific batch.
     */
    @Transactional(readOnly = true)
    public ChatResponseDTO chat(String batchId, ChatRequestDTO request) {
        log.debug("Processing chat request for batch: {}", batchId);

        // Validate input
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Chat message cannot be empty");
        }

        Batch batch = batchRepository.findByIdWithDocuments(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        try {
            // Build context for AI
            String contextString = buildContext(batch, request.getInvoiceId());

            // FIX: Create proper ChatContext object
            Long batchIdLong = null;
            try {
                batchIdLong = Long.parseLong(batchId);
            } catch (NumberFormatException e) {
                // If batchId is not numeric, leave as null
            }

            Long invoiceIdLong = null;
            if (request.getInvoiceId() != null) {
                try {
                    invoiceIdLong = Long.parseLong(request.getInvoiceId());
                } catch (NumberFormatException e) {
                    // If invoiceId is not numeric, leave as null
                }
            }

            Map<String, Object> additionalContext = new HashMap<>();
            additionalContext.put("contextString", contextString);
            additionalContext.put("batchIdStr", batchId);
            additionalContext.put("invoiceIdStr", request.getInvoiceId());

            OpenAIService.ChatContext chatContext = new OpenAIService.ChatContext(
                    invoiceIdLong,  // documentId (using invoiceId as proxy)
                    batchIdLong,    // batchId
                    additionalContext
            );

            // Generate AI response
            // Create conversation history list - the third parameter should be List<ChatMessage>, not Map
            List<OpenAIService.ChatMessage> conversationHistory = List.of(
                new OpenAIService.ChatMessage(
                    "user", 
                    request.getMessage(), 
                    System.currentTimeMillis()
                )
            );

            OpenAIService.ChatResponse aiResponse = openAIService.chat(
                    request.getMessage(),
                    chatContext,
                    conversationHistory
            );

            // Build response DTO
            return ChatResponseDTO.builder()
                    .batchId(batchId)
                    .message(request.getMessage())
                    .response(aiResponse.getMessage())
                    .references(aiResponse.getReferences())
                    .metadata(aiResponse.getMetadata())
                    .build();

        } catch (Exception e) {
            log.error("Error processing chat for batch: {}", batchId, e);
            throw new RuntimeException("Failed to process chat request", e);
        }
    }

    /**
     * Retrieves analysis for a batch.
     */
    @Transactional(readOnly = true)
    public Optional<AnalysisDTO> getAnalysisByBatchId(String batchId) {
        return analysisRepository.findByBatchId(batchId)
                .map(this::convertToDTO);
    }

    /**
     * Builds comprehensive context for AI interaction.
     */
    private String buildContext(Batch batch, String invoiceId) {
        StringBuilder context = new StringBuilder();
        context.append("Batch ID: ").append(batch.getId()).append("\n");
        context.append("Batch Name: ").append(batch.getName()).append("\n");
        context.append("Document Count: ").append(batch.getDocumentCount()).append("\n\n");

        // Add document summaries
        for (Document doc : batch.getDocuments()) {
            context.append("Document: ").append(doc.getFileName()).append("\n");
            if (doc.getExtractedText() != null) {
                context.append("Content Preview: ")
                        .append(doc.getExtractedText().substring(0,
                                Math.min(200, doc.getExtractedText().length())))
                        .append("...\n");
            }
            context.append("\n");
        }

        // Add specific invoice context if provided
        if (invoiceId != null) {
            context.append("Focusing on Invoice ID: ").append(invoiceId).append("\n");
        }

        return context.toString();
    }

    /**
     * Retrieves all analyses with pagination.
     */
    @Transactional(readOnly = true)
    public List<AnalysisDTO> getAllAnalyses(int page, int size) {
        // Implementation would include pagination
        return analysisRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private AnalysisDTO convertToDTO(Analysis analysis) {
        Map<String, Object> metadata = new HashMap<>();
        if (analysis.getMetadata() != null) {
            // Safely convert the metadata map entries
            for (Map.Entry<String, String> entry : analysis.getMetadata().entrySet()) {
                metadata.put(entry.getKey(), entry.getValue());
            }
        }

        return AnalysisDTO.builder()
                .id(analysis.getId())
                .batchId(analysis.getBatch().getId())
                .summary(analysis.getSummary())
                .recommendations(analysis.getRecommendations())
                .metadata(metadata)
                .createdAt(analysis.getCreatedAt())
                .build();
    }
}