package com.example.beanutils.scanner.model;

public enum FindingStatus {
    RISK(0), REVIEW(1), IGNORED(2), SAFE(3);

    private final int severityOrder;

    FindingStatus(int severityOrder) {
        this.severityOrder = severityOrder;
    }

    public int severityOrder() {
        return severityOrder;
    }
}
