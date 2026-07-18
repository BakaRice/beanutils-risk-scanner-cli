package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.BeanUtilsRiskScanner;
import com.example.beanutils.scanner.ScanRequest;
import com.example.beanutils.scanner.model.FindingStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    }
}
