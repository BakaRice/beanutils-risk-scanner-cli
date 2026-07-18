package com.example.beanutils.scanner.source;

import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;

public record ParsedSource(Path path, String module, CompilationUnit compilationUnit, String sourceText) {
}
