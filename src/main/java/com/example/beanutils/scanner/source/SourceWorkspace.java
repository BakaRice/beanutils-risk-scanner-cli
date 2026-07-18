package com.example.beanutils.scanner.source;

import com.example.beanutils.scanner.model.Diagnostic;
import com.example.beanutils.scanner.model.SourceLocation;
import com.example.beanutils.scanner.project.ModuleModel;
import com.example.beanutils.scanner.project.ProjectModel;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

public record SourceWorkspace(
        ProjectModel project,
        CombinedTypeSolver typeSolver,
        List<ParsedSource> parsedSources,
        List<Diagnostic> diagnostics,
        AutoCloseable compiledClassLoader) implements AutoCloseable {

    public SourceWorkspace {
        parsedSources = List.copyOf(parsedSources);
        diagnostics = List.copyOf(diagnostics);
    }

    @Override
    public void close() throws Exception {
        if (compiledClassLoader != null) {
            compiledClassLoader.close();
        }
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

    public Optional<SourceLocation> typeLocation(String qualifiedName) {
        for (ParsedSource parsed : parsedSources) {
            List<TypeDeclaration<?>> declarations = new ArrayList<>();
            declarations.addAll(parsed.compilationUnit().findAll(ClassOrInterfaceDeclaration.class));
            declarations.addAll(parsed.compilationUnit().findAll(EnumDeclaration.class));
            declarations.addAll(parsed.compilationUnit().findAll(RecordDeclaration.class));
            declarations.addAll(parsed.compilationUnit().findAll(AnnotationDeclaration.class));
            for (TypeDeclaration<?> declaration : declarations) {
                if (declaration.getFullyQualifiedName().filter(qualifiedName::equals).isPresent()) {
                    String relative = project.rootDirectory().relativize(parsed.path()).toString().replace('\\', '/');
                    int line = declaration.getBegin().map(position -> position.line).orElse(1);
                    int column = declaration.getBegin().map(position -> position.column).orElse(1);
                    return Optional.of(new SourceLocation(parsed.module(), relative, line, column));
                }
            }
        }
        return Optional.empty();
    }
}
