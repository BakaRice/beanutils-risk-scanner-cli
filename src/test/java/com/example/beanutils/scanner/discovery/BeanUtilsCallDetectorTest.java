package com.example.beanutils.scanner.discovery;

import com.example.beanutils.scanner.project.MavenProjectLoader;
import com.example.beanutils.scanner.source.SourceIndexer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanUtilsCallDetectorTest {
    @Test
    void discoversSpringCallsAndRejectsNamesakes() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/copy-calls").toAbsolutePath();
        var project = new MavenProjectLoader().load(fixture, false,
                Path.of(System.getProperty("user.home"), ".m2", "repository"));
        var workspace = new SourceIndexer().index(project);

        List<CopyCallSite> calls = new BeanUtilsCallDetector().discover(workspace);

        assertEquals(7, calls.size());
        assertTrue(calls.stream().anyMatch(call -> call.form() == CopyCallForm.IGNORE_PROPERTIES
                && call.ignoredProperties().contains("items") && call.ignoredProperties().contains("tags")));
        assertTrue(calls.stream().anyMatch(call -> call.form() == CopyCallForm.EDITABLE));
        assertTrue(calls.stream().anyMatch(call -> call.form() == CopyCallForm.METHOD_REFERENCE));
        assertTrue(calls.stream().noneMatch(call -> call.code().contains("OtherBeanUtils")));
    }
}
