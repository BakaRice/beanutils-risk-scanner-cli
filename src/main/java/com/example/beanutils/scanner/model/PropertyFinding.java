package com.example.beanutils.scanner.model;

public record PropertyFinding(
        String propertyName,
        TypeRef sourceType,
        TypeRef targetType,
        FindingStatus status,
        boolean oldAssignable,
        String newDecision,
        String reason) {
}
