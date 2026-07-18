package com.example.beanutils.scanner.model;

public record Diagnostic(String severity, String code, String message, SourceLocation location) {
    public static Diagnostic warning(String code, String message, SourceLocation location) {
        return new Diagnostic("WARNING", code, message, location);
    }
}
