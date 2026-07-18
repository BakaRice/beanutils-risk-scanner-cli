package com.example.beanutils.scanner.report;

import com.example.beanutils.scanner.model.CopyFinding;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.PropertyFinding;
import com.example.beanutils.scanner.model.ScanReport;
import com.example.beanutils.scanner.model.TypeRef;

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
                .replace("{{RISK}}", Long.toString(report.count(FindingStatus.RISK)))
                .replace("{{REVIEW}}", Long.toString(report.count(FindingStatus.REVIEW)))
                .replace("{{IGNORED}}", Long.toString(report.count(FindingStatus.IGNORED)))
                .replace("{{SAFE}}", Long.toString(report.count(FindingStatus.SAFE)))
                .replace("{{ROWS}}", rows(report))
                .replace("{{REPORT_JSON}}", json);
        Files.writeString(output, html, StandardCharsets.UTF_8);
    }

    private String rows(ScanReport report) {
        StringBuilder rows = new StringBuilder();
        for (int index = 0; index < report.findings().size(); index++) {
            CopyFinding finding = report.findings().get(index);
            String search = searchText(finding);
            rows.append("<tr tabindex=\"0\" role=\"button\" aria-controls=\"detail-panel\" data-index=\"")
                    .append(index).append("\" data-status=\"").append(finding.status())
                    .append("\" data-module=\"").append(attribute(finding.module()))
                    .append("\" data-search=\"").append(attribute(search.toLowerCase())).append("\">")
                    .append("<td><span class=\"status ").append(finding.status().name().toLowerCase())
                    .append("\">").append(finding.status()).append("</span></td>")
                    .append("<td><strong class=\"file\">").append(html(fileName(finding.location().relativePath())))
                    .append(":").append(finding.location().line()).append("</strong><small>")
                    .append(html(finding.module())).append(" · ").append(html(finding.location().relativePath()))
                    .append("</small></td>")
                    .append(typeCell(finding.sourceType()))
                    .append(typeCell(finding.targetType()))
                    .append("<td>").append(propertySummary(finding)).append("</td></tr>");
        }
        return rows.toString();
    }

    private String searchText(CopyFinding finding) {
        StringBuilder search = new StringBuilder().append(finding.status()).append(' ')
                .append(finding.location().relativePath()).append(' ').append(finding.module()).append(' ')
                .append(finding.sourceType().qualifiedName()).append(' ').append(finding.sourceType().module()).append(' ')
                .append(finding.targetType().qualifiedName()).append(' ').append(finding.targetType().module()).append(' ')
                .append(finding.code());
        for (PropertyFinding property : finding.properties()) {
            search.append(' ').append(property.propertyName()).append(' ').append(property.status()).append(' ')
                    .append(property.sourceType().qualifiedName()).append(' ')
                    .append(property.targetType().qualifiedName()).append(' ').append(property.reason());
        }
        return search.toString();
    }

    private String propertySummary(CopyFinding finding) {
        long problems = finding.properties().stream().filter(property -> property.status() != FindingStatus.SAFE).count();
        if (finding.properties().isEmpty()) {
            return "<span class=\"summary muted\">无可展示属性</span>";
        }
        if (problems == 0) {
            return "<span class=\"summary safe-text\">" + finding.properties().size() + " 个属性均兼容</span>";
        }
        return "<span class=\"summary problem-text\">" + problems + " 个需关注</span><small>共 "
                + finding.properties().size() + " 个属性</small>";
    }

    private String typeCell(TypeRef type) {
        StringBuilder cell = new StringBuilder("<td><strong class=\"type-name\">")
                .append(html(type.displayName())).append("</strong>");
        if (!type.module().isBlank()) {
            cell.append("<span class=\"module-chip\">").append(html(type.module())).append("</span>");
        }
        cell.append("<small title=\"").append(attribute(type.qualifiedName())).append("\">")
                .append(html(type.qualifiedName())).append("</small>");
        return cell.append("</td>").toString();
    }

    private String fileName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
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
        return html(value == null ? "" : value).replace("\n", " ").replace("\r", " ");
    }

    private String template() {
        return """
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>BeanUtils 升级风险审计报告</title><style>
                :root{--bg:#f4f6f9;--panel:#fff;--ink:#172033;--muted:#687386;--line:#dfe4ec;--line-strong:#c9d1dd;--accent:#245cc7;--risk:#b42318;--risk-bg:#fff0ee;--review:#9a4e00;--review-bg:#fff5e8;--ignored:#506176;--ignored-bg:#eef2f6;--safe:#14734b;--safe-bg:#eaf8f1}
                *{box-sizing:border-box}html,body{height:100%}body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.page{max-width:1800px;margin:auto;padding:26px 24px 42px}button,input,select{font:inherit}
                .topbar{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.topbar h1{font-size:25px;line-height:1.2;margin:0 0 6px}.subtitle{max-width:900px;color:var(--muted);word-break:break-all}.visible-count{white-space:nowrap;color:var(--muted);padding-top:6px}.visible-count b{color:var(--ink)}
                .metrics{display:grid;grid-template-columns:repeat(5,minmax(120px,1fr));gap:10px;margin:20px 0 14px}.metric{appearance:none;text-align:left;background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:13px 15px;cursor:pointer;color:var(--ink)}.metric:hover,.metric.active{border-color:var(--accent);box-shadow:0 0 0 2px #245cc71f}.metric b{display:block;font-size:22px}.metric span{color:var(--muted)}
                .controls{position:sticky;top:0;z-index:8;display:grid;grid-template-columns:minmax(260px,1fr) auto auto;gap:10px;padding:10px 0;background:var(--bg)}.search-wrap{position:relative}.search-wrap input{width:100%;border:1px solid var(--line-strong);border-radius:8px;background:#fff;padding:10px 12px}.controls select{border:1px solid var(--line-strong);border-radius:8px;background:#fff;padding:9px 34px 9px 11px}.filters{display:flex;gap:5px;align-items:center}.filters button{border:1px solid var(--line);border-radius:7px;padding:9px 11px;background:#fff;color:#465166;cursor:pointer}.filters button.active{background:#25324a;border-color:#25324a;color:#fff}
                .workspace{display:grid;grid-template-columns:minmax(620px,1fr) minmax(390px,520px);gap:12px;align-items:start}.list-panel,.detail-panel{background:#fff;border:1px solid var(--line);border-radius:10px;box-shadow:0 3px 14px #23314b0a}.list-panel{overflow:auto;max-height:calc(100vh - 190px)}table{width:100%;border-collapse:collapse}.findings-table{min-width:980px}th,td{text-align:left;vertical-align:top;border-bottom:1px solid var(--line);padding:11px 12px}.findings-table thead th{position:sticky;top:0;z-index:2;background:#f8fafc;color:#566176;font-size:12px;letter-spacing:.02em;white-space:nowrap}.findings-table tbody tr{cursor:pointer}.findings-table tbody tr:hover{background:#f8faff}.findings-table tbody tr.selected{background:#eef4ff;box-shadow:inset 3px 0 var(--accent)}.findings-table tbody tr:focus{outline:2px solid var(--accent);outline-offset:-2px}.findings-table td:nth-child(1){width:82px}.findings-table td:nth-child(2){min-width:220px}.findings-table td:nth-child(3),.findings-table td:nth-child(4){min-width:190px;max-width:260px}.findings-table td:nth-child(5){min-width:130px}
                .status{display:inline-block;border-radius:999px;padding:3px 8px;font-size:11px;font-weight:750;color:#fff}.status.risk{background:var(--risk)}.status.review{background:var(--review)}.status.ignored{background:var(--ignored)}.status.safe{background:var(--safe)}.file,.type-name{display:block;font-weight:650}.module-chip{display:inline-block;margin-top:5px;border:1px solid var(--line);border-radius:4px;padding:1px 5px;color:#536075;font-size:11px;background:#f8fafc}small{display:block;margin-top:3px;color:var(--muted);font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.summary{font-weight:650}.problem-text{color:var(--risk)}.safe-text{color:var(--safe)}.muted{color:var(--muted)}.empty{text-align:center;padding:34px;color:var(--muted);display:none}
                .detail-panel{position:sticky;top:58px;max-height:calc(100vh - 78px);overflow:auto}.detail-empty{display:grid;place-items:center;min-height:390px;padding:30px;text-align:center;color:var(--muted)}.detail{padding:18px}.detail-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;border-bottom:1px solid var(--line);padding-bottom:14px}.detail-title{margin:7px 0 0;font-size:17px}.icon-button{border:1px solid var(--line);border-radius:7px;background:#fff;padding:6px 9px;cursor:pointer;color:#4a566a}.section{margin-top:18px}.section-title{display:flex;align-items:center;justify-content:space-between;gap:10px;margin:0 0 8px;font-size:13px}.code{margin:0;padding:11px;border:1px solid var(--line);border-radius:7px;background:#f6f8fb;white-space:pre-wrap;word-break:break-word;font:12px/1.55 ui-monospace,SFMono-Regular,Menlo,monospace}.type-compare{display:grid;grid-template-columns:1fr 1fr;gap:8px}.type-card{border:1px solid var(--line);border-radius:8px;padding:11px;min-width:0}.type-card .label{font-size:11px;color:var(--muted);text-transform:uppercase}.type-card code{display:block;margin:5px 0;word-break:break-all;font:12px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace}.origin{font-size:11px;color:var(--muted);word-break:break-all}.property-toggle{border:1px solid var(--line);border-radius:6px;background:#fff;padding:5px 8px;color:#465166;cursor:pointer}.property-table{font-size:12px;table-layout:fixed}.property-table th{position:sticky;top:0;background:#f8fafc;color:#596477}.property-table th,.property-table td{padding:8px}.property-table th:nth-child(1){width:70px}.property-table th:nth-child(2){width:110px}.property-table th:nth-child(3),.property-table th:nth-child(4){width:22%}.property-table code{word-break:break-all;font-size:11px}.decision{font-weight:650}.reason{color:#536075}.chain{margin:0;padding-left:20px}.chain li{margin:5px 0}.diagnostic{border-radius:7px;padding:9px;background:var(--review-bg);color:#764200}
                @media(max-width:1180px){.workspace{grid-template-columns:1fr}.list-panel{max-height:none}.detail-panel{position:fixed;z-index:20;inset:20px 18px 20px auto;width:min(620px,calc(100vw - 36px));max-height:none;box-shadow:0 18px 70px #17203340;display:none}.detail-panel.open{display:block}.findings-table{min-width:900px}}
                @media(max-width:760px){.page{padding:18px 10px 30px}.topbar{display:block}.visible-count{margin-top:6px}.metrics{grid-template-columns:repeat(2,1fr)}.controls{grid-template-columns:1fr}.filters{overflow:auto}.workspace{display:block}.detail-panel{inset:8px;width:calc(100vw - 16px)}.type-compare{grid-template-columns:1fr}.property-table{min-width:760px}}
                </style></head><body><main class="page"><header class="topbar"><div><h1>BeanUtils 升级风险审计报告</h1><div class="subtitle">{{PROJECT}} · 生成于 {{GENERATED_AT}}</div></div><div class="visible-count" aria-live="polite">显示 <b id="visible-count">{{TOTAL}}</b> / {{TOTAL}}</div></header>
                <section class="metrics" aria-label="按结论快速筛选"><button class="metric active" data-filter="ALL"><b>{{TOTAL}}</b><span>全部调用</span></button><button class="metric" data-filter="RISK"><b>{{RISK}}</b><span>RISK</span></button><button class="metric" data-filter="REVIEW"><b>{{REVIEW}}</b><span>REVIEW</span></button><button class="metric" data-filter="IGNORED"><b>{{IGNORED}}</b><span>IGNORED</span></button><button class="metric" data-filter="SAFE"><b>{{SAFE}}</b><span>SAFE</span></button></section>
                <section class="controls" aria-label="报告筛选"><div class="search-wrap"><input id="search" type="search" aria-label="搜索报告" placeholder="搜索文件、module、类型、属性或原因"></div><select id="module-filter" aria-label="按调用 module 筛选"><option value="ALL">全部 module</option></select><div class="filters"><button class="active" data-filter="ALL">全部</button><button data-filter="RISK">RISK</button><button data-filter="REVIEW">REVIEW</button><button data-filter="IGNORED">IGNORED</button><button data-filter="SAFE">SAFE</button></div></section>
                <section class="workspace"><div class="list-panel" id="findings-list"><table class="findings-table"><thead><tr><th>结论</th><th>代码位置</th><th>Source 类型</th><th>Target 类型</th><th>属性概览</th></tr></thead><tbody id="rows">{{ROWS}}</tbody></table><div id="empty" class="empty">没有符合筛选条件的调用</div></div>
                <aside class="detail-panel" id="detail-panel" aria-label="调用详情"><div class="detail-empty">选择左侧任意调用，查看类型来源、属性差异和调用链。</div></aside></section>
                </main><script id="report-data" type="application/json">{{REPORT_JSON}}</script><script>
                (()=>{const report=JSON.parse(document.querySelector('#report-data').textContent),rows=[...document.querySelectorAll('#rows>tr')],search=document.querySelector('#search'),moduleFilter=document.querySelector('#module-filter'),empty=document.querySelector('#empty'),detail=document.querySelector('#detail-panel'),visible=document.querySelector('#visible-count');let statusFilter='ALL',selected=-1,propertyMode='PROBLEMS';
                const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));const loc=l=>`${l?.relativePath??''}:${l?.line??0}`;const status=s=>`<span class="status ${String(s).toLowerCase()}">${esc(s)}</span>`;
                [...new Set(report.findings.map(f=>f.module).filter(Boolean))].sort().forEach(m=>{const o=document.createElement('option');o.value=m;o.textContent=m;moduleFilter.append(o)});
                function setStatus(value){statusFilter=value;document.querySelectorAll('[data-filter]').forEach(b=>b.classList.toggle('active',b.dataset.filter===value));apply()}
                function apply(){const q=search.value.trim().toLowerCase(),m=moduleFilter.value;let count=0,first=-1;rows.forEach(r=>{const show=(statusFilter==='ALL'||r.dataset.status===statusFilter)&&(m==='ALL'||r.dataset.module===m)&&(!q||r.dataset.search.includes(q));r.hidden=!show;if(show){count++;if(first<0)first=Number(r.dataset.index)}});visible.textContent=count;empty.style.display=count?'none':'block';if(selected<0||rows[selected]?.hidden){first>=0?select(first):clearDetail()}}
                function typeCard(label,t){const origin=[t.module,t.sourcePath].filter(Boolean).join(' · ');return `<div class="type-card"><div class="label">${label}</div><code>${esc(t.qualifiedName)}</code>${origin?`<div class="origin">${esc(origin)}</div>`:''}</div>`}
                function properties(f){const all=f.properties??[],problems=all.filter(p=>p.status!=='SAFE'),shown=propertyMode==='PROBLEMS'&&problems.length?problems:all;if(!shown.length)return `<div class="muted">${f.status==='REVIEW'?'类型或属性无法完全解析，请人工检查。':'未发现同名且可复制的 JavaBean 属性。'}</div>`;return `<div style="overflow:auto"><table class="property-table"><thead><tr><th>结论</th><th>属性</th><th>Source 属性类型</th><th>Target 属性类型</th><th>判定与原因</th></tr></thead><tbody>${shown.map(p=>`<tr><td>${status(p.status)}</td><td><b>${esc(p.propertyName)}</b></td><td><code>${esc(p.sourceType.qualifiedName)}</code></td><td><code>${esc(p.targetType.qualifiedName)}</code></td><td><div class="decision">${esc(p.newDecision)}</div><div class="reason">${esc(p.reason)}</div></td></tr>`).join('')}</tbody></table></div>`}
                function chain(f){const items=f.callChain??[];return items.length?`<ol class="chain">${items.map(s=>`<li><b>${esc(s.method)}</b><div class="origin">${esc(loc(s.location))}</div></li>`).join('')}</ol>`:'<div class="muted">无调用链信息</div>'}
                function render(){if(selected<0)return;const f=report.findings[selected],issues=(f.properties??[]).filter(p=>p.status!=='SAFE').length;detail.innerHTML=`<div class="detail"><div class="detail-head"><div>${status(f.status)}<h2 class="detail-title">${esc(loc(f.location))}</h2><div class="origin">调用 module：${esc(f.module||'-')} · ${esc(f.callForm)}</div></div><button class="icon-button" id="detail-close" aria-label="关闭详情">关闭</button></div><section class="section"><h3 class="section-title">调用代码</h3><pre class="code">${esc(f.code)}</pre></section><section class="section"><h3 class="section-title">Bean 类型与来源</h3><div class="type-compare">${typeCard('Source',f.sourceType)}${typeCard('Target',f.targetType)}</div></section><section class="section"><h3 class="section-title"><span>属性分析 · ${f.properties.length} 个</span>${f.properties.length&&issues?`<button class="property-toggle" id="property-problem-only">${propertyMode==='PROBLEMS'?'显示全部属性':'仅看问题属性'}</button>`:''}</h3>${properties(f)}</section><section class="section"><h3 class="section-title">调用链</h3>${chain(f)}</section></div>`;detail.classList.add('open')}
                function select(index){selected=index;propertyMode='PROBLEMS';rows.forEach(r=>r.classList.toggle('selected',Number(r.dataset.index)===index));render()}
                function clearDetail(){selected=-1;rows.forEach(r=>r.classList.remove('selected'));detail.classList.remove('open');detail.innerHTML='<div class="detail-empty">选择左侧任意调用，查看类型来源、属性差异和调用链。</div>'}
                rows.forEach(r=>{r.addEventListener('click',()=>select(Number(r.dataset.index)));r.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();select(Number(r.dataset.index))}})});document.querySelectorAll('[data-filter]').forEach(b=>b.addEventListener('click',()=>setStatus(b.dataset.filter)));search.addEventListener('input',apply);moduleFilter.addEventListener('change',apply);detail.addEventListener('click',e=>{if(e.target.id==='detail-close')clearDetail();if(e.target.id==='property-problem-only'){propertyMode=propertyMode==='PROBLEMS'?'ALL':'PROBLEMS';render()}});document.addEventListener('keydown',e=>{if(e.key==='Escape')clearDetail()});apply();
                })();
                </script></body></html>
                """;
    }
}
