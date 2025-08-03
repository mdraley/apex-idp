package com.apex.idp.domain.batch;



/**
 * Enum representing the various states of batch processing.
 * States are organized in logical processing order from creation to completion.
 */

public enum BatchStatus {
    // Initial state
    CREATED("Created"),

    // Processing states
    PROCESSING("Processing"),
    OCR_COMPLETED("OCR Completed"),
    EXTRACTION_COMPLETED("Extraction Completed"),
    ANALYSIS_IN_PROGRESS("Analysis In Progress"),
    ANALYSIS_COMPLETED("Analysis Completed"),

    // Terminal success state
    COMPLETED("Completed"),

    // Terminal failure states
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

    /**
     * Determines if this status represents a terminal state (processing complete).
     * Terminal states indicate that no further processing will occur.
     */
    public boolean isTerminal() {
        return this == COMPLETED ||
                this == ANALYSIS_COMPLETED ||
                this == FAILED ||
                this == ANALYSIS_FAILED ||
                this == CANCELLED;
    }
}