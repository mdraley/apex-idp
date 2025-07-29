package com.apex.idp.interfaces.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ChatResponseDTO {
    private String batchId;
    private String message;
    private String response;
    private List<?> references;
    private Map<String, Object> metadata;
    private String error;

    /**
     * Creates an error response DTO.
     */
    public static ChatResponseDTO error(String errorMessage) {
        return ChatResponseDTO.builder()
                .error(errorMessage)
                .build();
    }
}