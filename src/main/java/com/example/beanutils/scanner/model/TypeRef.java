package com.example.beanutils.scanner.model;

public record TypeRef(String displayName, String qualifiedName, boolean resolved,
                      String module, String sourcePath) {
    public TypeRef(String displayName, String qualifiedName, boolean resolved) {
        this(displayName, qualifiedName, resolved, "", "");
    }

    public TypeRef {
        displayName = displayName == null || displayName.isBlank() ? "?" : displayName;
        qualifiedName = qualifiedName == null || qualifiedName.isBlank() ? displayName : qualifiedName;
        module = module == null ? "" : module;
        sourcePath = sourcePath == null ? "" : sourcePath;
    }

    public static TypeRef unresolved(String expression) {
        return new TypeRef(expression, expression, false);
    }

    public TypeRef withOrigin(String originModule, String originSourcePath) {
        return new TypeRef(displayName, qualifiedName, resolved, originModule, originSourcePath);
    }
}
