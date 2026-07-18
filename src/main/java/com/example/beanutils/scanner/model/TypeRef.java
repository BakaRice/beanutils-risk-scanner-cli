package com.example.beanutils.scanner.model;

public record TypeRef(String displayName, String qualifiedName, boolean resolved) {
    public TypeRef {
        displayName = displayName == null || displayName.isBlank() ? "?" : displayName;
        qualifiedName = qualifiedName == null || qualifiedName.isBlank() ? displayName : qualifiedName;
    }

    public static TypeRef unresolved(String expression) {
        return new TypeRef(expression, expression, false);
    }
}
