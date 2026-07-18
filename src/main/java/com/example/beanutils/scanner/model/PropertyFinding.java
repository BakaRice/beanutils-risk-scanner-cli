package com.example.beanutils.scanner.model;

public record PropertyFinding(
        String propertyName,
        TypeRef sourceType,
        TypeRef targetType,
        String sourceDeclaringType,
        String targetDeclaringType,
        PropertyMapping mapping,
        FindingStatus status,
        boolean oldAssignable,
        String newDecision,
        String reason) {
}
