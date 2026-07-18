package com.example.beanutils.scanner.report;

import com.example.beanutils.scanner.model.ScanReport;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonReportWriter {
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    public void write(ScanReport report, Path output) throws IOException {
        createParent(output);
        mapper.writeValue(output.toFile(), report);
    }

    public String toJson(ScanReport report) throws IOException {
        return mapper.writeValueAsString(report);
    }

    static void createParent(Path output) throws IOException {
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
