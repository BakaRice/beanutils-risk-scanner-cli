package com.example.beanutils.scanner.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanReportTest {
    @Test
    void sortsBySeverityAndKeepsSeparateTypes() {
        CopyFinding safe = finding(FindingStatus.SAFE, "z-module", "Z.java", 8);
        CopyFinding review = finding(FindingStatus.REVIEW, "a-module", "B.java", 2);
        CopyFinding risk = finding(FindingStatus.RISK, "a-module", "A.java", 1);
        CopyFinding ignored = finding(FindingStatus.IGNORED, "a-module", "C.java", 3);

        ScanReport report = new ScanReport("fixture", "2026-07-18T00:00:00Z",
                List.of(safe, review, risk, ignored), List.of());

        assertEquals(List.of(FindingStatus.RISK, FindingStatus.REVIEW,
                        FindingStatus.IGNORED, FindingStatus.SAFE),
                report.findings().stream().map(CopyFinding::status).toList());
        assertEquals("com.acme.Source", report.findings().get(0).sourceType().qualifiedName());
        assertEquals("com.acme.Target", report.findings().get(0).targetType().qualifiedName());
        assertEquals(1, report.count(FindingStatus.RISK));
    }

    private CopyFinding finding(FindingStatus status, String module, String file, int line) {
        return new CopyFinding(status, new SourceLocation(module, file, line, 1),
                "BeanUtils.copyProperties(source, target)",
                new TypeRef("Source", "com.acme.Source", true),
                new TypeRef("Target", "com.acme.Target", true),
                List.of(), List.of(), "DIRECT", module);
    }
}
