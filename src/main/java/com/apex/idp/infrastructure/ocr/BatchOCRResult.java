package com.apex.idp.infrastructure.ocr;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOCRResult {
    private List<DocumentOCRResult> results;
    private int successCount;
    private int failureCount;
    private long totalProcessingTime;
}