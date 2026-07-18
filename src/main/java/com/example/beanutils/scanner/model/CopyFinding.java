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
        String module,
        List<ReviewReason> reviewReasons) {

    public CopyFinding {
        properties = properties == null ? List.of() : List.copyOf(properties);
        callChain = callChain == null ? List.of() : List.copyOf(callChain);
        code = code == null ? "" : code;
        callForm = callForm == null ? "UNKNOWN" : callForm;
        module = module == null ? "" : module;
        reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
        if (status == FindingStatus.REVIEW && reviewReasons.isEmpty()) {
            reviewReasons = List.of(new ReviewReason("REVIEW_REASON_MISSING",
                    "扫描器未记录具体待审查原因；这是扫描器自身的诊断缺口，不能据此判定为安全", "CALL"));
        }
    }

    public CopyFinding(FindingStatus status, SourceLocation location, String code, TypeRef sourceType,
                       TypeRef targetType, List<PropertyFinding> properties, List<CallChainStep> callChain,
                       String callForm, String module) {
        this(status, location, code, sourceType, targetType, properties, callChain, callForm, module, List.of());
    }
}
