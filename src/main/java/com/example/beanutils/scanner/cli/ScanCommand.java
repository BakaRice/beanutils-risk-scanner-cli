package com.example.beanutils.scanner.cli;

import com.example.beanutils.scanner.BeanUtilsRiskScanner;
import com.example.beanutils.scanner.ScanRequest;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.report.HtmlReportWriter;
import com.example.beanutils.scanner.report.JsonReportWriter;
import com.example.beanutils.scanner.project.MavenSettingsReader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Desktop;
import java.util.concurrent.Callable;

@Command(name = "beanutils-risk-scanner", mixinStandardHelpOptions = true,
        version = "beanutils-risk-scanner 1.0.0",
        description = "Scan Maven projects for Spring BeanUtils generic copy risks.")
public final class ScanCommand implements Callable<Integer> {
    @Option(names = "--project", required = true, description = "Maven project root")
    private Path project;

    @Option(names = "--output", defaultValue = "beanutils-risk-report.html",
            description = "HTML report index output; also creates a sibling <base>-details directory")
    private Path output;

    @Option(names = "--json-output", description = "Optional JSON report output")
    private Path jsonOutput;

    @Option(names = "--local-repository", description = "Maven local repository")
    private Path localRepository;

    @Option(names = "--settings", description = "Maven settings.xml")
    private Path settings;

    @Option(names = "--include-tests", description = "Include src/test/java")
    private boolean includeTests;

    @Option(names = "--open", description = "Open the generated HTML report")
    private boolean open;

    @Override
    public Integer call() throws Exception {
        Path pom = project.toAbsolutePath().normalize().resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            throw new CommandLine.ParameterException(new CommandLine(this),
                    "Maven project pom.xml not found: " + pom);
        }
        if (settings != null && !Files.isRegularFile(settings.toAbsolutePath().normalize())) {
            throw new CommandLine.ParameterException(new CommandLine(this),
                    "Maven settings.xml not found: " + settings.toAbsolutePath().normalize());
        }
        Path repository;
        if (localRepository != null) {
            repository = localRepository.toAbsolutePath().normalize();
        } else {
            repository = new MavenSettingsReader().localRepository(settings)
                    .orElse(Path.of(System.getProperty("user.home"), ".m2", "repository"));
        }
        Path html = output.toAbsolutePath().normalize();
        var report = new BeanUtilsRiskScanner().scan(new ScanRequest(project.toAbsolutePath().normalize(), repository, includeTests));
        new HtmlReportWriter().write(report, html);
        if (jsonOutput != null) {
            new JsonReportWriter().write(report, jsonOutput.toAbsolutePath().normalize());
        }
        System.out.printf("扫描完成：共 %d 处，RISK %d，REVIEW %d，IGNORED %d，SAFE %d%n报告：%s%n",
                report.findings().size(), report.count(FindingStatus.RISK), report.count(FindingStatus.REVIEW),
                report.count(FindingStatus.IGNORED), report.count(FindingStatus.SAFE), html);
        if (open) {
            try {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(html.toUri());
                else System.err.println("当前环境不支持自动打开浏览器，报告已正常生成。");
            } catch (Exception exception) {
                System.err.println("无法自动打开报告，报告已正常生成：" + exception.getMessage());
            }
        }
        return CommandLine.ExitCode.OK;
    }
}
