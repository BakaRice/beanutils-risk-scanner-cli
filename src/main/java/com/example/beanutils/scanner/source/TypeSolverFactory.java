package com.example.beanutils.scanner.source;

import com.example.beanutils.scanner.model.Diagnostic;
import com.example.beanutils.scanner.project.ProjectModel;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TypeSolverFactory {
    public Setup create(ProjectModel project, List<Diagnostic> diagnostics, boolean compiledClasses,
                        boolean includeTests) {
        CombinedTypeSolver solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        CompiledProjectClassLoader classLoader = null;
        if (compiledClasses) {
            try {
                classLoader = CompiledProjectClassLoader.create(project, includeTests);
                solver.add(new ClassLoaderTypeSolver(classLoader));
            } catch (Exception exception) {
                diagnostics.add(Diagnostic.warning("CLASS_OUTPUT_INDEX_ERROR",
                        "Cannot index compiled project classes: " + exception.getMessage(), null));
            }
        }
        for (Path root : project.sourceRoots()) {
            if (Files.isDirectory(root)) {
                solver.add(new JavaParserTypeSolver(root));
            }
        }
        for (Path jar : project.classpath()) {
            try {
                solver.add(new JarTypeSolver(jar));
            } catch (Exception exception) {
                diagnostics.add(Diagnostic.warning("JAR_INDEX_ERROR",
                        "Cannot index " + jar + ": " + exception.getMessage(), null));
            }
        }
        return new Setup(solver, classLoader);
    }

    public record Setup(CombinedTypeSolver solver, AutoCloseable classLoader) {
    }
}
