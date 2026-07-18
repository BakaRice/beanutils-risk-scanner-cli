package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.model.TypeRef;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BeanPropertyResolver {
    private final BeanPropertyTraceLogger trace;

    BeanPropertyResolver(BeanPropertyTraceLogger trace) {
        this.trace = trace;
    }

    boolean canResolve(ResolvedType beanType) {
        try {
            return beanType != null && beanType.isReferenceType()
                    && beanType.asReferenceType().getTypeDeclaration().isPresent();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    Map<String, BeanProperty> resolve(ResolvedType beanType) {
        if (beanType == null || !beanType.isReferenceType()) {
            trace.error(beanType, "not-reference-type");
            return Map.of();
        }
        try {
            if (beanType.asReferenceType().getTypeDeclaration().isEmpty()) {
                trace.error(beanType, "missing-type-declaration");
                return Map.of();
            }
        } catch (RuntimeException exception) {
            trace.error(beanType, "type-declaration-resolution-failed");
            return Map.of();
        }
        Map<String, MutableProperty> properties = new LinkedHashMap<>();
        List<ResolvedReferenceType> hierarchy = new ArrayList<>();
        hierarchy.add(beanType.asReferenceType());
        try {
            hierarchy.addAll(beanType.asReferenceType().getAllAncestors());
        } catch (RuntimeException ignored) {
            // A partially resolved external hierarchy still leaves local methods usable.
        }
        for (ResolvedReferenceType reference : hierarchy) {
            ResolvedReferenceTypeDeclaration declaration = reference.getTypeDeclaration().orElse(null);
            if (declaration == null) {
                continue;
            }
            for (MethodUsage usage : declaration.getDeclaredMethods().stream().map(MethodUsage::new).toList()) {
                MethodUsage specialized = specialize(usage, reference);
                add(properties, specialized, declaration.getQualifiedName());
            }
        }
        Map<String, BeanProperty> result = new LinkedHashMap<>();
        properties.forEach((name, value) -> result.put(name, value.freeze(name)));
        boolean compiledEvidence = beanType.asReferenceType().getTypeDeclaration()
                .map(this::isCompiledDeclaration)
                .orElse(false);
        trace.resolved(beanType, hierarchy, result,
                compiledEvidence ? "compiled-class" : "source-fallback");
        return result;
    }

    private boolean isCompiledDeclaration(ResolvedReferenceTypeDeclaration declaration) {
        String implementation = declaration.getClass().getName();
        return implementation.contains("reflectionmodel") || implementation.contains("javassistmodel");
    }

    private MethodUsage specialize(MethodUsage usage, ResolvedReferenceType reference) {
        MethodUsage specialized = usage;
        for (var pair : reference.getTypeParametersMap()) {
            specialized = specialized.replaceTypeParameter(pair.a, pair.b);
        }
        return specialized;
    }

    private void add(Map<String, MutableProperty> properties, MethodUsage method, String owner) {
        try {
            if (!method.getDeclaration().accessSpecifier().asString().equals("public")
                    || method.getDeclaration().isStatic()) {
                return;
            }
        } catch (RuntimeException ignored) {
            return;
        }
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3 && method.getNoParams() == 0
                && !method.returnType().isVoid()) {
            properties.computeIfAbsent(decap(name.substring(3)), unused -> new MutableProperty())
                    .read(method.returnType(), owner);
        } else if (name.startsWith("is") && name.length() > 2 && method.getNoParams() == 0) {
            properties.computeIfAbsent(decap(name.substring(2)), unused -> new MutableProperty())
                    .read(method.returnType(), owner);
        } else if (name.startsWith("set") && name.length() > 3 && method.getNoParams() == 1) {
            properties.computeIfAbsent(decap(name.substring(3)), unused -> new MutableProperty())
                    .write(method.getParamType(0), owner);
        }
    }

    private String decap(String value) {
        return Introspector.decapitalize(value);
    }

    private TypeRef ref(ResolvedType type) {
        if (type == null) {
            return TypeRef.unresolved("?");
        }
        String description = type.describe();
        return new TypeRef(description.replaceAll("(?:[a-z_$][\\w$]*\\.)+([A-Z_$][\\w$]*)", "$1"),
                description, true);
    }

    private final class MutableProperty {
        private ResolvedType read;
        private ResolvedType write;
        private String getterOwner = "";
        private String setterOwner = "";

        MutableProperty read(ResolvedType type, String owner) {
            if (read == null) {
                read = type;
                getterOwner = owner;
            }
            return this;
        }

        MutableProperty write(ResolvedType type, String owner) {
            if (write == null) {
                write = type;
                setterOwner = owner;
            }
            return this;
        }

        BeanProperty freeze(String name) {
            return new BeanProperty(name, read, write, ref(read), ref(write), getterOwner, setterOwner);
        }
    }
}
