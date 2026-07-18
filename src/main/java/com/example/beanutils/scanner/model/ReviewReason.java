package com.example.beanutils.scanner.model;

public record ReviewReason(String code, String message, String subject, String propertyName) {
    public ReviewReason {
        code = normalize(code, "REVIEW_REASON_UNSPECIFIED");
        message = normalize(message, "待审查原因未说明");
        subject = normalize(subject, "CALL");
        propertyName = propertyName == null ? "" : propertyName.trim();
    }

    public ReviewReason(String code, String message, String subject) {
        this(code, message, subject, "");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
