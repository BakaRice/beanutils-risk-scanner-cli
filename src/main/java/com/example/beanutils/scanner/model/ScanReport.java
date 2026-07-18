package com.example.beanutils.scanner.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ScanReport(
        String projectPath,
        String generatedAt,
        List<CopyFinding> findings,
        List<Diagnostic> diagnostics) {

    public ScanReport {
        List<CopyFinding> ordered = new ArrayList<>(findings == null ? List.of() : findings);
        ordered.sort(Comparator
                .comparingInt((CopyFinding finding) -> finding.status().severityOrder())
                .thenComparing(CopyFinding::module)
                .thenComparing(finding -> finding.location().relativePath())
                .thenComparingInt(finding -> finding.location().line())
                .thenComparingInt(finding -> finding.location().column()));
        findings = List.copyOf(ordered);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public long count(FindingStatus status) {
        return findings.stream().filter(finding -> finding.status() == status).count();
    }
}
