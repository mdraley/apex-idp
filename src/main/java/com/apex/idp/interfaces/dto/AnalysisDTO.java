package com.apex.idp.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisDTO {
    private String id;
    private String batchId;
    private String summary;
    private List<String> recommendations;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}