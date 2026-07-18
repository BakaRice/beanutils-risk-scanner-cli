package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.discovery.BeanUtilsCallDetector;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.project.MavenProjectLoader;
import com.example.beanutils.scanner.source.SourceIndexer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectCopyAnalyzerTest {
    @Test
    void keepsAPropertyRowWhenTargetHasNoSetter() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/copy-calls").toAbsolutePath();
        var project = new MavenProjectLoader().load(fixture, false,
                Path.of(System.getProperty("user.home"), ".m2", "repository"));
        var workspace = new SourceIndexer().index(project);
        var call = new BeanUtilsCallDetector().discover(workspace).stream()
                .filter(value -> value.code().contains("MissingSetterSource"))
                .findFirst().orElseThrow();

        var finding = new DirectCopyAnalyzer().analyze(call);

        assertEquals(FindingStatus.SAFE, finding.status());
        assertEquals(1, finding.properties().size());
        assertEquals("externalId", finding.properties().get(0).propertyName());
        assertEquals("SKIPPED_NO_SETTER", finding.properties().get(0).newDecision());
    }
}
