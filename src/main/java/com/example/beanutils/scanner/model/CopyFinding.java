package com.example.beanutils.scanner.model;

import java.util.List;

public record CopyFinding(
        FindingStatus status,
        SourceLocation location,
        String code,
        TypeRef sourceType,
        TypeRef targetType,
        List<PropertyFinding> properties,
        List<CallChainStep> callChain,
        String callForm,
        String module) {

    public CopyFinding {
        properties = properties == null ? List.of() : List.copyOf(properties);
        callChain = callChain == null ? List.of() : List.copyOf(callChain);
        code = code == null ? "" : code;
        callForm = callForm == null ? "UNKNOWN" : callForm;
        module = module == null ? "" : module;
    }
}
