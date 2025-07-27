package com.apex.idp.domain.document;

public enum DocumentType {
    PDF("application/pdf"),
    JPEG("image/jpeg"),
    PNG("image/png"),
    TIFF("image/tiff");

    private final String mimeType;

    DocumentType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public static DocumentType fromMimeType(String mimeType) {
        for (DocumentType type : values()) {
            if (type.mimeType.equals(mimeType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported mime type: " + mimeType);
    }
}
