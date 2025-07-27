package com.apex.idp.interfaces.dto;

import com.apex.idp.domain.batch.Batch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDTO {

    private String id;
    private String name;
    private String status;
    private int documentCount;
    private int processedCount;
    private String createdAt;
    private String updatedAt;

    /**
     * A static factory method to create a BatchDTO from a Batch domain entity.
     * This is the method the compiler was unable to find.
     *
     * @param batch The Batch entity from the database.
     * @return A new BatchDTO object with data suitable for the frontend.
     */
    public static BatchDTO from(Batch batch) {
        return BatchDTO.builder()
                .id(batch.getId())
                .name(batch.getName())
                .status(batch.getStatus().name()) // Convert enum to a String
                .documentCount(batch.getDocumentCount())
                .processedCount(batch.getProcessedCount())
                .createdAt(batch.getCreatedAt().toString())
                .updatedAt(batch.getUpdatedAt() != null ? batch.getUpdatedAt().toString() : null)
                .build();
    }
}