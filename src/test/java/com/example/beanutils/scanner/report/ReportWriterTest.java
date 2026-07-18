package com.example.beanutils.scanner.report;

import com.example.beanutils.scanner.model.CopyFinding;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.PropertyFinding;
import com.example.beanutils.scanner.model.PropertyMapping;
import com.example.beanutils.scanner.model.ScanReport;
import com.example.beanutils.scanner.model.SourceLocation;
import com.example.beanutils.scanner.model.TypeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {
    @TempDir Path temp;

    @Test
    void writesJsonAndOfflineHtmlWithConclusionFirst() throws Exception {
        var finding = new CopyFinding(FindingStatus.RISK,
                new SourceLocation("Facade", "Facade/src/A.java", 12, 9),
                "BeanUtils.copyProperties(source, target)",
                new TypeRef("Source", "example.Source<java.lang.String>", true),
                new TypeRef("Target", "example.Target<java.lang.Long>", true),
                List.of(new PropertyFinding("items",
                        new TypeRef("List<String>", "java.util.List<java.lang.String>", true),
                        new TypeRef("List<Long>", "java.util.List<java.lang.Long>", true),
                        "example.BaseSource", "example.BaseTarget", PropertyMapping.MAPPED,
                        FindingStatus.RISK, true,
                        "REJECTED", "泛型参数不兼容")),
                List.of(), "DIRECT", "Facade");
        var report = new ScanReport("/project/<unsafe>", "2026-07-18T00:00:00+08:00", List.of(finding), List.of());
        Path htmlPath = temp.resolve("report.html");
        Path jsonPath = temp.resolve("report.json");

        new HtmlReportWriter().write(report, htmlPath);
        new JsonReportWriter().write(report, jsonPath);

        String html = Files.readString(htmlPath);
        String json = Files.readString(jsonPath);
        assertTrue(html.contains("<th>结论</th><th>代码位置</th><th>Source 类型</th><th>Target 类型</th>"));
        assertTrue(html.contains("data-filter=\"RISK\""));
        assertTrue(html.contains("id=\"search\""));
        assertTrue(html.contains("id=\"findings-list\""));
        assertTrue(html.contains("id=\"detail-panel\""));
        assertTrue(html.contains("id=\"module-filter\""));
        assertTrue(html.contains("id=\"property-problem-only\""));
        assertTrue(html.contains("aria-live=\"polite\""));
        assertTrue(html.contains("声明于"));
        assertTrue(html.contains("Source 独有"));
        assertTrue(html.contains("Target 独有"));
        assertTrue(html.contains("同名但不可复制"));
        assertTrue(html.contains("propertyMode='ALL'"));
        assertTrue(html.contains("example.BaseSource"));
        assertTrue(html.contains("example.BaseTarget"));
        assertTrue(html.contains("<td class=\"location-cell\" title=\"Facade/src/A.java:12\">"));
        assertTrue(html.contains("<strong class=\"file\">A.java:12</strong><small class=\"location-module\">Facade</small>"));
        assertFalse(html.contains("Facade · Facade/src/A.java"));
        assertFalse(html.contains("<details>"));
        assertFalse(html.contains("http://"));
        assertFalse(html.contains("https://"));
        assertTrue(html.contains("/project/&lt;unsafe&gt;"));
        assertTrue(json.contains("example.Source<java.lang.String>"));
        assertTrue(json.contains("example.Target<java.lang.Long>"));
        assertTrue(json.contains("\"sourceDeclaringType\" : \"example.BaseSource\""));
        assertTrue(json.contains("\"targetDeclaringType\" : \"example.BaseTarget\""));
    }
}
