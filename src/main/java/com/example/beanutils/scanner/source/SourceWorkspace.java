package com.example.beanutils.scanner.source;

import com.example.beanutils.scanner.model.Diagnostic;
import com.example.beanutils.scanner.project.ModuleModel;
import com.example.beanutils.scanner.project.ProjectModel;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record SourceWorkspace(
        ProjectModel project,
        CombinedTypeSolver typeSolver,
        List<ParsedSource> parsedSources,
        List<Diagnostic> diagnostics) {

    public SourceWorkspace {
        parsedSources = List.copyOf(parsedSources);
        diagnostics = List.copyOf(diagnostics);
    }

    public Optional<ResolvedReferenceTypeDeclaration> resolveType(String qualifiedName) {
        try {
            var solved = typeSolver.tryToSolveType(qualifiedName);
            return solved.isSolved() ? Optional.of(solved.getCorrespondingDeclaration()) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public Optional<ModuleModel> moduleFor(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        return project.modules().stream()
                .filter(module -> normalized.startsWith(module.baseDirectory()))
                .findFirst();
    }
}
