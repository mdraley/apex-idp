package com.apex.idp.infrastructure.ocr;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreprocessingOptions {
    private boolean deskew;
    private boolean removeNoise;
    private boolean enhanceContrast;
    private boolean binarize;
    private int targetDpi;
}