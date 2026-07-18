package com.example.beanutils.scanner.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScannerApplicationTest {
    @TempDir Path temp;

    @Test
    void projectIsRequired() {
        int exit = new CommandLine(new ScanCommand()).execute();
        assertEquals(CommandLine.ExitCode.USAGE, exit);
    }

    @Test
    void createsHtmlAndOptionalJsonEvenWhenFindingsNeedReview() {
        Path fixture = Path.of("src/test/resources/fixtures/copy-calls").toAbsolutePath();
        Path html = temp.resolve("audit.html");
        Path json = temp.resolve("audit.json");

        int exit = new CommandLine(new ScanCommand()).execute("--project", fixture.toString(),
                "--output", html.toString(), "--json-output", json.toString());

        assertEquals(CommandLine.ExitCode.OK, exit);
        org.junit.jupiter.api.Assertions.assertTrue(Files.isRegularFile(html));
        org.junit.jupiter.api.Assertions.assertTrue(Files.isRegularFile(json));
    }
}
