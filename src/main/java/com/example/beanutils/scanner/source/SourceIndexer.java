package com.example.beanutils.scanner.source;

import com.example.beanutils.scanner.model.Diagnostic;
import com.example.beanutils.scanner.model.SourceLocation;
import com.example.beanutils.scanner.project.ModuleModel;
import com.example.beanutils.scanner.project.ProjectModel;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SourceIndexer {
    public SourceWorkspace index(ProjectModel project) throws Exception {
        return index(project, false, false);
    }

    public SourceWorkspace index(ProjectModel project, boolean compiledClasses, boolean includeTests) throws Exception {
        List<Diagnostic> diagnostics = new ArrayList<>(project.diagnostics());
        var setup = new TypeSolverFactory().create(project, diagnostics, compiledClasses, includeTests);
        var typeSolver = setup.solver();
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8)
                .setStoreTokens(true)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        JavaParser parser = new JavaParser(configuration);

        List<Path> files = new ArrayList<>();
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                        .forEach(files::add);
            }
        }
        files.sort(Comparator.comparing(Path::toString));

        List<ParsedSource> parsed = new ArrayList<>();
        for (Path file : files) {
            String module = module(project, file);
            var result = parser.parse(file);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                CompilationUnit unit = result.getResult().orElseThrow();
                unit.setStorage(file);
                parsed.add(new ParsedSource(file, module, unit, Files.readString(file)));
            } else {
                String message = result.getProblems().isEmpty() ? "Unknown parse error"
                        : result.getProblems().get(0).getVerboseMessage();
                Path relative = project.rootDirectory().relativize(file);
                diagnostics.add(Diagnostic.warning("JAVA_PARSE_ERROR", message,
                        new SourceLocation(module, relative.toString(), 1, 1)));
            }
        }
        return new SourceWorkspace(project, typeSolver, parsed, diagnostics, setup.classLoader());
    }

    private String module(ProjectModel project, Path file) {
        return project.modules().stream()
                .filter(candidate -> file.toAbsolutePath().normalize().startsWith(candidate.baseDirectory()))
                .map(ModuleModel::artifactId)
                .findFirst().orElse("");
    }
}
