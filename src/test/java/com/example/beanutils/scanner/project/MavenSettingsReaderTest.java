package com.example.beanutils.scanner.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenSettingsReaderTest {
    @TempDir Path temp;

    @Test
    void readsLocalRepositoryWithoutAllowingExternalXmlEntities() throws Exception {
        Path settings = temp.resolve("settings.xml");
        Files.writeString(settings, "<settings><localRepository>" + temp.resolve("repository")
                + "</localRepository></settings>");

        assertEquals(temp.resolve("repository").toAbsolutePath().normalize(),
                new MavenSettingsReader().localRepository(settings).orElseThrow());
    }
}
