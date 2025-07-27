package com.apex.idp.interfaces.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatResponseDTO {
    private String batchId;
    private String message;
    private String response;
}