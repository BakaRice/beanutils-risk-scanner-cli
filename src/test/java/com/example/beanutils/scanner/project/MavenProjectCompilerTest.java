package com.example.beanutils.scanner.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenProjectCompilerTest {
    @TempDir Path project;

    @Test
    void buildsAWrapperCommandWithRepositorySettingsAndTestClasses() throws Exception {
        Path wrapper = Files.createFile(project.resolve("mvnw"));
        Path repository = project.resolve("repo");
        Path settings = Files.createFile(project.resolve("settings.xml"));

        var command = new MavenProjectCompiler().command(project, repository, settings, true);

        assertEquals(wrapper.toAbsolutePath().normalize().toString(), command.get(0));
        assertEquals("-DskipTests", command.get(1));
        assertEquals("-Dmaven.repo.local=" + repository.toAbsolutePath().normalize(), command.get(2));
        assertEquals("-s", command.get(3));
        assertEquals(settings.toAbsolutePath().normalize().toString(), command.get(4));
        assertEquals("test-compile", command.get(5));
    }
}
