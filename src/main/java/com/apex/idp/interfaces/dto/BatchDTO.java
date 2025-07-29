package com.apex.idp.interfaces.dto;

import com.apex.idp.domain.batch.Batch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDTO {
    private String id;
    private String name;
    private String description;
    private String status;
    private int documentCount;
    private int processedCount;
    private int failedCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DocumentDTO> documents;
    private String error;

    /**
     * Creates a BatchDTO from a Batch domain entity.
     */
    public static BatchDTO from(Batch batch) {
        return BatchDTO.builder()
                .id(batch.getId())
                .name(batch.getName())
                .description(batch.getDescription())
                .status(batch.getStatus().name())
                .documentCount(batch.getDocumentCount())
                .processedCount(batch.getProcessedDocumentCount())
                .failedCount(batch.getFailedDocumentCount())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }

    /**
     * Creates an error BatchDTO.
     */
    public static BatchDTO error(String errorMessage) {
        return BatchDTO.builder()
                .error(errorMessage)
                .build();
    }
}