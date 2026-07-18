package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.discovery.BeanUtilsCallDetector;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.PropertyMapping;
import com.example.beanutils.scanner.project.MavenProjectLoader;
import com.example.beanutils.scanner.source.SourceIndexer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectCopyAnalyzerTest {
    @Test
    void reportsMappedAndUnmatchedPropertiesForSafeCopy() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/copy-calls").toAbsolutePath();
        var project = new MavenProjectLoader().load(fixture, false,
                Path.of(System.getProperty("user.home"), ".m2", "repository"));
        var workspace = new SourceIndexer().index(project);
        var call = new BeanUtilsCallDetector().discover(workspace).stream()
                .filter(value -> value.code().equals("BeanUtils.copyProperties(source, target)"))
                .findFirst().orElseThrow();

        var finding = new DirectCopyAnalyzer().analyze(call);

        assertEquals(FindingStatus.SAFE, finding.status());
        assertEquals(PropertyMapping.MAPPED, property(finding, "name").mapping());
        assertEquals(PropertyMapping.SOURCE_ONLY, property(finding, "sourceOnly").mapping());
        assertEquals(PropertyMapping.TARGET_ONLY, property(finding, "targetOnly").mapping());
        assertEquals(3, finding.properties().size());
    }

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
        assertEquals(PropertyMapping.SAME_NAME_NOT_COPYABLE, finding.properties().get(0).mapping());
        assertEquals("SKIPPED_NO_SETTER", finding.properties().get(0).newDecision());
    }

    private com.example.beanutils.scanner.model.PropertyFinding property(
            com.example.beanutils.scanner.model.CopyFinding finding, String name) {
        return finding.properties().stream().filter(value -> value.propertyName().equals(name))
                .findFirst().orElseThrow();
    }
}
