package com.apex.idp.infrastructure.ocr;

/**
 * Custom exception for OCR processing errors.
 */
public class OCRException extends Exception {
    public OCRException(String message) {
        super(message);
    }

    public OCRException(String message, Throwable cause) {
        super(message, cause);
    }
}