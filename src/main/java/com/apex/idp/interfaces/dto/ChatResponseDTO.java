package com.apex.idp.interfaces.dto;

import com.apex.idp.infrastructure.ai.OpenAIService.DocumentReference;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {
    private String batchId;
    private String message;
    private String response;
    private List<DocumentReference> references;
    private Map<String, Object> metadata;
    private String error;

    public static ChatResponseDTO error(String errorMessage) {
        return ChatResponseDTO.builder()
                .error(errorMessage)
                .build();
    }
}