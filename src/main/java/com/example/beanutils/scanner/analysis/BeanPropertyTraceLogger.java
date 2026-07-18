package com.example.beanutils.scanner.analysis;

import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class BeanPropertyTraceLogger {
    private static final Consumer<String> NO_OUTPUT = unused -> { };

    private final Consumer<String> output;
    private final Set<String> tracedTypes = new LinkedHashSet<>();
    private final Set<String> tracedErrors = new LinkedHashSet<>();

    public BeanPropertyTraceLogger(Consumer<String> output) {
        this.output = output == null ? NO_OUTPUT : output;
    }

    public static BeanPropertyTraceLogger silent() {
        return new BeanPropertyTraceLogger(NO_OUTPUT);
    }

    synchronized void resolved(ResolvedType beanType, List<ResolvedReferenceType> hierarchy,
                               Map<String, BeanProperty> properties, String evidence) {
        String beanName = describe(beanType);
        if (!tracedTypes.add(beanName)) {
            return;
        }
        String hierarchyText = hierarchy.stream().map(this::describe).reduce((left, right) -> left + " -> " + right)
                .orElse("-");
        output.accept("[BeanUtilsScanner][BEAN] type=" + beanName
                + " evidence=" + value(evidence) + " hierarchy=" + hierarchyText);
        List<BeanProperty> businessProperties = properties.values().stream()
                .filter(property -> !property.name().equals("class"))
                .sorted(java.util.Comparator.comparing(BeanProperty::name))
                .toList();
        for (BeanProperty property : businessProperties) {
            output.accept("[BeanUtilsScanner][PROPERTY] bean=" + beanName
                    + " name=" + property.name()
                    + " readType=" + type(property.readTypeRef(), property.readType())
                    + " writeType=" + type(property.writeTypeRef(), property.writeType())
                    + " getterOwner=" + value(property.getterOwner())
                    + " setterOwner=" + value(property.setterOwner()));
        }
        output.accept("[BeanUtilsScanner][BEAN-END] type=" + beanName
                + " properties=" + businessProperties.size());
    }

    synchronized void error(ResolvedType beanType, String reason) {
        error(describe(beanType), reason);
    }

    synchronized void error(String beanName, String reason) {
        String normalizedName = beanName == null || beanName.isBlank() ? "?" : beanName;
        if (tracedErrors.add(normalizedName + "|" + reason)) {
            output.accept("[BeanUtilsScanner][BEAN-ERROR] type=" + normalizedName + " reason=" + reason);
        }
    }

    private String type(com.example.beanutils.scanner.model.TypeRef reference, ResolvedType resolvedType) {
        return resolvedType == null ? "-" : reference.qualifiedName();
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String describe(ResolvedType type) {
        if (type == null) {
            return "?";
        }
        try {
            return type.describe();
        } catch (RuntimeException exception) {
            return type.toString();
        }
    }

    private String describe(ResolvedReferenceType type) {
        try {
            return type.describe();
        } catch (RuntimeException exception) {
            return type.toString();
        }
    }
}
