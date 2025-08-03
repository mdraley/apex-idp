package com.apex.idp.domain.invoice;

/**
 * Enum representing the possible statuses of an invoice throughout its lifecycle.
 * Statuses are organized in typical workflow order.
 */
public enum InvoiceStatus {
    /**
     * Initial draft state when invoice is first created
     */
    DRAFT,

    /**
     * Invoice is pending initial processing
     */
    PENDING,

    /**
     * OCR/data extraction failed and requires manual intervention
     */
    EXTRACTION_FAILED,

    /**
     * Invoice has been processed and extracted data is available
     */
    PROCESSED,

    /**
     * Invoice requires manual review due to validation issues
     */
    REQUIRES_REVIEW,

    /**
     * Invoice is currently being reviewed by a user
     */
    IN_REVIEW,

    /**
     * Invoice has been approved for payment
     */
    APPROVED,

    /**
     * Invoice has been rejected and will not be processed
     */
    REJECTED,

    /**
     * Invoice is approved and awaiting payment processing
     */
    PENDING_PAYMENT,

    /**
     * Invoice has been paid successfully
     */
    PAID,

    /**
     * Invoice has been voided/cancelled
     */
    VOIDED
}