package com.example.beanutils.scanner.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record ModuleModel(
        String groupId,
        String artifactId,
        String version,
        String javaRelease,
        Path baseDirectory,
        List<Path> mainSourceRoots,
        List<Path> testSourceRoots,
        Set<String> reactorDependencies,
        List<Path> classpath) {

    public ModuleModel {
        mainSourceRoots = List.copyOf(mainSourceRoots);
        testSourceRoots = List.copyOf(testSourceRoots);
        reactorDependencies = Set.copyOf(reactorDependencies);
        classpath = List.copyOf(classpath);
    }

    public String coordinates() {
        return groupId + ":" + artifactId;
    }
}
