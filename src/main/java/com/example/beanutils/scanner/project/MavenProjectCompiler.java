package com.example.beanutils.scanner.project;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MavenProjectCompiler {
    public Result compile(Path project, Path localRepository, Path settings, boolean includeTests,
                          Consumer<String> output) {
        Consumer<String> sink = output == null ? unused -> { } : output;
        List<String> command = command(project, localRepository, settings, includeTests);
        sink.accept("[BeanUtilsScanner][COMPILE] command=" + String.join(" ", command));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(project.toFile())
                .redirectErrorStream(true);
        try {
            Process process = builder.start();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sink.accept("[BeanUtilsScanner][MAVEN] " + line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                sink.accept("[BeanUtilsScanner][COMPILE-END] status=success");
                return new Result(true, "");
            }
            String message = "Maven exited with code " + exitCode;
            sink.accept("[BeanUtilsScanner][COMPILE-ERROR] reason=" + message + " fallback=source");
            return new Result(false, message);
        } catch (IOException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            sink.accept("[BeanUtilsScanner][COMPILE-ERROR] reason=" + message + " fallback=source");
            return new Result(false, message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            sink.accept("[BeanUtilsScanner][COMPILE-ERROR] reason=interrupted fallback=source");
            return new Result(false, "interrupted");
        }
    }

    List<String> command(Path project, Path localRepository, Path settings, boolean includeTests) {
        List<String> command = new ArrayList<>();
        Path unixWrapper = project.resolve("mvnw");
        Path windowsWrapper = project.resolve("mvnw.cmd");
        if (Files.isRegularFile(unixWrapper)) {
            command.add(unixWrapper.toAbsolutePath().normalize().toString());
        } else if (Files.isRegularFile(windowsWrapper)) {
            command.add(windowsWrapper.toAbsolutePath().normalize().toString());
        } else {
            command.add("mvn");
        }
        command.add("-DskipTests");
        if (localRepository != null) {
            command.add("-Dmaven.repo.local=" + localRepository.toAbsolutePath().normalize());
        }
        if (settings != null) {
            command.add("-s");
            command.add(settings.toAbsolutePath().normalize().toString());
        }
        command.add(includeTests ? "test-compile" : "compile");
        return List.copyOf(command);
    }

    public record Result(boolean success, String message) {
    }
}
