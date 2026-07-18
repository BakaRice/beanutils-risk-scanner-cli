package com.example.beanutils.scanner.report;

import com.example.beanutils.scanner.model.CopyFinding;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.PropertyFinding;
import com.example.beanutils.scanner.model.PropertyMapping;
import com.example.beanutils.scanner.model.ScanReport;
import com.example.beanutils.scanner.model.SourceLocation;
import com.example.beanutils.scanner.model.TypeRef;
import com.example.beanutils.scanner.model.ReviewReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                List.of(
                        new PropertyFinding("items",
                                new TypeRef("List<String>", "java.util.List<java.lang.String>", true),
                                new TypeRef("List<Long>", "java.util.List<java.lang.Long>", true),
                                "example.BaseSource", "example.BaseTarget", PropertyMapping.MAPPED,
                                FindingStatus.RISK, true,
                                "REJECTED", "泛型参数不兼容"),
                        new PropertyFinding("sourceOnly",
                                new TypeRef("String", "java.lang.String", true),
                                TypeRef.unresolved("Target 中不存在"),
                                "example.Source", "", PropertyMapping.SOURCE_ONLY,
                                FindingStatus.SAFE, false,
                                "NO_SAME_NAME_TARGET", "Source 独有属性"),
                        new PropertyFinding("targetOnly",
                                TypeRef.unresolved("Source 中不存在"),
                                new TypeRef("Long", "java.lang.Long", true),
                                "", "example.Target", PropertyMapping.TARGET_ONLY,
                                FindingStatus.SAFE, false,
                                "NO_SAME_NAME_SOURCE", "Target 独有属性"),
                        new PropertyFinding("readOnly",
                                TypeRef.unresolved("unresolved<SourceType>"),
                                new TypeRef("String", "java.lang.String", true),
                                "example.Source", "example.Target", PropertyMapping.SAME_NAME_NOT_COPYABLE,
                                FindingStatus.REVIEW, false,
                                "UNKNOWN", "类型信息不完整且 Target 缺少 <setter>")),
                List.of(), "DIRECT", "Facade");
        var report = new ScanReport("/project/<unsafe>", "2026-07-18T00:00:00+08:00", List.of(finding), List.of());
        Path htmlPath = temp.resolve("report audit#1.html");
        Path jsonPath = temp.resolve("report.json");
        Path detailDirectory = temp.resolve("report audit#1-details");
        Files.createDirectories(detailDirectory);
        Files.writeString(detailDirectory.resolve("finding-9999.html"), "stale");

        var secondFinding = new CopyFinding(FindingStatus.REVIEW,
                new SourceLocation("Domain", "Domain/src/B.java", 28, 5),
                "BeanUtils.copyProperties(otherSource, otherTarget)",
                finding.sourceType(), finding.targetType(), finding.properties(),
                List.of(), "DIRECT", "Domain", List.of(
                        new ReviewReason("SOURCE_PROPERTY_MODEL_INCOMPLETE",
                                "Source Bean example.Source 的父类 com.example.MissingBase 无法加载",
                                "SOURCE"),
                        new ReviewReason("RAW_GENERIC_PROPERTY",
                                "同名属性 items 使用 raw type，泛型参数已经丢失", "PROPERTY", "items")));
        report = new ScanReport(report.projectPath(), report.generatedAt(), List.of(finding, secondFinding), List.of());

        new HtmlReportWriter().write(report, htmlPath);
        new JsonReportWriter().write(report, jsonPath);

        String html = Files.readString(htmlPath);
        String json = Files.readString(jsonPath);
        Path detailPath = detailDirectory.resolve("finding-0001.html");
        String detail = Files.readString(detailPath);
        assertTrue(html.contains("<th>结论</th><th>代码位置</th><th>Source 类型</th><th>Target 类型</th>"));
        assertTrue(html.contains("data-filter=\"RISK\""));
        assertTrue(html.contains("id=\"search\""));
        assertTrue(html.contains("id=\"findings-list\""));
        assertFalse(html.contains("id=\"detail-panel\""));
        assertTrue(html.contains("id=\"module-filter\""));
        assertTrue(html.contains("href=\"report%20audit%231-details/finding-0001.html\""));
        assertTrue(html.contains("href=\"report%20audit%231-details/finding-0002.html\""));
        assertTrue(html.contains("查看详情"));
        assertTrue(html.contains("aria-live=\"polite\""));
        assertTrue(html.contains("aria-pressed=\"true\""));
        assertTrue(html.contains("setAttribute('aria-pressed'"));
        assertFalse(html.contains("<tr tabindex=\"0\" role=\"link\""));
        assertTrue(html.contains("Source 独有"));
        assertTrue(html.contains("Target 独有"));
        assertTrue(html.contains("同名不可复制"));
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
        assertTrue(json.contains("\"reviewReasons\""));
        assertTrue(json.contains("\"code\" : \"SOURCE_PROPERTY_MODEL_INCOMPLETE\""));
        assertTrue(json.contains("com.example.MissingBase"));

        assertTrue(Files.isDirectory(detailDirectory));
        try (var detailFiles = Files.list(detailDirectory)) {
            assertEquals(List.of("finding-0001.html", "finding-0002.html"), detailFiles
                    .map(path -> path.getFileName().toString()).sorted().toList());
        }
        assertFalse(Files.exists(detailDirectory.resolve("finding-9999.html")));
        assertFalse(detail.contains("id=\"source-properties\""));
        assertFalse(detail.contains("id=\"target-properties\""));
        assertFalse(detail.contains("id=\"property-mappings\""));
        assertTrue(detail.contains("id=\"bean-property-union\""));
        assertTrue(detail.contains("Bean 属性并集 · 4 个"));
        assertEquals(1, occurrences(detail, "<table class=\"property-table union-table\""));
        assertTrue(detail.contains("<th>属性名</th><th>Source 字段</th><th>Target 字段</th><th>映射关系</th><th>Spring 5.0.7</th><th>Spring 5.3.1 结论与原因</th>"));
        for (String propertyName : List.of("items", "sourceOnly", "targetOnly", "readOnly")) {
            assertEquals(1, occurrences(detail, "data-property=\"" + propertyName + "\""));
        }
        String itemsRow = propertyRow(detail, "items");
        assertTrue(itemsRow.contains("java.util.List&lt;java.lang.String&gt;"));
        assertTrue(itemsRow.contains("java.util.List&lt;java.lang.Long&gt;"));
        assertTrue(itemsRow.contains("声明于 example.BaseSource"));
        assertTrue(itemsRow.contains("声明于 example.BaseTarget"));
        assertTrue(itemsRow.contains("原始类型可赋值"));
        assertTrue(itemsRow.contains("<span class=\"status risk\">RISK</span>"));
        String sourceOnlyRow = propertyRow(detail, "sourceOnly");
        assertTrue(sourceOnlyRow.contains("java.lang.String"));
        assertEquals(1, occurrences(sourceOnlyRow, "— 不存在"));
        assertTrue(sourceOnlyRow.contains("Source 独有"));
        String targetOnlyRow = propertyRow(detail, "targetOnly");
        assertTrue(targetOnlyRow.contains("java.lang.Long"));
        assertEquals(1, occurrences(targetOnlyRow, "— 不存在"));
        assertTrue(targetOnlyRow.contains("Target 独有"));
        String readOnlyRow = propertyRow(detail, "readOnly");
        assertTrue(readOnlyRow.contains("同名但不可复制"));
        assertTrue(readOnlyRow.contains("unresolved&lt;SourceType&gt;"));
        assertFalse(readOnlyRow.contains("— 不存在"));
        assertTrue(readOnlyRow.contains("<span class=\"status review\">REVIEW</span>"));
        assertTrue(readOnlyRow.contains("类型信息不完整且 Target 缺少 &lt;setter&gt;"));
        assertFalse(readOnlyRow.contains("类型信息不完整且 Target 缺少 <setter>"));
        assertTrue(detail.contains("href=\"../report%20audit%231.html\""));
        assertTrue(detail.contains("href=\"finding-0002.html\""));
        String secondDetail = Files.readString(detailDirectory.resolve("finding-0002.html"));
        assertTrue(html.contains("Source Bean example.Source 的父类 com.example.MissingBase 无法加载"));
        assertTrue(secondDetail.contains("id=\"review-reasons\""));
        assertTrue(secondDetail.contains("SOURCE_PROPERTY_MODEL_INCOMPLETE"));
        assertTrue(secondDetail.contains("RAW_GENERIC_PROPERTY"));
        assertTrue(secondDetail.contains("同名属性 items 使用 raw type，泛型参数已经丢失"));
        assertTrue(secondDetail.contains("href=\"finding-0001.html\""));
        assertTrue(secondDetail.contains("下一个调用 →</span>"));
        assertFalse(detail.contains("http://"));
        assertFalse(detail.contains("https://"));
    }

    private String propertyRow(String html, String propertyName) {
        String marker = "<tr data-property=\"" + propertyName + "\"";
        int start = html.indexOf(marker);
        int end = html.indexOf("</tr>", start);
        return start < 0 || end < 0 ? "" : html.substring(start, end + 5);
    }

    private int occurrences(String value, String needle) {
        int count = 0;
        for (int index = value.indexOf(needle); index >= 0; index = value.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }
}
