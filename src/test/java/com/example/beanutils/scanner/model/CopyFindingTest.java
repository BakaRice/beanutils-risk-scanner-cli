package com.example.beanutils.scanner.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopyFindingTest {
    @Test
    void neverAllowsAReasonlessReviewFinding() {
        var finding = new CopyFinding(FindingStatus.REVIEW, new SourceLocation("API", "A.java", 1, 1),
                "BeanUtils.copyProperties(source, target)", TypeRef.unresolved("source"),
                TypeRef.unresolved("target"), List.of(), List.of(), "DIRECT", "API");

        assertEquals(1, finding.reviewReasons().size());
        assertEquals("REVIEW_REASON_MISSING", finding.reviewReasons().get(0).code());
    }

    @Test
    void preservesStructuredReviewReasonFields() {
        var reason = new ReviewReason("SOURCE_PARENT_TYPE_MISSING",
                "Source Bean 的父类 missing.ExternalBase 无法解析", "SOURCE", "id");
        var finding = new CopyFinding(FindingStatus.REVIEW, new SourceLocation("API", "A.java", 1, 1),
                "copy", TypeRef.unresolved("source"), TypeRef.unresolved("target"), List.of(), List.of(),
                "DIRECT", "API", List.of(reason));

        assertEquals(reason, finding.reviewReasons().get(0));
        assertEquals("SOURCE", finding.reviewReasons().get(0).subject());
        assertEquals("id", finding.reviewReasons().get(0).propertyName());
    }
}
