package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.discovery.BeanUtilsCallDetector;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.PropertyMapping;
import com.example.beanutils.scanner.project.MavenProjectLoader;
import com.example.beanutils.scanner.source.SourceIndexer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectCopyAnalyzerTest {
    @Test
    void tracesEveryResolvedBeanPropertyOnceWithMissingAccessorEvidence() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/copy-calls").toAbsolutePath();
        var project = new MavenProjectLoader().load(fixture, false,
                Path.of(System.getProperty("user.home"), ".m2", "repository"));
        var workspace = new SourceIndexer().index(project);
        var calls = new BeanUtilsCallDetector().discover(workspace);
        var direct = calls.stream()
                .filter(value -> value.code().equals("BeanUtils.copyProperties(source, target)"))
                .findFirst().orElseThrow();
        var missingSetter = calls.stream()
                .filter(value -> value.code().contains("MissingSetterSource"))
                .findFirst().orElseThrow();
        var methodReference = calls.stream()
                .filter(value -> value.form() == com.example.beanutils.scanner.discovery.CopyCallForm.METHOD_REFERENCE)
                .findFirst().orElseThrow();
        var lines = new ArrayList<String>();
        var analyzer = new DirectCopyAnalyzer(lines::add);

        analyzer.analyze(direct);
        analyzer.analyze(direct);
        analyzer.analyze(missingSetter);
        var methodReferenceFinding = analyzer.analyze(methodReference);

        assertEquals(1, count(lines, "[BeanUtilsScanner][BEAN] type=example.CopyCalls.Source "));
        assertEquals(1, count(lines, "[BeanUtilsScanner][BEAN] type=example.CopyCalls.Target "));
        assertTrue(lines.stream().anyMatch(line -> line.contains("[PROPERTY] bean=example.CopyCalls.Source name=name")
                && line.contains("readType=java.lang.String writeType=-")
                && line.contains("getterOwner=example.CopyCalls.Source setterOwner=-")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("[PROPERTY] bean=example.CopyCalls.Target name=name")
                && line.contains("readType=- writeType=java.lang.String")
                && line.contains("getterOwner=- setterOwner=example.CopyCalls.Target")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("bean=example.CopyCalls.MissingSetterTarget name=externalId")
                && line.contains("readType=java.lang.String writeType=-")
                && line.contains("setterOwner=-")));
        assertTrue(lines.stream().anyMatch(line -> line.equals(
                "[BeanUtilsScanner][BEAN-END] type=example.CopyCalls.Source properties=2")));
        assertTrue(lines.stream().noneMatch(line -> line.contains(" name=class ")));
        assertTrue(lines.stream().anyMatch(line -> line.equals(
                "[BeanUtilsScanner][BEAN-ERROR] type=? reason=method-reference-no-concrete-bean-types")));
        assertEquals("METHOD_REFERENCE_TYPES_UNKNOWN", methodReferenceFinding.reviewReasons().get(0).code());
        assertTrue(methodReferenceFinding.reviewReasons().get(0).message().contains("实际的 Source Bean 和 Target Bean 类型"));
    }

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

    private long count(ArrayList<String> lines, String prefix) {
        return lines.stream().filter(line -> line.startsWith(prefix)).count();
    }
}
