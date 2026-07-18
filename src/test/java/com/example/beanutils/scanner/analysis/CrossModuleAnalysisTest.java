package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.BeanUtilsRiskScanner;
import com.example.beanutils.scanner.ScanRequest;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.PropertyMapping;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CrossModuleAnalysisTest {
    @Test
    void resolvesBothDtoOriginsAndGenericPropertiesAcrossModules() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/cross-module").toAbsolutePath();
        var report = new BeanUtilsRiskScanner().scan(new ScanRequest(fixture,
                Path.of(System.getProperty("user.home"), ".m2", "repository"), false));

        var finding = report.findings().get(0);
        assertEquals(FindingStatus.RISK, finding.status());
        assertEquals("fixture.api.SourceDto", finding.sourceType().qualifiedName());
        assertEquals("fixture-api", finding.sourceType().module());
        assertEquals("api/src/main/java/fixture/api/SourceDto.java", finding.sourceType().sourcePath());
        assertEquals("fixture.facade.TargetDto", finding.targetType().qualifiedName());
        assertEquals("fixture-facade", finding.targetType().module());
        assertEquals("facade/src/main/java/fixture/facade/TargetDto.java", finding.targetType().sourcePath());
        assertEquals(FindingStatus.RISK, finding.properties().get(0).status());
        assertEquals("java.util.List<java.lang.String>", finding.properties().get(0).sourceType().qualifiedName());
        assertEquals("java.util.List<java.lang.Long>", finding.properties().get(0).targetType().qualifiedName());
        var inherited = finding.properties().stream()
                .filter(property -> property.propertyName().equals("values"))
                .findFirst().orElse(null);
        assertNotNull(inherited);
        assertEquals(FindingStatus.RISK, inherited.status());
        assertEquals("java.util.List<java.lang.String>", inherited.sourceType().qualifiedName());
        assertEquals("java.util.List<java.lang.Long>", inherited.targetType().qualifiedName());
        assertEquals("fixture.api.GenericBase", inherited.sourceDeclaringType());
        assertEquals("fixture.api.GenericBase", inherited.targetDeclaringType());
        assertEquals(PropertyMapping.MAPPED, inherited.mapping());
        assertEquals(PropertyMapping.SOURCE_ONLY, property(finding, "sourceOnly").mapping());
        assertEquals(PropertyMapping.TARGET_ONLY, property(finding, "targetOnly").mapping());
        assertEquals(PropertyMapping.SAME_NAME_NOT_COPYABLE, property(finding, "readOnly").mapping());
        assertEquals(5, finding.properties().size());
    }

    private com.example.beanutils.scanner.model.PropertyFinding property(
            com.example.beanutils.scanner.model.CopyFinding finding, String name) {
        return finding.properties().stream().filter(value -> value.propertyName().equals(name))
                .findFirst().orElseThrow();
    }
}
