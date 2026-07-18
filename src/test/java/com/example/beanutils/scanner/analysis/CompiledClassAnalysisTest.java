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

    @Test
    void fallsBackToSourceAndContinuesWhenACompiledMethodSignatureHasAMissingClass() throws Exception {
        writeMissingSignatureProject();
        compileMissingSignatureBeans();
        Files.delete(project.resolve("target/classes/fixture/MissingType.class"));
        var trace = new ArrayList<String>();

        var report = new BeanUtilsRiskScanner().scan(new ScanRequest(project,
                Path.of(System.getProperty("user.home"), ".m2", "repository"), false, true), trace::add);

        assertEquals(2, report.findings().size());
        assertTrue(report.findings().stream().anyMatch(finding -> finding.properties().stream()
                .anyMatch(property -> property.propertyName().equals("missing"))));
        assertTrue(report.findings().stream().anyMatch(finding -> finding.properties().stream()
                .anyMatch(property -> property.propertyName().equals("name"))));
        assertTrue(trace.stream().anyMatch(line -> line.contains("type=fixture.BrokenSource")
                && line.contains("evidence=source-fallback")));
        assertTrue(trace.stream().anyMatch(line -> line.contains("[CLASS-FALLBACK] type=fixture.BrokenSource")
                && line.contains("missing=fixture.MissingType")));
        assertTrue(trace.stream().anyMatch(line -> line.contains("type=fixture.HealthySource")
                && line.contains("evidence=compiled-class")));
    }

    @Test
    void reportsReviewInsteadOfExitingWhenABeanImplementsMissingMybatisPlusService() throws Exception {
        writeMissingFrameworkInterfaceProject();
        compileMissingFrameworkInterfaceProject();
        Files.delete(project.resolve("target/classes/com/baomidou/mybatisplus/service/IService.class"));
        Files.delete(project.resolve("src/main/java/com/baomidou/mybatisplus/service/IService.java"));
        var trace = new ArrayList<String>();

        var report = new BeanUtilsRiskScanner().scan(new ScanRequest(project,
                Path.of(System.getProperty("user.home"), ".m2", "repository"), false, true), trace::add);

        assertEquals(1, report.findings().size());
        assertEquals(FindingStatus.REVIEW, report.findings().get(0).status());
        assertTrue(trace.stream().anyMatch(line -> line.contains("type=fixture.MybatisServiceBean")
                && (line.contains("CLASS-FALLBACK") || line.contains("property-model-incomplete"))));
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

    private void writeMissingSignatureProject() throws Exception {
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId><artifactId>missing-signature</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-beans</artifactId><version>5.0.7.RELEASE</version></dependency></dependencies>
                </project>
                """);
        Path source = Files.createDirectories(project.resolve("src/main/java/fixture"));
        Files.writeString(source.resolve("MissingType.java"), "package fixture; public class MissingType {}\n");
        Files.writeString(source.resolve("BrokenSource.java"), """
                package fixture;
                public class BrokenSource {
                    private java.util.List<MissingType> missing;
                    public java.util.List<MissingType> getMissing() { return missing; }
                    public void setMissing(java.util.List<MissingType> missing) { this.missing = missing; }
                }
                """);
        Files.writeString(source.resolve("BrokenTarget.java"), """
                package fixture;
                public class BrokenTarget {
                    private java.util.List<MissingType> missing;
                    public java.util.List<MissingType> getMissing() { return missing; }
                    public void setMissing(java.util.List<MissingType> missing) { this.missing = missing; }
                }
                """);
        Files.writeString(source.resolve("HealthySource.java"), beanWithStringProperty("HealthySource"));
        Files.writeString(source.resolve("HealthyTarget.java"), beanWithStringProperty("HealthyTarget"));
        Files.writeString(source.resolve("CopyService.java"), """
                package fixture;
                import org.springframework.beans.BeanUtils;
                public class CopyService {
                    void copy(BrokenSource brokenSource, BrokenTarget brokenTarget,
                              HealthySource healthySource, HealthyTarget healthyTarget) {
                        BeanUtils.copyProperties(brokenSource, brokenTarget);
                        BeanUtils.copyProperties(healthySource, healthyTarget);
                    }
                }
                """);
    }

    private void compileMissingSignatureBeans() throws Exception {
        Path source = project.resolve("src/main/java/fixture");
        Path output = Files.createDirectories(project.resolve("target/classes"));
        int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", output.toString(),
                source.resolve("MissingType.java").toString(),
                source.resolve("BrokenSource.java").toString(),
                source.resolve("BrokenTarget.java").toString(),
                source.resolve("HealthySource.java").toString(),
                source.resolve("HealthyTarget.java").toString());
        assertEquals(0, exit);
    }

    private String beanWithStringProperty(String name) {
        return """
                package fixture;
                public class %s {
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
                """.formatted(name);
    }

    private void writeMissingFrameworkInterfaceProject() throws Exception {
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId><artifactId>missing-mybatis-plus</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-beans</artifactId><version>5.0.7.RELEASE</version></dependency></dependencies>
                </project>
                """);
        Path framework = Files.createDirectories(
                project.resolve("src/main/java/com/baomidou/mybatisplus/service"));
        Files.writeString(framework.resolve("IService.java"), """
                package com.baomidou.mybatisplus.service;
                public interface IService<T> {}
                """);
        Path source = Files.createDirectories(project.resolve("src/main/java/fixture"));
        Files.writeString(source.resolve("MybatisServiceBean.java"), """
                package fixture;
                public class MybatisServiceBean
                        implements com.baomidou.mybatisplus.service.IService<String> {
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
                """);
        Files.writeString(source.resolve("Target.java"), beanWithStringProperty("Target"));
        Files.writeString(source.resolve("CopyService.java"), """
                package fixture;
                import org.springframework.beans.BeanUtils;
                public class CopyService {
                    void copy(MybatisServiceBean source, Target target) {
                        BeanUtils.copyProperties(source, target);
                    }
                }
                """);
    }

    private void compileMissingFrameworkInterfaceProject() throws Exception {
        Path source = project.resolve("src/main/java");
        Path output = Files.createDirectories(project.resolve("target/classes"));
        int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", output.toString(),
                source.resolve("com/baomidou/mybatisplus/service/IService.java").toString(),
                source.resolve("fixture/MybatisServiceBean.java").toString(),
                source.resolve("fixture/Target.java").toString());
        assertEquals(0, exit);
    }
}
