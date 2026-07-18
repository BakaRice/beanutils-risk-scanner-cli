package com.example.beanutils.scanner.project;

import com.example.beanutils.scanner.model.Diagnostic;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class MavenProjectLoader {
    public ProjectModel load(Path projectRoot, boolean includeTests, Path localRepository) throws Exception {
        Path root = projectRoot.toAbsolutePath().normalize();
        Model rootModel = read(root.resolve("pom.xml"));
        Properties properties = mergedProperties(rootModel, null);
        Map<String, String> managedVersions = managedVersions(rootModel, properties);

        List<Path> modulePaths = new ArrayList<>();
        if (rootModel.getModules().isEmpty()) {
            modulePaths.add(root);
        } else {
            for (String module : rootModel.getModules()) {
                Path modulePath = root.resolve(module).normalize();
                if (!modulePath.startsWith(root)) {
                    throw new IllegalArgumentException("Module escapes project root: " + module);
                }
                modulePaths.add(modulePath);
            }
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        List<RawModule> rawModules = new ArrayList<>();
        for (Path modulePath : modulePaths) {
            Model model = read(modulePath.resolve("pom.xml"));
            Properties moduleProperties = mergedProperties(model, properties);
            Map<String, String> moduleManaged = new LinkedHashMap<>(managedVersions);
            moduleManaged.putAll(managedVersions(model, moduleProperties));
            rawModules.add(new RawModule(modulePath, model, moduleProperties, moduleManaged));
        }

        Set<String> reactorCoordinates = new LinkedHashSet<>();
        for (RawModule module : rawModules) {
            reactorCoordinates.add(groupId(module.model(), rootModel) + ":" + module.model().getArtifactId());
        }

        LocalClasspathResolver classpathResolver = new LocalClasspathResolver(localRepository);
        List<ModuleModel> modules = new ArrayList<>();
        LinkedHashSet<Path> allClasspath = new LinkedHashSet<>();
        List<Path> sourceRoots = new ArrayList<>();
        for (RawModule raw : rawModules) {
            Model model = raw.model();
            String groupId = groupId(model, rootModel);
            String version = version(model, rootModel);
            Set<String> reactorDependencies = new LinkedHashSet<>();
            List<Dependency> external = new ArrayList<>();
            for (Dependency dependency : model.getDependencies()) {
                String coordinate = dependency.getGroupId() + ":" + dependency.getArtifactId();
                if (reactorCoordinates.contains(coordinate)) {
                    reactorDependencies.add(coordinate);
                } else {
                    external.add(dependency);
                }
            }
            List<Path> classpath = classpathResolver.resolve(external, raw.managedVersions(),
                    raw.properties(), diagnostics);
            allClasspath.addAll(classpath);
            Path main = raw.baseDirectory().resolve("src/main/java");
            Path test = raw.baseDirectory().resolve("src/test/java");
            sourceRoots.add(main);
            if (includeTests) {
                sourceRoots.add(test);
            }
            modules.add(new ModuleModel(groupId, model.getArtifactId(), version,
                    javaRelease(raw.properties()), raw.baseDirectory(), List.of(main), List.of(test),
                    reactorDependencies, classpath));
        }

        return new ProjectModel(root, modules, sourceRoots, List.copyOf(allClasspath), diagnostics);
    }

    private Model read(Path pom) throws Exception {
        try (Reader reader = Files.newBufferedReader(pom)) {
            return new MavenXpp3Reader().read(reader);
        }
    }

    private Properties mergedProperties(Model model, Properties inherited) {
        Properties result = new Properties();
        if (inherited != null) {
            result.putAll(inherited);
        }
        result.putAll(model.getProperties());
        Parent parent = model.getParent();
        String projectVersion = model.getVersion() != null ? model.getVersion()
                : parent == null ? null : parent.getVersion();
        String projectGroup = model.getGroupId() != null ? model.getGroupId()
                : parent == null ? null : parent.getGroupId();
        if (projectVersion != null) {
            result.setProperty("project.version", projectVersion);
            result.setProperty("pom.version", projectVersion);
        }
        if (projectGroup != null) {
            result.setProperty("project.groupId", projectGroup);
            result.setProperty("pom.groupId", projectGroup);
        }
        return result;
    }

    private Map<String, String> managedVersions(Model model, Properties properties) {
        Map<String, String> result = new LinkedHashMap<>();
        DependencyManagement management = model.getDependencyManagement();
        if (management != null) {
            for (Dependency dependency : management.getDependencies()) {
                result.put(dependency.getGroupId() + ":" + dependency.getArtifactId(),
                        interpolate(dependency.getVersion(), properties));
            }
        }
        return result;
    }

    static String interpolate(String value, Properties properties) {
        if (value == null) {
            return null;
        }
        String result = value;
        for (int iteration = 0; iteration < 10; iteration++) {
            int start = result.indexOf("${");
            if (start < 0) {
                break;
            }
            int end = result.indexOf('}', start);
            if (end < 0) {
                break;
            }
            String key = result.substring(start + 2, end);
            String replacement = properties.getProperty(key);
            if (replacement == null) {
                break;
            }
            result = result.substring(0, start) + replacement + result.substring(end + 1);
        }
        return result;
    }

    private String groupId(Model model, Model root) {
        if (model.getGroupId() != null) return model.getGroupId();
        if (model.getParent() != null) return model.getParent().getGroupId();
        return root.getGroupId();
    }

    private String version(Model model, Model root) {
        if (model.getVersion() != null) return model.getVersion();
        if (model.getParent() != null) return model.getParent().getVersion();
        return root.getVersion();
    }

    private String javaRelease(Properties properties) {
        String release = properties.getProperty("maven.compiler.release");
        if (release != null) return interpolate(release, properties);
        return properties.getProperty("maven.compiler.source", "8");
    }

    private record RawModule(Path baseDirectory, Model model, Properties properties,
                             Map<String, String> managedVersions) {
    }
}
