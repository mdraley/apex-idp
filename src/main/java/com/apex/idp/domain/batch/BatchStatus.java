package com.apex.idp.domain.batch;

/**
 * Enum representing the various states of batch processing.
 */
public enum BatchStatus {
    CREATED("Created"),
    PROCESSING("Processing"),
    OCR_COMPLETED("OCR Completed"),
    ANALYSIS_IN_PROGRESS("Analysis In Progress"),
    ANALYSIS_COMPLETED("Analysis Completed"),
    COMPLETED("Completed"),
    ANALYSIS_FAILED("Analysis Failed"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    private final String displayName;

    BatchStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == ANALYSIS_COMPLETED || this == COMPLETED || this == FAILED || 
               this == CANCELLED || this == ANALYSIS_FAILED;
    }
}