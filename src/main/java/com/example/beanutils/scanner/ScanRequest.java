package com.example.beanutils.scanner;

import java.nio.file.Path;

public record ScanRequest(Path project, Path localRepository, boolean includeTests) {
}
