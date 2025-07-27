package com.apex.idp.application.service;

import com.apex.idp.domain.analysis.AnalysisRepository;
import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.batch.BatchRepository;
import com.apex.idp.infrastructure.ai.OpenAIService;
import com.apex.idp.interfaces.dto.ChatRequestDTO;
import com.apex.idp.interfaces.dto.ChatResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final BatchRepository batchRepository;
    private final OpenAIService openAIService;

    public ChatResponseDTO chat(String batchId, ChatRequestDTO request) {
        Batch batch = batchRepository.findByIdWithDocuments(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        String context = buildContext(batch, request.getInvoiceId());
        String response = openAIService.generateResponse(request.getMessage(), context);

        return ChatResponseDTO.builder()
                .batchId(batchId)
                .message(request.getMessage())
                .response(response)
                .build();
    }

    private String buildContext(Batch batch, String invoiceId) {
        StringBuilder context = new StringBuilder();
        context.append("Batch: ").append(batch.getName()).append("\n");
        context.append("Documents: ").append(batch.getDocumentCount()).append("\n");

        // Add specific invoice context if provided
        if (invoiceId != null) {
            batch.getDocuments().stream()
                    .filter(doc -> doc.getInvoice() != null && doc.getInvoice().getId().equals(invoiceId))
                    .findFirst()
                    .ifPresent(doc -> {
                        context.append("Invoice: ").append(doc.getInvoice().getInvoiceNumber()).append("\n");
                        context.append("Amount: ").append(doc.getInvoice().getAmount()).append("\n");
                        if (doc.getInvoice().getVendor() != null) {
                            context.append("Vendor: ").append(doc.getInvoice().getVendor().getName()).append("\n");
                        }
                    });
        }

        return context.toString();
    }
}
