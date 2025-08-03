package com.apex.idp.interfaces.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {
    private String batchId;
    private String message;
    private String response;
    private List<DocumentReferenceDTO> references;
    private Map<String, Object> metadata;
    private String error;

    public static ChatResponseDTO error(String errorMessage) {
        return ChatResponseDTO.builder()
                .error(errorMessage)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentReferenceDTO {
        private String documentId;
        private String fileName;
        private String excerpt;
        private Integer pageNumber;
        private Double relevanceScore;

        public static DocumentReferenceDTO from(com.apex.idp.infrastructure.ai.OpenAIService.DocumentReference ref) {
            return DocumentReferenceDTO.builder()
                    .documentId(ref.getDocumentId())
                    .fileName(ref.getFileName())
                    .excerpt(ref.getExcerpt())
                    .pageNumber(ref.getPageNumber())
                    .relevanceScore(ref.getRelevanceScore())
                    .build();
        }
    }
}