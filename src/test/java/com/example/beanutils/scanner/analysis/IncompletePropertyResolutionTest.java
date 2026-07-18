package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.BeanUtilsRiskScanner;
import com.example.beanutils.scanner.ScanRequest;
import com.example.beanutils.scanner.model.FindingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncompletePropertyResolutionTest {
    @TempDir Path project;

    @Test
    void reportsReviewWhenAnUnavailableParentPreventsCompletePropertyInspection() throws Exception {
        writeProject();
        var trace = new ArrayList<String>();

        var report = new BeanUtilsRiskScanner().scan(new ScanRequest(project,
                Path.of(System.getProperty("user.home"), ".m2", "repository"), false), trace::add);

        assertEquals(1, report.findings().size());
        assertEquals(FindingStatus.REVIEW, report.findings().get(0).status());
        assertTrue(trace.stream().anyMatch(line -> line.contains("[BEAN-ERROR] type=fixture.Source")
                && line.contains("reason=property-model-incomplete")));
    }

    private void writeProject() throws Exception {
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId><artifactId>incomplete-parent</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-beans</artifactId><version>5.0.7.RELEASE</version></dependency></dependencies>
                </project>
                """);
        Path source = Files.createDirectories(project.resolve("src/main/java/fixture"));
        Files.writeString(source.resolve("Source.java"), """
                package fixture;
                public class Source extends missing.ExternalBase {
                    private String local;
                    public String getLocal() { return local; }
                    public void setLocal(String local) { this.local = local; }
                }
                """);
        Files.writeString(source.resolve("Target.java"), """
                package fixture;
                public class Target extends missing.ExternalBase {
                    private String local;
                    public String getLocal() { return local; }
                    public void setLocal(String local) { this.local = local; }
                }
                """);
        Files.writeString(source.resolve("CopyService.java"), """
                package fixture;
                import org.springframework.beans.BeanUtils;
                public class CopyService {
                    void copy(Source source, Target target) {
                        BeanUtils.copyProperties(source, target);
                    }
                }
                """);
    }
}
