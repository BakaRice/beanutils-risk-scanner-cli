package com.example.beanutils.scanner.source;

import com.example.beanutils.scanner.project.ProjectModel;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CompiledProjectClassLoader extends URLClassLoader {
    private CompiledProjectClassLoader(URL[] urls) {
        super(urls, ClassLoader.getPlatformClassLoader());
    }

    static CompiledProjectClassLoader create(ProjectModel project, boolean includeTests)
            throws MalformedURLException {
        Set<Path> paths = new LinkedHashSet<>();
        project.modules().forEach(module -> {
            Path main = module.baseDirectory().resolve("target/classes");
            if (Files.isDirectory(main)) {
                paths.add(main);
            }
            Path tests = module.baseDirectory().resolve("target/test-classes");
            if (includeTests && Files.isDirectory(tests)) {
                paths.add(tests);
            }
        });
        project.classpath().stream().filter(Files::exists).forEach(paths::add);
        List<URL> urls = new ArrayList<>();
        for (Path path : paths) {
            urls.add(path.toUri().toURL());
        }
        return new CompiledProjectClassLoader(urls.toArray(URL[]::new));
    }
}
