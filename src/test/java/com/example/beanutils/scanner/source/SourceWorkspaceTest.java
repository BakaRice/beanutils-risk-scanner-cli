package com.example.beanutils.scanner.source;

import com.example.beanutils.scanner.project.MavenProjectLoader;
import com.example.beanutils.scanner.project.ProjectModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceWorkspaceTest {
    @Test
    void resolvesAcrossModulesAndKeepsParseDiagnostics() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/maven-reactor").toAbsolutePath();
        ProjectModel project = new MavenProjectLoader().load(fixture, false,
                Path.of(System.getProperty("user.home"), ".m2", "repository"));

        SourceWorkspace workspace = new SourceIndexer().index(project);

        assertTrue(workspace.resolveType("example.alpha.Alpha").isPresent());
        assertTrue(workspace.resolveType("example.beta.Beta").isPresent());
        assertTrue(workspace.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JAVA_PARSE_ERROR")));
        assertTrue(workspace.parsedSources().size() >= 2);
    }
}
