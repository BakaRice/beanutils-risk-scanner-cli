package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.model.TypeRef;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

final class BeanPropertyResolver {
    private final BeanPropertyTraceLogger trace;

    BeanPropertyResolver(BeanPropertyTraceLogger trace) {
        this.trace = trace;
    }

    BeanPropertyResolution resolve(ResolvedType beanType) {
        if (beanType == null || !beanType.isReferenceType()) {
            trace.error(beanType, "not-reference-type");
            trace.error(beanType, "property-model-incomplete");
            return BeanPropertyResolution.incomplete("Bean 类型不是可解析的引用类型：" + describe(beanType));
        }
        try {
            if (beanType.asReferenceType().getTypeDeclaration().isEmpty()) {
                trace.error(beanType, "missing-type-declaration");
                trace.error(beanType, "property-model-incomplete");
                return BeanPropertyResolution.incomplete("找不到 Bean 类型声明：" + describe(beanType));
            }
        } catch (RuntimeException | LinkageError exception) {
            trace.error(beanType, "type-declaration-resolution-failed");
            trace.error(beanType, "property-model-incomplete");
            return BeanPropertyResolution.incomplete("读取 Bean 类型声明失败：" + failure(exception));
        }
        boolean complete = true;
        Set<String> issues = new LinkedHashSet<>();
        Map<String, MutableProperty> properties = new LinkedHashMap<>();
        List<ResolvedReferenceType> hierarchy = new ArrayList<>();
        hierarchy.add(beanType.asReferenceType());
        try {
            hierarchy.addAll(beanType.asReferenceType().getAllAncestors());
        } catch (RuntimeException | LinkageError exception) {
            complete = false;
            trace.error(beanType, "ancestor-resolution-failed");
            issues.add("解析父类/接口继承链失败：" + ancestorFailure(beanType, exception));
        }
        for (ResolvedReferenceType reference : hierarchy) {
            ResolvedReferenceTypeDeclaration declaration;
            try {
                declaration = reference.getTypeDeclaration().orElse(null);
            } catch (RuntimeException | LinkageError exception) {
                complete = false;
                issues.add("读取继承类型声明失败（" + describe(reference) + "）：" + failure(exception));
                continue;
            }
            if (declaration == null) {
                complete = false;
                issues.add("继承链中的类型没有可用声明：" + describe(reference));
                continue;
            }
            List<MethodUsage> methods;
            try {
                methods = declaration.getDeclaredMethods().stream().map(MethodUsage::new).toList();
            } catch (RuntimeException | LinkageError exception) {
                complete = false;
                issues.add("读取 " + declaration.getQualifiedName() + " 的 JavaBean 方法失败：" + failure(exception));
                continue;
            }
            for (MethodUsage usage : methods) {
                try {
                    MethodUsage specialized = specialize(usage, reference);
                    if (!add(properties, specialized, declaration.getQualifiedName())) {
                        complete = false;
                        issues.add("解析 " + declaration.getQualifiedName() + "#" + usage.getName()
                                + " 的 getter/setter 签名失败");
                    }
                } catch (RuntimeException | LinkageError exception) {
                    complete = false;
                    issues.add("解析 " + declaration.getQualifiedName() + "#" + usage.getName()
                            + " 的泛型方法签名失败：" + failure(exception));
                }
            }
        }
        Map<String, BeanProperty> result = new LinkedHashMap<>();
        properties.forEach((name, value) -> result.put(name, value.freeze(name)));
        boolean compiledEvidence = beanType.asReferenceType().getTypeDeclaration()
                .map(this::isCompiledDeclaration)
                .orElse(false);
        trace.resolved(beanType, hierarchy, result,
                compiledEvidence ? "compiled-class" : "source-fallback");
        if (!complete) {
            trace.error(beanType, "property-model-incomplete");
        }
        return new BeanPropertyResolution(result, complete, List.copyOf(issues));
    }

    private String describe(Object value) {
        if (value == null) return "?";
        try {
            if (value instanceof ResolvedType type) return type.describe();
            if (value instanceof ResolvedReferenceType type) return type.describe();
        } catch (RuntimeException | LinkageError ignored) {
            // Fall through to a safe representation.
        }
        return String.valueOf(value);
    }

    private String failure(Throwable failure) {
        Throwable useful = failure;
        while (useful.getCause() != null && useful.getCause() != useful) useful = useful.getCause();
        String message = useful.getMessage();
        if (message == null || message.isBlank()) message = failure.getMessage();
        String type = useful.getClass().getSimpleName();
        return message == null || message.isBlank() ? type : type + "：" + message.replace('/', '.');
    }

    private String ancestorFailure(ResolvedType beanType, Throwable exception) {
        String basic = failure(exception);
        try {
            var declaration = beanType.asReferenceType().getTypeDeclaration().orElse(null);
            if (declaration == null) return basic;
            var ast = declaration.toAst(ClassOrInterfaceDeclaration.class).orElse(null);
            if (ast == null) return basic;
            String missingSimpleName = basic.replaceFirst("(?s).*Unsolved symbol\\s*:\\s*", "").trim();
            if (missingSimpleName.contains(" ")) missingSimpleName = missingSimpleName.substring(0, missingSimpleName.indexOf(' '));
            List<ClassOrInterfaceType> ancestors = new ArrayList<>();
            ancestors.addAll(ast.getExtendedTypes());
            ancestors.addAll(ast.getImplementedTypes());
            for (ClassOrInterfaceType ancestor : ancestors) {
                String written = ancestor.getNameWithScope();
                if (!written.equals(missingSimpleName) && !written.endsWith("." + missingSimpleName)) continue;
                String qualified = qualify(ast, written);
                return basic + "（缺失类型：" + qualified + "）";
            }
        } catch (RuntimeException | LinkageError ignored) {
            // The original resolution failure remains the useful evidence.
        }
        return basic;
    }

    private String qualify(ClassOrInterfaceDeclaration declaration, String written) {
        if (written.contains(".")) return written;
        var unit = declaration.findCompilationUnit().orElse(null);
        if (unit == null) return written;
        var imported = unit.getImports().stream()
                .filter(value -> !value.isAsterisk() && value.getName().getIdentifier().equals(written))
                .map(value -> value.getNameAsString()).findFirst();
        if (imported.isPresent()) return imported.orElseThrow();
        return unit.getPackageDeclaration().map(value -> value.getNameAsString() + "." + written).orElse(written);
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

    private boolean add(Map<String, MutableProperty> properties, MethodUsage method, String owner) {
        try {
            if (!method.getDeclaration().accessSpecifier().asString().equals("public")
                    || method.getDeclaration().isStatic()) {
                return true;
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
            return true;
        } catch (RuntimeException | LinkageError exception) {
            return false;
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
