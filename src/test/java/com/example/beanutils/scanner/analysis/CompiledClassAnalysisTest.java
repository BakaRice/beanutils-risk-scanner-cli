package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.BeanUtilsRiskScanner;
import com.example.beanutils.scanner.ScanRequest;
import com.example.beanutils.scanner.model.FindingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledClassAnalysisTest {
    @TempDir Path project;

    @Test
    void readsGeneratedAccessorsFromClassFilesWhenTheyDoNotExistInSource() throws Exception {
        writeProjectSources();
        compilePostProcessedBeans();
        var trace = new ArrayList<String>();

        var report = new BeanUtilsRiskScanner().scan(new ScanRequest(project,
                Path.of(System.getProperty("user.home"), ".m2", "repository"), false, true), trace::add);

        var finding = report.findings().get(0);
        assertEquals(FindingStatus.RISK, finding.status());
        assertEquals("values", finding.properties().get(0).propertyName());
        assertEquals("java.util.List<java.lang.String>", finding.properties().get(0).sourceType().qualifiedName());
        assertEquals("java.util.List<java.lang.Integer>", finding.properties().get(0).targetType().qualifiedName());
        assertTrue(trace.stream().anyMatch(line -> line.contains("type=fixture.GeneratedSource")
                && line.contains("evidence=compiled-class")));
        assertTrue(trace.stream().anyMatch(line -> line.contains("bean=fixture.GeneratedSource name=values")
                && line.contains("readType=java.util.List<java.lang.String>")));
    }

    private void writeProjectSources() throws Exception {
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId><artifactId>compiled-beans</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-beans</artifactId><version>5.0.7.RELEASE</version></dependency></dependencies>
                </project>
                """);
        Path source = Files.createDirectories(project.resolve("src/main/java/fixture"));
        Files.writeString(source.resolve("GeneratedSource.java"), """
                package fixture;
                public class GeneratedSource { private java.util.List<String> values; }
                """);
        Files.writeString(source.resolve("GeneratedTarget.java"), """
                package fixture;
                public class GeneratedTarget { private java.util.List<Integer> values; }
                """);
        Files.writeString(source.resolve("CopyService.java"), """
                package fixture;
                import org.springframework.beans.BeanUtils;
                public class CopyService {
                    void copy(GeneratedSource source, GeneratedTarget target) {
                        BeanUtils.copyProperties(source, target);
                    }
                }
                """);
    }

    private void compilePostProcessedBeans() throws Exception {
        Path generated = Files.createDirectories(project.resolve("generated/fixture"));
        Path output = Files.createDirectories(project.resolve("target/classes"));
        Path source = generated.resolve("GeneratedSource.java");
        Path target = generated.resolve("GeneratedTarget.java");
        Files.writeString(source, """
                package fixture;
                public class GeneratedSource {
                    private java.util.List<String> values;
                    public java.util.List<String> getValues() { return values; }
                    public void setValues(java.util.List<String> values) { this.values = values; }
                }
                """);
        Files.writeString(target, """
                package fixture;
                public class GeneratedTarget {
                    private java.util.List<Integer> values;
                    public java.util.List<Integer> getValues() { return values; }
                    public void setValues(java.util.List<Integer> values) { this.values = values; }
                }
                """);
        int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", output.toString(), source.toString(), target.toString());
        assertEquals(0, exit);
    }
}
