package com.example.beanutils.scanner.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenProjectLoaderTest {
    @TempDir Path temp;

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

    @Test
    void includesProvidedDependenciesBecauseCompiledClassesMayReferenceThem() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId><artifactId>provided-classpath</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework</groupId><artifactId>spring-beans</artifactId>
                      <version>5.0.7.RELEASE</version><scope>provided</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);

        ProjectModel project = new MavenProjectLoader().load(temp, false,
                Path.of(System.getProperty("user.home"), ".m2", "repository"));

        assertTrue(project.classpath().stream()
                .anyMatch(path -> path.getFileName().toString().equals("spring-beans-5.0.7.RELEASE.jar")));
    }
}
