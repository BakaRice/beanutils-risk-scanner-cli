package com.example.beanutils.scanner;

import com.example.beanutils.scanner.analysis.DirectCopyAnalyzer;
import com.example.beanutils.scanner.analysis.BeanPropertyTraceLogger;
import com.example.beanutils.scanner.callgraph.WrapperCallAnalyzer;
import com.example.beanutils.scanner.discovery.BeanUtilsCallDetector;
import com.example.beanutils.scanner.model.Diagnostic;
import com.example.beanutils.scanner.model.ScanReport;
import com.example.beanutils.scanner.project.MavenProjectLoader;
import com.example.beanutils.scanner.source.SourceIndexer;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.function.Consumer;

public final class BeanUtilsRiskScanner {
    public ScanReport scan(ScanRequest request) throws Exception {
        return scan(request, unused -> { });
    }

    public ScanReport scan(ScanRequest request, Consumer<String> traceOutput) throws Exception {
        var project = new MavenProjectLoader().load(request.project(), request.includeTests(), request.localRepository());
        try (var workspace = new SourceIndexer().index(project, request.useCompiledClasses(), request.includeTests())) {
            var calls = new BeanUtilsCallDetector().discover(workspace);
            var trace = new BeanPropertyTraceLogger(traceOutput);
            var analyzer = new DirectCopyAnalyzer(trace);
            var findings = new ArrayList<>(calls.stream().map(analyzer::analyze).toList());
            findings.addAll(new WrapperCallAnalyzer(trace).analyze(workspace, calls));
            var diagnostics = new ArrayList<Diagnostic>();
            diagnostics.addAll(project.diagnostics());
            diagnostics.addAll(workspace.diagnostics());
            return new ScanReport(project.rootDirectory().toString(), OffsetDateTime.now().toString(), findings, diagnostics);
        }
    }
}
