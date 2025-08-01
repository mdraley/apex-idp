package com.apex.idp.infrastructure.ocr;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentOCRResult {
    private String documentId;
    private String fileName;
    private boolean success;
    private OCRResult ocrResult;
    private String errorMessage;
}