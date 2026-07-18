package com.example.beanutils.scanner.model;

public record SourceLocation(String module, String relativePath, int line, int column) {
    public SourceLocation {
        module = module == null ? "" : module;
        relativePath = relativePath == null ? "" : relativePath;
    }

    public String display() {
        return relativePath + ":" + line;
    }
}
