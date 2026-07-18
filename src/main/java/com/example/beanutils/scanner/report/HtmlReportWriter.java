package com.example.beanutils.scanner.report;

import com.example.beanutils.scanner.model.CallChainStep;
import com.example.beanutils.scanner.model.CopyFinding;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.PropertyFinding;
import com.example.beanutils.scanner.model.PropertyMapping;
import com.example.beanutils.scanner.model.ScanReport;
import com.example.beanutils.scanner.model.TypeRef;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class HtmlReportWriter {
    public void write(ScanReport report, Path output) throws IOException {
        Path index = output.toAbsolutePath().normalize();
        JsonReportWriter.createParent(index);
        Path detailDirectory = detailDirectory(index);
        prepareDetailDirectory(detailDirectory);

        for (int indexOfFinding = 0; indexOfFinding < report.findings().size(); indexOfFinding++) {
            Path detail = detailDirectory.resolve(detailFileName(indexOfFinding));
            Files.writeString(detail, detailPage(report, indexOfFinding, index.getFileName().toString()),
                    StandardCharsets.UTF_8);
        }
        Files.writeString(index, indexPage(report, detailDirectory.getFileName().toString()), StandardCharsets.UTF_8);
    }

    private Path detailDirectory(Path output) {
        String fileName = output.getFileName().toString();
        int extension = fileName.lastIndexOf('.');
        String base = extension > 0 ? fileName.substring(0, extension) : fileName;
        if (base.isBlank()) {
            base = "report";
        }
        return output.resolveSibling(base + "-details");
    }

    private void prepareDetailDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        try (DirectoryStream<Path> staleDetails = Files.newDirectoryStream(directory, "finding-*.html")) {
            for (Path staleDetail : staleDetails) {
                Files.deleteIfExists(staleDetail);
            }
        }
    }

    private String detailFileName(int zeroBasedIndex) {
        return String.format(Locale.ROOT, "finding-%04d.html", zeroBasedIndex + 1);
    }

    private String indexPage(ScanReport report, String detailDirectoryName) {
        return """
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>BeanUtils 升级风险审计报告</title><style>
                %s
                </style></head><body class="index-page"><main class="page-shell">
                <header class="report-header"><div><div class="eyebrow">SPRING 5.0.7 → 5.3.1</div><h1>BeanUtils 升级风险审计</h1><p class="subtitle">%s</p><p class="generated">生成于 %s</p></div><div class="header-note"><b>逐条审阅</b><span>点击任意一行进入独立详情页</span></div></header>
                <section class="metrics" aria-label="按结论快速筛选">%s</section>
                <section class="controls" aria-label="报告筛选"><label class="search-field"><span>搜索</span><input id="search" type="search" aria-label="搜索报告" placeholder="文件、Module、类型、属性或原因"></label><label><span>调用 Module</span><select id="module-filter" aria-label="按调用 Module 筛选"><option value="ALL">全部 Module</option></select></label><div class="visible-count" aria-live="polite">当前显示 <b id="visible-count">%d</b> / %d</div></section>
                <section class="list-panel" id="findings-list"><table class="findings-table"><thead><tr><th>结论</th><th>代码位置</th><th>Source 类型</th><th>Target 类型</th><th>属性概览</th><th><span class="sr-only">操作</span></th></tr></thead><tbody id="rows">%s</tbody></table><div id="empty" class="empty">没有符合筛选条件的调用</div></section>
                </main><script>%s</script></body></html>
                """.formatted(sharedCss(), html(report.projectPath()), html(report.generatedAt()), metrics(report),
                report.findings().size(), report.findings().size(), rows(report, detailDirectoryName), indexScript());
    }

    private String metrics(ScanReport report) {
        return metric("ALL", "全部调用", report.findings().size(), "all")
                + metric("RISK", "升级风险", report.count(FindingStatus.RISK), "risk")
                + metric("REVIEW", "需要复核", report.count(FindingStatus.REVIEW), "review")
                + metric("IGNORED", "已被排除", report.count(FindingStatus.IGNORED), "ignored")
                + metric("SAFE", "兼容安全", report.count(FindingStatus.SAFE), "safe");
    }

    private String metric(String filter, String label, long count, String tone) {
        return "<button class=\"metric " + tone + ("ALL".equals(filter) ? " active" : "")
                + "\" data-filter=\"" + filter + "\" aria-pressed=\"" + "ALL".equals(filter) + "\"><span>"
                + html(label) + "</span><b>" + count
                + "</b></button>";
    }

    private String rows(ScanReport report, String detailDirectoryName) {
        StringBuilder rows = new StringBuilder();
        for (int index = 0; index < report.findings().size(); index++) {
            CopyFinding finding = report.findings().get(index);
            String href = urlSegment(detailDirectoryName) + "/" + detailFileName(index);
            rows.append("<tr data-href=\"").append(attribute(href))
                    .append("\" data-status=\"").append(finding.status())
                    .append("\" data-module=\"").append(attribute(finding.module()))
                    .append("\" data-search=\"").append(attribute(searchText(finding).toLowerCase(Locale.ROOT)))
                    .append("\">")
                    .append("<td>").append(status(finding.status())).append("</td>")
                    .append("<td class=\"location-cell\" title=\"")
                    .append(attribute(finding.location().relativePath())).append(":")
                    .append(finding.location().line()).append("\"><strong class=\"file\">")
                    .append(html(fileName(finding.location().relativePath()))).append(":")
                    .append(finding.location().line()).append("</strong><small class=\"location-module\">")
                    .append(html(finding.module())).append("</small></td>")
                    .append(typeCell(finding.sourceType())).append(typeCell(finding.targetType()))
                    .append("<td>").append(propertySummary(finding)).append("</td>")
                    .append("<td class=\"open-cell\"><a class=\"detail-link\" href=\"").append(attribute(href))
                    .append("\" aria-label=\"查看详情\">查看详情 <span aria-hidden=\"true\">→</span></a></td></tr>");
        }
        return rows.toString();
    }

    private String detailPage(ScanReport report, int index, String indexFileName) {
        CopyFinding finding = report.findings().get(index);
        List<PropertyFinding> sourceProperties = finding.properties().stream()
                .filter(property -> property.mapping() != PropertyMapping.TARGET_ONLY).toList();
        List<PropertyFinding> targetProperties = finding.properties().stream()
                .filter(property -> property.mapping() != PropertyMapping.SOURCE_ONLY).toList();
        String previous = index > 0 ? navLink(detailFileName(index - 1), "← 上一个调用", "") : disabledNav("← 上一个调用");
        String next = index + 1 < report.findings().size()
                ? navLink(detailFileName(index + 1), "下一个调用 →", "") : disabledNav("下一个调用 →");
        String back = navLink("../" + urlSegment(indexFileName), "← 返回报告列表", "back-link");
        String location = finding.location().relativePath() + ":" + finding.location().line();

        return """
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s · BeanUtils 调用详情</title><style>%s</style></head><body class="detail-page"><main class="detail-shell">
                <nav class="detail-nav" aria-label="详情导航"><div>%s</div><div class="pager">%s%s</div></nav>
                <header class="detail-header"><div class="detail-heading"><div class="detail-kicker">调用 %d / %d</div><div class="detail-title-line">%s<h1>%s</h1></div><p>Module：%s <span>·</span> 调用形式：%s</p></div><div class="verdict-card %s"><span>扫描结论</span><b>%s</b><small>%s</small></div></header>
                <section class="detail-section"><div class="section-heading"><div><span class="section-index">01</span><h2>调用现场</h2></div></div><pre class="code-block"><code>%s</code></pre><div class="bean-types">%s%s</div></section>
                <section class="detail-section property-section" id="source-properties"><div class="section-heading"><div><span class="section-index">02</span><h2>Source Bean 全部属性 · %d 个</h2></div><p>不论是否存在 Target 同名属性，全部列出</p></div>%s</section>
                <section class="detail-section property-section" id="target-properties"><div class="section-heading"><div><span class="section-index">03</span><h2>Target Bean 全部属性 · %d 个</h2></div><p>不论是否存在 Source 同名属性，全部列出</p></div>%s</section>
                <section class="detail-section" id="property-mappings"><div class="section-heading"><div><span class="section-index">04</span><h2>属性映射对照 · %d 个</h2></div><p>完整属性并集与 Spring 5.3.1 判定</p></div>%s</section>
                <section class="detail-section"><div class="section-heading"><div><span class="section-index">05</span><h2>调用链</h2></div></div>%s</section>
                <footer class="detail-footer">%s<div class="pager">%s%s</div></footer>
                </main></body></html>
                """.formatted(html(location), sharedCss(), back, previous, next, index + 1, report.findings().size(),
                status(finding.status()), html(location), html(finding.module().isBlank() ? "-" : finding.module()),
                html(finding.callForm()), finding.status().name().toLowerCase(Locale.ROOT), finding.status(),
                html(statusDescription(finding.status())), html(finding.code()),
                typeCard("Source Bean", finding.sourceType()), typeCard("Target Bean", finding.targetType()),
                sourceProperties.size(), sidePropertyTable(sourceProperties, true), targetProperties.size(),
                sidePropertyTable(targetProperties, false), finding.properties().size(), mappingTable(finding),
                callChain(finding), back, previous, next);
    }

    private String sidePropertyTable(List<PropertyFinding> properties, boolean sourceSide) {
        if (properties.isEmpty()) {
            return "<div class=\"property-empty\"><b>未能列出属性</b><span>该 Bean 类型或属性未能完整解析，请结合调用代码人工复核。</span></div>";
        }
        String side = sourceSide ? "source" : "target";
        StringBuilder body = new StringBuilder();
        for (PropertyFinding property : properties) {
            TypeRef type = sourceSide ? property.sourceType() : property.targetType();
            String owner = sourceSide ? property.sourceDeclaringType() : property.targetDeclaringType();
            body.append("<tr data-").append(side).append("-property=\"")
                    .append(attribute(property.propertyName())).append("\"><td><strong>")
                    .append(html(property.propertyName())).append("</strong></td><td><code>")
                    .append(html(type.qualifiedName())).append("</code><small>")
                    .append(owner == null || owner.isBlank() ? "声明类未解析" : "声明于 " + html(owner))
                    .append("</small></td><td>").append(mapping(property.mapping())).append("</td><td>")
                    .append(status(property.status())).append("</td><td class=\"reason-cell\">")
                    .append(html(property.reason())).append("</td></tr>");
        }
        return "<div class=\"table-scroll\"><table class=\"property-table side-table\"><thead><tr><th>属性名</th><th>属性类型与来源</th><th>映射关系</th><th>结论</th><th>说明</th></tr></thead><tbody>"
                + body + "</tbody></table></div>";
    }

    private String mappingTable(CopyFinding finding) {
        if (finding.properties().isEmpty()) {
            return "<div class=\"property-empty\"><b>属性映射无法确定</b><span>当前调用的类型解析不完整，因此没有足够证据形成映射结论。</span></div>";
        }
        StringBuilder body = new StringBuilder();
        for (PropertyFinding property : finding.properties()) {
            body.append("<tr><td>").append(status(property.status())).append("</td><td>")
                    .append(mapping(property.mapping())).append("</td><td><strong>")
                    .append(html(property.propertyName())).append("</strong></td><td>")
                    .append(propertyType(property.sourceType(), property.sourceDeclaringType())).append("</td><td>")
                    .append(propertyType(property.targetType(), property.targetDeclaringType())).append("</td><td>")
                    .append(oldVersionResult(property)).append("</td><td><b class=\"decision\">")
                    .append(html(property.newDecision())).append("</b><span class=\"reason\">")
                    .append(html(property.reason())).append("</span></td></tr>");
        }
        return "<div class=\"table-scroll\"><table class=\"property-table mapping-table\"><thead><tr><th>结论</th><th>映射关系</th><th>属性名</th><th>Source 属性</th><th>Target 属性</th><th>Spring 5.0.7</th><th>Spring 5.3.1 判定与原因</th></tr></thead><tbody>"
                + body + "</tbody></table></div>";
    }

    private String oldVersionResult(PropertyFinding property) {
        if (property.mapping() != PropertyMapping.MAPPED) {
            return "<span class=\"old-result muted\">不参与复制</span>";
        }
        return property.oldAssignable()
                ? "<span class=\"old-result old-copy\">原始类型可赋值</span>"
                : "<span class=\"old-result muted\">原始类型不可赋值</span>";
    }

    private String typeCard(String label, TypeRef type) {
        String origin = List.of(type.module(), type.sourcePath()).stream().filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " · " + right).orElse("来源未解析");
        return "<article class=\"bean-type-card\"><span>" + html(label) + "</span><b>" + html(type.displayName())
                + "</b><code>" + html(type.qualifiedName()) + "</code><small>" + html(origin) + "</small></article>";
    }

    private String callChain(CopyFinding finding) {
        if (finding.callChain().isEmpty()) {
            return "<div class=\"property-empty\"><b>没有调用链信息</b></div>";
        }
        StringBuilder chain = new StringBuilder("<ol class=\"call-chain\">");
        for (CallChainStep step : finding.callChain()) {
            chain.append("<li><span></span><div><b>").append(html(step.method())).append("</b><small>")
                    .append(html(step.location().relativePath())).append(":").append(step.location().line())
                    .append("</small></div></li>");
        }
        return chain.append("</ol>").toString();
    }

    private String navLink(String href, String label, String cssClass) {
        return "<a class=\"nav-link " + cssClass + "\" href=\"" + attribute(href) + "\">" + html(label) + "</a>";
    }

    private String disabledNav(String label) {
        return "<span class=\"nav-link disabled\">" + html(label) + "</span>";
    }

    private String searchText(CopyFinding finding) {
        StringBuilder search = new StringBuilder().append(finding.status()).append(' ')
                .append(finding.location().relativePath()).append(' ').append(finding.module()).append(' ')
                .append(finding.sourceType().qualifiedName()).append(' ').append(finding.sourceType().module()).append(' ')
                .append(finding.targetType().qualifiedName()).append(' ').append(finding.targetType().module()).append(' ')
                .append(finding.code());
        for (PropertyFinding property : finding.properties()) {
            search.append(' ').append(property.propertyName()).append(' ').append(property.status()).append(' ')
                    .append(property.mapping()).append(' ').append(property.sourceType().qualifiedName()).append(' ')
                    .append(property.targetType().qualifiedName()).append(' ').append(property.reason());
        }
        return search.toString();
    }

    private String propertySummary(CopyFinding finding) {
        if (finding.properties().isEmpty()) {
            return "<span class=\"summary muted\">属性解析不完整，进入详情复核</span>";
        }
        long problems = finding.properties().stream().filter(property -> property.status() != FindingStatus.SAFE).count();
        long mapped = count(finding, PropertyMapping.MAPPED);
        long sourceOnly = count(finding, PropertyMapping.SOURCE_ONLY);
        long targetOnly = count(finding, PropertyMapping.TARGET_ONLY);
        long notCopyable = count(finding, PropertyMapping.SAME_NAME_NOT_COPYABLE);
        String headline = problems > 0 ? problems + " 个需关注" : mapped + " 个同名映射";
        return "<strong class=\"summary "+ (problems > 0 ? "problem-text" : "safe-text") + "\">" + headline
                + "</strong><small>完整并集 " + finding.properties().size() + " · Source 独有 " + sourceOnly
                + " · Target 独有 " + targetOnly + " · 同名不可复制 " + notCopyable + "</small>";
    }

    private long count(CopyFinding finding, PropertyMapping mapping) {
        return finding.properties().stream().filter(property -> property.mapping() == mapping).count();
    }

    private String typeCell(TypeRef type) {
        return "<td><strong class=\"type-name\">" + html(type.displayName()) + "</strong>"
                + (type.module().isBlank() ? "" : "<span class=\"module-chip\">" + html(type.module()) + "</span>")
                + "<small title=\"" + attribute(type.qualifiedName()) + "\">" + html(type.qualifiedName()) + "</small></td>";
    }

    private String propertyType(TypeRef type, String owner) {
        return "<code>" + html(type.qualifiedName()) + "</code>"
                + (owner == null || owner.isBlank() ? "" : "<small>声明于 " + html(owner) + "</small>");
    }

    private String status(FindingStatus status) {
        return "<span class=\"status " + status.name().toLowerCase(Locale.ROOT) + "\">" + status + "</span>";
    }

    private String mapping(PropertyMapping mapping) {
        String label = switch (mapping) {
            case MAPPED -> "同名已映射";
            case SAME_NAME_NOT_COPYABLE -> "同名但不可复制";
            case SOURCE_ONLY -> "Source 独有";
            case TARGET_ONLY -> "Target 独有";
        };
        return "<span class=\"mapping " + mapping.name().toLowerCase(Locale.ROOT) + "\">" + label + "</span>";
    }

    private String statusDescription(FindingStatus status) {
        return switch (status) {
            case RISK -> "Spring 5.3.1 下存在属性不再兼容的风险";
            case REVIEW -> "类型或调用信息未能完整解析，需要人工确认";
            case IGNORED -> "不兼容属性已被当前调用显式排除";
            case SAFE -> "未发现由本次泛型规则变化引起的复制风险";
        };
    }

    private String fileName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String attribute(String value) {
        return html(value == null ? "" : value).replace("\n", " ").replace("\r", " ");
    }

    private String urlSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String indexScript() {
        return """
                (()=>{const rows=[...document.querySelectorAll('#rows>tr')],search=document.querySelector('#search'),moduleFilter=document.querySelector('#module-filter'),empty=document.querySelector('#empty'),visible=document.querySelector('#visible-count');let statusFilter='ALL';
                [...new Set(rows.map(row=>row.dataset.module).filter(Boolean))].sort().forEach(module=>{const option=document.createElement('option');option.value=module;option.textContent=module;moduleFilter.append(option)});
                function setStatus(value){statusFilter=value;document.querySelectorAll('[data-filter]').forEach(button=>{const active=button.dataset.filter===value;button.classList.toggle('active',active);button.setAttribute('aria-pressed',String(active))});apply()}
                function apply(){const query=search.value.trim().toLowerCase(),module=moduleFilter.value;let count=0;rows.forEach(row=>{const show=(statusFilter==='ALL'||row.dataset.status===statusFilter)&&(module==='ALL'||row.dataset.module===module)&&(!query||row.dataset.search.includes(query));row.hidden=!show;if(show)count++});visible.textContent=count;empty.style.display=count?'none':'block'}
                function openRow(row){window.location.href=row.dataset.href}
                rows.forEach(row=>row.addEventListener('click',event=>{if(!event.target.closest('a'))openRow(row)}));
                document.querySelectorAll('[data-filter]').forEach(button=>button.addEventListener('click',()=>setStatus(button.dataset.filter)));search.addEventListener('input',apply);moduleFilter.addEventListener('change',apply);apply();
                })();
                """;
    }

    private String sharedCss() {
        return """
                :root{--canvas:#f2f4f7;--paper:#fff;--ink:#172033;--muted:#667085;--line:#dfe3e8;--line-dark:#c8ced8;--navy:#18243b;--blue:#2859c5;--blue-soft:#edf3ff;--risk:#b42318;--risk-soft:#fff0ee;--review:#a15c00;--review-soft:#fff6e8;--ignored:#526277;--ignored-soft:#eef1f5;--safe:#087a55;--safe-soft:#eaf8f2;--shadow:0 8px 30px rgba(23,32,51,.06)}
                *{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:var(--canvas);color:var(--ink);font:14px/1.55 Inter,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}button,input,select{font:inherit}a{color:inherit}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
                .page-shell{max-width:1540px;margin:0 auto;padding:34px 28px 56px}.report-header{display:flex;justify-content:space-between;align-items:flex-end;gap:32px;margin-bottom:22px}.eyebrow,.detail-kicker{font-size:11px;font-weight:800;letter-spacing:.14em;color:var(--blue)}h1{margin:4px 0 5px;font-size:30px;line-height:1.2;letter-spacing:-.025em}.subtitle{margin:0;color:#475467;word-break:break-all}.generated{margin:3px 0 0;color:var(--muted);font-size:12px}.header-note{min-width:245px;border-left:3px solid var(--blue);padding:5px 0 5px 14px}.header-note b,.header-note span{display:block}.header-note span{margin-top:2px;color:var(--muted);font-size:12px}
                .metrics{display:grid;grid-template-columns:repeat(5,minmax(130px,1fr));gap:10px;margin-bottom:12px}.metric{position:relative;display:flex;align-items:flex-end;justify-content:space-between;min-height:76px;padding:14px 16px;border:1px solid var(--line);border-radius:10px;background:var(--paper);color:var(--muted);cursor:pointer;text-align:left;box-shadow:0 1px 2px rgba(23,32,51,.02)}.metric:before{content:"";position:absolute;inset:0 auto 0 0;width:3px;border-radius:10px 0 0 10px;background:var(--line-dark)}.metric.risk:before{background:var(--risk)}.metric.review:before{background:var(--review)}.metric.ignored:before{background:var(--ignored)}.metric.safe:before{background:var(--safe)}.metric b{font-size:25px;line-height:1;color:var(--ink)}.metric:hover,.metric.active{border-color:#9db4e9;box-shadow:0 0 0 2px rgba(40,89,197,.1)}
                .controls{position:sticky;top:0;z-index:10;display:grid;grid-template-columns:minmax(320px,1fr) 230px auto;align-items:end;gap:12px;margin-bottom:12px;padding:12px;border:1px solid var(--line);border-radius:10px;background:rgba(242,244,247,.94);backdrop-filter:blur(9px)}.controls label>span{display:block;margin:0 0 5px;color:var(--muted);font-size:11px;font-weight:700}.controls input,.controls select{width:100%;height:40px;border:1px solid var(--line-dark);border-radius:7px;background:#fff;padding:0 11px;color:var(--ink);outline:none}.controls input:focus,.controls select:focus{border-color:var(--blue);box-shadow:0 0 0 3px rgba(40,89,197,.12)}.visible-count{padding:0 4px 9px;white-space:nowrap;color:var(--muted)}.visible-count b{color:var(--ink)}
                .list-panel{overflow:auto;border:1px solid var(--line);border-radius:11px;background:var(--paper);box-shadow:var(--shadow)}table{width:100%;border-collapse:collapse}.findings-table{min-width:1080px}.findings-table th,.findings-table td{text-align:left;vertical-align:top;border-bottom:1px solid var(--line);padding:13px 14px}.findings-table thead th{position:sticky;top:0;z-index:2;background:#f8f9fb;color:#596579;font-size:11px;letter-spacing:.035em;white-space:nowrap}.findings-table tbody tr{cursor:pointer;transition:background .12s ease}.findings-table tbody tr:hover{background:#f6f9ff}.findings-table tbody tr:focus{outline:2px solid var(--blue);outline-offset:-2px}.findings-table tbody tr:last-child td{border-bottom:0}.findings-table td:nth-child(1){width:90px}.findings-table td:nth-child(2){width:165px}.findings-table td:nth-child(3),.findings-table td:nth-child(4){width:22%}.findings-table td:nth-child(6){width:105px;vertical-align:middle}.file,.type-name{display:block;font-weight:700}.location-cell .file,.location-cell .location-module{white-space:normal;overflow-wrap:anywhere}.module-chip{display:inline-block;margin-top:5px;border:1px solid var(--line);border-radius:4px;padding:1px 6px;color:#526075;font-size:10px;background:#f8fafc}small{display:block;margin-top:3px;color:var(--muted);font-size:11px;overflow-wrap:anywhere}.summary{font-weight:700}.problem-text{color:var(--risk)}.safe-text{color:var(--safe)}.muted{color:var(--muted)}.open-cell{text-align:right!important}.detail-link{color:var(--blue);font-size:12px;font-weight:700;text-decoration:none;white-space:nowrap}.detail-link:hover{text-decoration:underline}.empty{display:none;padding:48px;text-align:center;color:var(--muted)}
                .status{display:inline-flex;align-items:center;justify-content:center;min-width:66px;border-radius:999px;padding:3px 8px;color:#fff;font-size:10px;font-weight:850;letter-spacing:.04em}.status.risk{background:var(--risk)}.status.review{background:var(--review)}.status.ignored{background:var(--ignored)}.status.safe{background:var(--safe)}.mapping{display:inline-block;border:1px solid var(--line);border-radius:5px;padding:2px 7px;background:#f8fafc;color:#536075;font-size:10px;white-space:nowrap}.mapping.mapped{border-color:#afdac8;background:var(--safe-soft);color:var(--safe)}.mapping.same_name_not_copyable{border-color:#efc98d;background:var(--review-soft);color:var(--review)}.mapping.source_only{border-color:#b9caef;background:var(--blue-soft);color:var(--blue)}.mapping.target_only{border-color:#c7cdd7;background:var(--ignored-soft);color:var(--ignored)}
                .detail-shell{max-width:1440px;margin:0 auto;padding:0 28px 64px}.detail-nav{position:sticky;top:0;z-index:20;display:flex;align-items:center;justify-content:space-between;gap:15px;min-height:58px;margin-bottom:26px;border-bottom:1px solid var(--line);background:rgba(242,244,247,.94);backdrop-filter:blur(10px)}.pager{display:flex;gap:7px}.nav-link{display:inline-flex;align-items:center;justify-content:center;min-height:34px;border:1px solid var(--line-dark);border-radius:7px;background:#fff;padding:6px 11px;color:#344054;font-size:12px;font-weight:650;text-decoration:none}.nav-link:hover{border-color:var(--blue);color:var(--blue)}.nav-link.back-link{border:0;background:transparent;padding-left:0;color:var(--blue)}.nav-link.disabled{opacity:.42;cursor:not-allowed}.detail-header{display:grid;grid-template-columns:1fr 260px;gap:24px;align-items:stretch;margin-bottom:18px}.detail-heading{min-width:0;padding:9px 0}.detail-title-line{display:flex;align-items:center;gap:12px;margin-top:6px}.detail-title-line h1{margin:0;font-size:25px;overflow-wrap:anywhere}.detail-heading p{margin:8px 0 0;color:var(--muted)}.detail-heading p span{padding:0 5px}.verdict-card{display:flex;flex-direction:column;justify-content:center;border:1px solid var(--line);border-left:4px solid var(--ignored);border-radius:9px;background:#fff;padding:16px 18px;box-shadow:var(--shadow)}.verdict-card.risk{border-left-color:var(--risk)}.verdict-card.review{border-left-color:var(--review)}.verdict-card.safe{border-left-color:var(--safe)}.verdict-card span{color:var(--muted);font-size:11px}.verdict-card b{margin:2px 0;font-size:22px}.verdict-card.risk b{color:var(--risk)}.verdict-card.review b{color:var(--review)}.verdict-card.safe b{color:var(--safe)}
                .detail-section{margin:14px 0;border:1px solid var(--line);border-radius:11px;background:#fff;padding:20px;box-shadow:0 2px 12px rgba(23,32,51,.035);scroll-margin-top:72px}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;margin-bottom:14px}.section-heading>div{display:flex;align-items:center;gap:9px}.section-heading h2{margin:0;font-size:16px}.section-heading p{margin:1px 0 0;color:var(--muted);font-size:11px}.section-index{display:inline-flex;align-items:center;justify-content:center;width:25px;height:25px;border-radius:6px;background:var(--navy);color:#fff;font-size:10px;font-weight:800}.code-block{margin:0;border:1px solid var(--line);border-radius:8px;background:#f7f8fa;padding:14px;white-space:pre-wrap;word-break:break-word;font:12px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace}.bean-types{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:12px}.bean-type-card{min-width:0;border:1px solid var(--line);border-radius:8px;padding:13px}.bean-type-card>span{display:block;color:var(--blue);font-size:10px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.bean-type-card b{display:block;margin:3px 0;font-size:15px}.bean-type-card code,.property-table code{font:11px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace;overflow-wrap:anywhere}.table-scroll{overflow:auto;border:1px solid var(--line);border-radius:8px}.property-table{min-width:900px;font-size:12px}.property-table th,.property-table td{text-align:left;vertical-align:top;border-bottom:1px solid var(--line);padding:10px}.property-table thead th{background:#f7f8fa;color:#596579;font-size:10px;letter-spacing:.025em;white-space:nowrap}.property-table tbody tr:last-child td{border-bottom:0}.property-table tbody tr:hover{background:#fafcff}.side-table th:nth-child(1){width:14%}.side-table th:nth-child(2){width:30%}.side-table th:nth-child(3){width:16%}.side-table th:nth-child(4){width:90px}.reason-cell{color:#526075}.mapping-table{min-width:1280px}.mapping-table th:nth-child(1){width:85px}.mapping-table th:nth-child(2){width:140px}.mapping-table th:nth-child(3){width:130px}.mapping-table th:nth-child(4),.mapping-table th:nth-child(5){width:18%}.mapping-table th:nth-child(6){width:135px}.old-result{font-size:11px}.old-copy{color:var(--safe);font-weight:700}.decision{display:block;color:#344054;font-size:11px}.reason{display:block;margin-top:3px;color:#596579}.property-empty{display:flex;flex-direction:column;align-items:flex-start;gap:3px;border:1px dashed var(--line-dark);border-radius:8px;background:#fafbfc;padding:18px;color:var(--muted)}.property-empty b{color:#344054}.call-chain{list-style:none;margin:0;padding:0}.call-chain li{display:flex;gap:10px;align-items:flex-start;padding:7px 0}.call-chain li>span{display:block;width:8px;height:8px;margin-top:7px;border:2px solid var(--blue);border-radius:50%;background:#fff}.call-chain b,.call-chain small{display:block}.detail-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:20px}
                @media(max-width:980px){.metrics{grid-template-columns:repeat(2,1fr)}.controls{grid-template-columns:1fr 200px}.visible-count{padding-bottom:0}.detail-header{grid-template-columns:1fr}.bean-types{grid-template-columns:1fr}}
                @media(max-width:680px){.page-shell,.detail-shell{padding-left:12px;padding-right:12px}.report-header{display:block}.header-note{margin-top:18px}.metrics{grid-template-columns:1fr 1fr}.controls{position:static;grid-template-columns:1fr}.detail-nav{margin-bottom:14px}.detail-nav .pager{display:none}.detail-title-line{align-items:flex-start;flex-direction:column}.detail-title-line h1{font-size:20px}.detail-section{padding:14px}.section-heading{display:block}.section-heading p{margin:7px 0 0 34px}.detail-footer{align-items:flex-start;flex-direction:column}.detail-footer .pager{width:100%;justify-content:space-between}}
                """;
    }
}
