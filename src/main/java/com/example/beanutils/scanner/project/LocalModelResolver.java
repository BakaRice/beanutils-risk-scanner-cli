package com.example.beanutils.scanner.project;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalModelResolver {
    private final Path repository;

    public LocalModelResolver(Path repository) {
        this.repository = repository.toAbsolutePath().normalize();
    }

    public Path artifact(String groupId, String artifactId, String version, String extension) {
        String relative = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/"
                + artifactId + "-" + version + "." + extension;
        return repository.resolve(relative);
    }

    public Path existingArtifact(String groupId, String artifactId, String version, String extension) {
        Path path = artifact(groupId, artifactId, version, extension);
        return Files.isRegularFile(path) ? path : null;
    }
}
