package com.apex.idp.infrastructure.ocr;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutOptions {
    private boolean detectTables;
    private boolean detectForms;
    private boolean detectHeaders;
    private String outputFormat;
}