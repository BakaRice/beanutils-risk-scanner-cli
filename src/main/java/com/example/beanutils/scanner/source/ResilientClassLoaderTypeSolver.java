package com.example.beanutils.scanner.source;

import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

final class ResilientClassLoaderTypeSolver extends ClassLoaderTypeSolver {
    private final Consumer<String> output;
    private final Set<String> acceptedTypes = new LinkedHashSet<>();
    private final Set<String> rejectedTypes = new LinkedHashSet<>();

    ResilientClassLoaderTypeSolver(ClassLoader classLoader, Consumer<String> output) {
        super(classLoader);
        this.output = output == null ? unused -> { } : output;
    }

    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
        if (rejectedTypes.contains(name)) {
            return SymbolReference.unsolved();
        }
        SymbolReference<ResolvedReferenceTypeDeclaration> solved = super.tryToSolveType(name);
        if (!solved.isSolved()) {
            return solved;
        }
        if (acceptedTypes.contains(name)) {
            return solved;
        }
        try {
            ResolvedReferenceTypeDeclaration declaration = solved.getCorrespondingDeclaration();
            declaration.getDeclaredMethods().forEach(MethodUsage::new);
            declaration.getAncestors();
            acceptedTypes.add(name);
            return solved;
        } catch (LinkageError | RuntimeException exception) {
            rejectedTypes.add(name);
            output.accept("[BeanUtilsScanner][CLASS-FALLBACK] type=" + name
                    + " reason=" + exception.getClass().getSimpleName()
                    + " missing=" + missingType(exception));
            return SymbolReference.unsolved();
        }
    }

    private String missingType(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getMessage();
        }
        return message == null || message.isBlank() ? "?" : message.replace('/', '.').replaceAll("[\\r\\n]+", " ");
    }
}
