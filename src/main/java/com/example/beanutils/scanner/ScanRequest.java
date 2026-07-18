package com.example.beanutils.scanner;

import java.nio.file.Path;

public record ScanRequest(Path project, Path localRepository, boolean includeTests, boolean useCompiledClasses) {
    public ScanRequest(Path project, Path localRepository, boolean includeTests) {
        this(project, localRepository, includeTests, false);
    }
}
