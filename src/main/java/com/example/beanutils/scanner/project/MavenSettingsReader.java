package com.example.beanutils.scanner.project;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class MavenSettingsReader {
    public Optional<Path> localRepository(Path settings) throws Exception {
        if (settings == null || !Files.isRegularFile(settings)) {
            return Optional.empty();
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document;
        try (Reader reader = Files.newBufferedReader(settings)) {
            document = factory.newDocumentBuilder().parse(new InputSource(reader));
        }
        NodeList nodes = document.getElementsByTagName("localRepository");
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent().isBlank()) {
            return Optional.empty();
        }
        String home = System.getProperty("user.home");
        String value = nodes.item(0).getTextContent().trim()
                .replace("${user.home}", home).replace("${env.HOME}", home);
        return Optional.of(Path.of(value).toAbsolutePath().normalize());
    }
}
