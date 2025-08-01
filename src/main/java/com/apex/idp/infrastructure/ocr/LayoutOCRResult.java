package com.apex.idp.infrastructure.ocr;

import lombok.*;
import org.apache.commons.compress.harmony.pack200.NewAttributeBands;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutOCRResult {
    private OCRResult baseResult;
    private List<NewAttributeBands.LayoutElement> elements;
    private Map<String, Object> layoutMetadata;
}