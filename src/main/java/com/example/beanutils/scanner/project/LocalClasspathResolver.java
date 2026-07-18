package com.example.beanutils.scanner.project;

import com.example.beanutils.scanner.model.Diagnostic;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class LocalClasspathResolver {
    private final LocalModelResolver resolver;

    public LocalClasspathResolver(Path repository) {
        this.resolver = new LocalModelResolver(repository);
    }

    public List<Path> resolve(List<Dependency> dependencies, Map<String, String> managedVersions,
                              Properties properties, List<Diagnostic> diagnostics) {
        Set<Path> paths = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<Request> queue = new ArrayDeque<>();
        for (Dependency dependency : dependencies) {
            queue.add(new Request(dependency, managedVersions, properties, Set.of()));
        }
        while (!queue.isEmpty()) {
            Request request = queue.removeFirst();
            Dependency dependency = request.dependency();
            String coordinate = dependency.getGroupId() + ":" + dependency.getArtifactId();
            if (request.exclusions().contains(coordinate)) {
                continue;
            }
            String scope = dependency.getScope();
            if ("test".equals(scope) || "system".equals(scope)
                    || dependency.isOptional() || "pom".equals(dependency.getType())) {
                continue;
            }
            String version = dependency.getVersion();
            if (version == null) {
                version = request.managedVersions().get(coordinate);
            }
            version = MavenProjectLoader.interpolate(version, request.properties());
            if (version == null || version.contains("${")) {
                diagnostics.add(Diagnostic.warning("MAVEN_VERSION_UNRESOLVED",
                        "Cannot resolve dependency version for " + coordinate, null));
                continue;
            }
            String versionedCoordinate = coordinate + ":" + version;
            if (!visited.add(versionedCoordinate)) {
                continue;
            }
            Path jar = resolver.existingArtifact(dependency.getGroupId(), dependency.getArtifactId(), version, "jar");
            if (jar != null) {
                paths.add(jar);
            } else {
                diagnostics.add(Diagnostic.warning("MAVEN_ARTIFACT_MISSING",
                        "Artifact not found in local repository: " + coordinate + ":" + version, null));
            }
            enqueueTransitive(dependency.getGroupId(), dependency.getArtifactId(), version,
                    request.properties(), request.managedVersions(), exclusions(request), queue, diagnostics);
        }
        return paths.stream().distinct().sorted().toList();
    }

    private void enqueueTransitive(String groupId, String artifactId, String version,
                                   Properties inheritedProperties, Map<String, String> inheritedManaged,
                                   Set<String> exclusions, ArrayDeque<Request> queue, List<Diagnostic> diagnostics) {
        Path pom = resolver.existingArtifact(groupId, artifactId, version, "pom");
        if (pom == null) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(pom)) {
            Model model = new MavenXpp3Reader().read(reader);
            Properties properties = new Properties();
            properties.putAll(inheritedProperties);
            properties.putAll(model.getProperties());
            properties.setProperty("project.groupId", model.getGroupId() == null ? groupId : model.getGroupId());
            properties.setProperty("project.artifactId", model.getArtifactId());
            properties.setProperty("project.version", model.getVersion() == null ? version : model.getVersion());
            Map<String, String> managed = new LinkedHashMap<>(inheritedManaged);
            if (model.getDependencyManagement() != null) {
                for (Dependency dependency : model.getDependencyManagement().getDependencies()) {
                    managed.put(dependency.getGroupId() + ":" + dependency.getArtifactId(),
                            MavenProjectLoader.interpolate(dependency.getVersion(), properties));
                }
            }
            for (Dependency child : model.getDependencies()) {
                queue.addLast(new Request(child, managed, properties, exclusions));
            }
        } catch (Exception exception) {
            diagnostics.add(Diagnostic.warning("MAVEN_POM_UNREADABLE",
                    "Cannot read dependency POM " + pom + ": " + exception.getMessage(), null));
        }
    }

    private Set<String> exclusions(Request request) {
        Set<String> exclusions = new LinkedHashSet<>(request.exclusions());
        request.dependency().getExclusions().forEach(value -> exclusions.add(value.getGroupId() + ":" + value.getArtifactId()));
        return Set.copyOf(exclusions);
    }

    private record Request(Dependency dependency, Map<String, String> managedVersions, Properties properties,
                           Set<String> exclusions) {
    }
}
