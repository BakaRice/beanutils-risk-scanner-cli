package com.example.beanutils.scanner.report;

import com.example.beanutils.scanner.model.CopyFinding;
import com.example.beanutils.scanner.model.PropertyFinding;
import com.example.beanutils.scanner.model.ScanReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HtmlReportWriter {
    private final JsonReportWriter jsonWriter = new JsonReportWriter();

    public void write(ScanReport report, Path output) throws IOException {
        JsonReportWriter.createParent(output);
        String json = safeJson(jsonWriter.toJson(report));
        String html = template()
                .replace("{{PROJECT}}", html(report.projectPath()))
                .replace("{{GENERATED_AT}}", html(report.generatedAt()))
                .replace("{{TOTAL}}", Integer.toString(report.findings().size()))
                .replace("{{RISK}}", Long.toString(report.count(com.example.beanutils.scanner.model.FindingStatus.RISK)))
                .replace("{{REVIEW}}", Long.toString(report.count(com.example.beanutils.scanner.model.FindingStatus.REVIEW)))
                .replace("{{IGNORED}}", Long.toString(report.count(com.example.beanutils.scanner.model.FindingStatus.IGNORED)))
                .replace("{{SAFE}}", Long.toString(report.count(com.example.beanutils.scanner.model.FindingStatus.SAFE)))
                .replace("{{ROWS}}", rows(report))
                .replace("{{REPORT_JSON}}", json);
        Files.writeString(output, html, StandardCharsets.UTF_8);
    }

    private String rows(ScanReport report) {
        StringBuilder rows = new StringBuilder();
        for (CopyFinding finding : report.findings()) {
            String search = finding.status() + " " + finding.location().display() + " "
                    + finding.sourceType().qualifiedName() + " " + finding.targetType().qualifiedName()
                    + " " + finding.code();
            rows.append("<tr data-status=\"").append(finding.status()).append("\" data-search=\"")
                    .append(attribute(search.toLowerCase())).append("\">")
                    .append("<td><span class=\"status ").append(finding.status().name().toLowerCase())
                    .append("\">").append(finding.status()).append("</span></td>")
                    .append("<td><a class=\"location\" href=\"#\">")
                    .append(html(finding.location().display())).append("</a><small>")
                    .append(html(finding.module())).append("</small></td>")
                    .append(typeCell(finding.sourceType()))
                    .append(typeCell(finding.targetType()))
                    .append("<td><details><summary>").append(html(finding.callForm())).append(" · ")
                    .append(finding.properties().size()).append(" 个属性</summary><div class=\"detail\">")
                    .append("<pre>").append(html(finding.code())).append("</pre>")
                    .append(propertyTable(finding)).append(chain(finding)).append("</div></details></td></tr>");
        }
        return rows.toString();
    }

    private String typeCell(com.example.beanutils.scanner.model.TypeRef type) {
        StringBuilder cell = new StringBuilder("<td><code>")
                .append(html(type.qualifiedName())).append("</code>");
        if (!type.module().isBlank() || !type.sourcePath().isBlank()) {
            cell.append("<small>").append(html(type.module()));
            if (!type.module().isBlank() && !type.sourcePath().isBlank()) cell.append(" · ");
            cell.append(html(type.sourcePath())).append("</small>");
        }
        return cell.append("</td>").toString();
    }

    private String propertyTable(CopyFinding finding) {
        if (finding.properties().isEmpty()) {
            String message = finding.status() == com.example.beanutils.scanner.model.FindingStatus.REVIEW
                    ? "类型或属性无法完全解析，请人工检查该调用。"
                    : "未发现同名且可复制的 JavaBean 属性。";
            return "<p class=\"muted\">" + message + "</p>";
        }
        StringBuilder result = new StringBuilder("<table class=\"properties\"><thead><tr><th>属性结论</th><th>属性</th><th>Source 属性类型</th><th>Target 属性类型</th><th>说明</th></tr></thead><tbody>");
        for (PropertyFinding property : finding.properties()) {
            result.append("<tr><td>").append(property.status()).append("</td><td>")
                    .append(html(property.propertyName())).append("</td><td><code>")
                    .append(html(property.sourceType().qualifiedName())).append("</code></td><td><code>")
                    .append(html(property.targetType().qualifiedName())).append("</code></td><td>")
                    .append(html(property.reason())).append("</td></tr>");
        }
        return result.append("</tbody></table>").toString();
    }

    private String chain(CopyFinding finding) {
        StringBuilder result = new StringBuilder("<div class=\"chain\"><strong>调用链</strong><ol>");
        finding.callChain().forEach(step -> result.append("<li>").append(html(step.method())).append(" — ")
                .append(html(step.location().display())).append("</li>"));
        return result.append("</ol></div>").toString();
    }

    private String safeJson(String json) {
        return json.replace("&", "\\u0026").replace("<", "\\u003c").replace(">", "\\u003e")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    private String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String attribute(String value) {
        return html(value).replace("\n", " ").replace("\r", " ");
    }

    private String template() {
        return """
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>BeanUtils 升级风险审计报告</title><style>
                :root{--bg:#f4f7fb;--panel:#fff;--ink:#172033;--muted:#657086;--line:#dfe5ef;--risk:#c62828;--review:#a35a00;--ignored:#53657d;--safe:#16734a}
                *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.wrap{max-width:1500px;margin:auto;padding:34px 28px 60px}
                h1{font-size:28px;margin:0 0 5px}.subtitle{color:var(--muted);word-break:break-all}.cards{display:grid;grid-template-columns:repeat(5,minmax(120px,1fr));gap:12px;margin:24px 0}.card{background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:16px;box-shadow:0 3px 16px #24324a0a}.card b{display:block;font-size:25px}.card span{color:var(--muted)}
                .toolbar{display:flex;gap:10px;align-items:center;margin:18px 0}.toolbar input{min-width:320px;flex:1;border:1px solid var(--line);border-radius:9px;padding:11px 13px;background:#fff}.filters{display:flex;gap:6px}.filters button{border:1px solid var(--line);border-radius:999px;padding:8px 12px;background:#fff;cursor:pointer}.filters button.active{background:#172033;color:#fff}
                .panel{overflow:auto;background:#fff;border:1px solid var(--line);border-radius:12px;box-shadow:0 4px 22px #24324a0b}table{width:100%;border-collapse:collapse}th,td{padding:13px 14px;text-align:left;vertical-align:top;border-bottom:1px solid var(--line)}thead th{position:sticky;top:0;background:#f9fbfd;z-index:1;color:#4f5a6f;white-space:nowrap}tbody tr:hover{background:#fbfcff}code{font:12px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace;word-break:break-all}.status{display:inline-block;border-radius:999px;padding:3px 9px;font-weight:700;color:#fff}.risk{background:var(--risk)}.review{background:var(--review)}.ignored{background:var(--ignored)}.safe{background:var(--safe)}.location{color:#245cc7;text-decoration:none;white-space:nowrap}small{display:block;color:var(--muted)}details summary{cursor:pointer;white-space:nowrap}.detail{padding:12px 0;min-width:850px}.detail pre{white-space:pre-wrap;background:#f5f7fa;padding:10px;border-radius:7px}.properties{font-size:12px}.properties th,.properties td{padding:8px}.muted{color:var(--muted)}.chain{margin-top:12px}.empty{text-align:center;padding:32px;color:var(--muted);display:none}
                @media(max-width:800px){.wrap{padding:20px 12px}.cards{grid-template-columns:repeat(2,1fr)}.toolbar{align-items:stretch;flex-direction:column}.toolbar input{min-width:0}.filters{overflow:auto}}
                </style></head><body><main class="wrap"><h1>BeanUtils 升级风险审计报告</h1><div class="subtitle">{{PROJECT}} · 生成于 {{GENERATED_AT}}</div>
                <section class="cards"><div class="card"><b>{{TOTAL}}</b><span>全部调用</span></div><div class="card"><b>{{RISK}}</b><span>RISK</span></div><div class="card"><b>{{REVIEW}}</b><span>REVIEW</span></div><div class="card"><b>{{IGNORED}}</b><span>IGNORED</span></div><div class="card"><b>{{SAFE}}</b><span>SAFE</span></div></section>
                <section class="toolbar"><input id="search" type="search" placeholder="搜索位置、类型或代码"><div class="filters"><button class="active" data-filter="ALL">全部</button><button data-filter="RISK">RISK</button><button data-filter="REVIEW">REVIEW</button><button data-filter="IGNORED">IGNORED</button><button data-filter="SAFE">SAFE</button></div></section>
                <div class="panel"><table><thead><tr><th>结论</th><th>代码位置</th><th>Source 类型</th><th>Target 类型</th><th>详情</th></tr></thead><tbody id="rows">{{ROWS}}</tbody></table><div id="empty" class="empty">没有符合筛选条件的调用</div></div>
                </main><script id="report-data" type="application/json">{{REPORT_JSON}}</script><script>
                (()=>{let filter='ALL';const input=document.querySelector('#search'),rows=[...document.querySelectorAll('#rows>tr')],empty=document.querySelector('#empty');function apply(){const q=input.value.trim().toLowerCase();let n=0;rows.forEach(r=>{const show=(filter==='ALL'||r.dataset.status===filter)&&(!q||r.dataset.search.includes(q));r.hidden=!show;if(show)n++});empty.style.display=n?'none':'block'}input.addEventListener('input',apply);document.querySelectorAll('[data-filter]').forEach(b=>b.addEventListener('click',()=>{filter=b.dataset.filter;document.querySelectorAll('[data-filter]').forEach(x=>x.classList.toggle('active',x===b));apply()}));})();
                </script></body></html>
                """;
    }
}
