package com.example.beanutils.scanner.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenProjectLoaderTest {
    @Test
    void loadsReactorAndSourceRoots() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/maven-reactor").toAbsolutePath();
        ProjectModel project = new MavenProjectLoader().load(fixture, false,
                Path.of(System.getProperty("user.home"), ".m2", "repository"));

        assertEquals(List.of("alpha", "beta"), project.modules().stream()
                .map(ModuleModel::artifactId).toList());
        assertTrue(project.modules().get(1).reactorDependencies().contains("example:alpha"));
        assertFalse(project.sourceRoots().stream()
                .anyMatch(path -> path.endsWith(Path.of("src", "test", "java"))));
        assertEquals("8", project.modules().get(0).javaRelease());
    }

    @Test
    void includesTestRootsWhenRequested() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/maven-reactor").toAbsolutePath();
        ProjectModel project = new MavenProjectLoader().load(fixture, true,
                Path.of(System.getProperty("user.home"), ".m2", "repository"));
        assertTrue(project.modules().stream().allMatch(module -> module.testSourceRoots().size() == 1));
    }
}
