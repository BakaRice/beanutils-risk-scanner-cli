package com.example.beanutils.scanner.project;

import com.example.beanutils.scanner.model.Diagnostic;

import java.nio.file.Path;
import java.util.List;

public record ProjectModel(
        Path rootDirectory,
        List<ModuleModel> modules,
        List<Path> sourceRoots,
        List<Path> classpath,
        List<Diagnostic> diagnostics) {

    public ProjectModel {
        modules = List.copyOf(modules);
        sourceRoots = List.copyOf(sourceRoots);
        classpath = List.copyOf(classpath);
        diagnostics = List.copyOf(diagnostics);
    }
}
