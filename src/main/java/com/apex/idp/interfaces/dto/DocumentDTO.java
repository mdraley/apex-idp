package com.apex.idp.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {
    private String id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String status;
    private LocalDateTime createdAt;
}