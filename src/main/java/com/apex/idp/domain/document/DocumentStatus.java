package com.apex.idp.domain.document;

/**
 * Enum representing the various states of document processing.
 */
public enum DocumentStatus {
    /**
     * Document has been created but not yet processed
     */
    CREATED("Created"),

    /**
     * Document is currently being processed
     */
    PROCESSING("Processing"),

    /**
     * OCR has been completed on the document
     */
    OCR_COMPLETED("OCR Completed"),

    /**
     * Document has been successfully processed
     */
    PROCESSED("Processed"),

    /**
     * Document processing has failed
     */
    FAILED("Failed"),

    /**
     * Document has been rejected
     */
    REJECTED("Rejected");

    private final String displayName;

    DocumentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this status represents a terminal state (no further processing)
     */
    public boolean isTerminal() {
        return this == PROCESSED || this == FAILED || this == REJECTED;
    }

    /**
     * Checks if this status represents a successful state
     */
    public boolean isSuccessful() {
        return this == PROCESSED || this == OCR_COMPLETED;
    }
}