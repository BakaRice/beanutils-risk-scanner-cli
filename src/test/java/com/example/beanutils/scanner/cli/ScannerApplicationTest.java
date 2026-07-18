package com.example.beanutils.scanner.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerApplicationTest {
    @TempDir Path temp;

    @Test
    void projectIsRequired() {
        int exit = new CommandLine(new ScanCommand()).execute();
        assertEquals(CommandLine.ExitCode.USAGE, exit);
    }

    @Test
    void createsReportsAndPrintsResolvedBeanPropertiesDuringTheScan() {
        Path fixture = Path.of("src/test/resources/fixtures/copy-calls").toAbsolutePath();
        Path html = temp.resolve("audit.html");
        Path json = temp.resolve("audit.json");
        PrintStream originalOut = System.out;
        var capturedBytes = new ByteArrayOutputStream();

        int exit;
        try (var capturedOut = new PrintStream(capturedBytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            exit = new CommandLine(new ScanCommand()).execute("--project", fixture.toString(),
                    "--output", html.toString(), "--json-output", json.toString());
        } finally {
            System.setOut(originalOut);
        }
        String log = capturedBytes.toString(StandardCharsets.UTF_8);

        assertEquals(CommandLine.ExitCode.OK, exit);
        assertTrue(Files.isRegularFile(html));
        assertTrue(Files.isRegularFile(json));
        assertTrue(log.contains("[BeanUtilsScanner][BEAN] type=example.CopyCalls.Source "));
        assertTrue(log.contains("[BeanUtilsScanner][PROPERTY] bean=example.CopyCalls.Source name=name"));
        assertTrue(log.contains("[BeanUtilsScanner][BEAN-END] type=example.CopyCalls.Source properties=2"));
        assertTrue(log.indexOf("[BeanUtilsScanner][BEAN]") < log.indexOf("扫描完成："));
    }
}
