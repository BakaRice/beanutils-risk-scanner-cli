package com.example.beanutils.scanner.discovery;

import com.example.beanutils.scanner.model.SourceLocation;
import com.example.beanutils.scanner.model.TypeRef;
import com.example.beanutils.scanner.source.ParsedSource;
import com.example.beanutils.scanner.source.SourceWorkspace;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.resolution.types.ResolvedType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class BeanUtilsCallDetector {
    private static final String SPRING_BEAN_UTILS = "org.springframework.beans.BeanUtils";
    private final IgnorePropertyResolver ignorePropertyResolver = new IgnorePropertyResolver();

    public List<CopyCallSite> discover(SourceWorkspace workspace) {
        List<CopyCallSite> calls = new ArrayList<>();
        for (ParsedSource source : workspace.parsedSources()) {
            CompilationUnit unit = source.compilationUnit();
            unit.findAll(MethodCallExpr.class).stream()
                    .filter(call -> call.getNameAsString().equals("copyProperties"))
                    .filter(call -> isSpringCall(call, unit))
                    .map(call -> methodCall(workspace, source, call))
                    .forEach(calls::add);
            unit.findAll(MethodReferenceExpr.class).stream()
                    .filter(reference -> reference.getIdentifier().equals("copyProperties"))
                    .filter(reference -> isSpringReference(reference, unit))
                    .map(reference -> methodReference(workspace, source, reference))
                    .forEach(calls::add);
        }
        calls.sort(Comparator.comparing((CopyCallSite call) -> call.location().relativePath())
                .thenComparingInt(call -> call.location().line())
                .thenComparingInt(call -> call.location().column()));
        return List.copyOf(calls);
    }

    private CopyCallSite methodCall(SourceWorkspace workspace, ParsedSource source, MethodCallExpr call) {
        Expression sourceExpression = call.getArguments().isEmpty() ? null : call.getArgument(0);
        Expression targetExpression = call.getArguments().size() < 2 ? null : call.getArgument(1);
        CopyCallForm form = CopyCallForm.DIRECT;
        Set<String> ignored = Set.of();
        boolean ignoresResolved = true;
        TypeRef editable = null;
        List<String> resolutionIssues = new ArrayList<>();
        if (call.getArguments().size() >= 3 && call.getArgument(2) instanceof ClassExpr classExpr) {
            form = CopyCallForm.EDITABLE;
            editable = resolveClassType(classExpr);
        } else if (call.getArguments().size() >= 3) {
            form = CopyCallForm.IGNORE_PROPERTIES;
            var result = ignorePropertyResolver.resolve(
                    call.getArguments().subList(2, call.getArguments().size()), source.compilationUnit());
            ignored = result.names();
            ignoresResolved = result.complete();
        }
        boolean exact = resolvesToSpring(call, resolutionIssues);
        ResolvedType resolvedSource = resolvedType(sourceExpression, "Source", resolutionIssues);
        ResolvedType resolvedTarget = resolvedType(targetExpression, "Target", resolutionIssues);
        return new CopyCallSite(call, sourceExpression, targetExpression,
                typeRef(workspace, resolvedSource, sourceExpression, "Source", resolutionIssues),
                typeRef(workspace, resolvedTarget, targetExpression, "Target", resolutionIssues),
                resolvedSource, resolvedTarget, ignored,
                ignoresResolved, editable, exact, location(workspace, source, call), call.toString(),
                containingMethod(call), ownerType(call), form, resolutionIssues);
    }

    private CopyCallSite methodReference(SourceWorkspace workspace, ParsedSource source, MethodReferenceExpr reference) {
        List<String> resolutionIssues = new ArrayList<>();
        boolean exact = resolvesToSpring(reference, resolutionIssues);
        return new CopyCallSite(reference, null, null, TypeRef.unresolved("method-reference-source"),
                TypeRef.unresolved("method-reference-target"), null, null, Set.of(), true, null,
                exact, location(workspace, source, reference), reference.toString(),
                containingMethod(reference), ownerType(reference), CopyCallForm.METHOD_REFERENCE, resolutionIssues);
    }

    private boolean isSpringCall(MethodCallExpr call, CompilationUnit unit) {
        if (resolvesToSpring(call)) {
            return true;
        }
        if (call.getScope().map(Object::toString).filter(SPRING_BEAN_UTILS::equals).isPresent()) {
            return true;
        }
        if (call.getScope().map(Object::toString).filter("BeanUtils"::equals).isPresent()) {
            return imports(unit, SPRING_BEAN_UTILS, false);
        }
        return call.getScope().isEmpty() && imports(unit, SPRING_BEAN_UTILS + ".copyProperties", true);
    }

    private boolean isSpringReference(MethodReferenceExpr reference, CompilationUnit unit) {
        if (resolvesToSpring(reference)) {
            return true;
        }
        String scope = reference.getScope().toString();
        return scope.equals(SPRING_BEAN_UTILS)
                || (scope.equals("BeanUtils") && imports(unit, SPRING_BEAN_UTILS, false));
    }

    private boolean imports(CompilationUnit unit, String name, boolean staticImport) {
        return unit.getImports().stream().anyMatch(anImport -> anImport.isStatic() == staticImport
                && (anImport.getNameAsString().equals(name)
                || (anImport.isAsterisk() && name.startsWith(anImport.getNameAsString()))));
    }

    private boolean resolvesToSpring(MethodCallExpr call) {
        return resolvesToSpring(call, new ArrayList<>());
    }

    private boolean resolvesToSpring(MethodCallExpr call, List<String> issues) {
        try {
            String owner = call.resolve().declaringType().getQualifiedName();
            boolean spring = owner.equals(SPRING_BEAN_UTILS);
            if (!spring) issues.add("copyProperties 方法解析到 " + owner + "，不是 " + SPRING_BEAN_UTILS);
            return spring;
        } catch (RuntimeException | LinkageError exception) {
            issues.add("解析 BeanUtils.copyProperties 方法声明失败：" + failure(exception));
            return false;
        }
    }

    private boolean resolvesToSpring(MethodReferenceExpr reference) {
        return resolvesToSpring(reference, new ArrayList<>());
    }

    private boolean resolvesToSpring(MethodReferenceExpr reference, List<String> issues) {
        try {
            String owner = reference.resolve().declaringType().getQualifiedName();
            boolean spring = owner.equals(SPRING_BEAN_UTILS);
            if (!spring) issues.add("copyProperties 方法引用解析到 " + owner + "，不是 " + SPRING_BEAN_UTILS);
            return spring;
        } catch (RuntimeException | LinkageError exception) {
            issues.add("解析 BeanUtils.copyProperties 方法引用失败：" + failure(exception));
            return false;
        }
    }

    private ResolvedType resolvedType(Expression expression, String side, List<String> issues) {
        if (expression == null) {
            issues.add(side + " 表达式不存在");
            return null;
        }
        try {
            ResolvedType type = expression.calculateResolvedType();
            if (type.isConstraint()) {
                type = type.asConstraintType().getBound();
            }
            if (type.isWildcard() && type.asWildcard().isBounded()) {
                return type.asWildcard().getBoundedType();
            }
            return type;
        } catch (RuntimeException | LinkageError exception) {
            issues.add(side + " 表达式 “" + expression + "” 的类型解析失败：" + failure(exception));
            return null;
        }
    }

    private TypeRef typeRef(SourceWorkspace workspace, ResolvedType type, Expression expression,
                            String side, List<String> issues) {
        if (type == null) {
            return TypeRef.unresolved(expression == null ? "missing-expression" : expression.toString());
        }
        try {
            String qualified = type.describe();
            TypeRef reference = new TypeRef(shortName(qualified), qualified, true);
            String rawQualifiedName = type.isReferenceType()
                    ? type.asReferenceType().getQualifiedName() : qualified.replaceAll("<.*>", "");
            return workspace.typeLocation(rawQualifiedName)
                    .map(location -> reference.withOrigin(location.module(), location.relativePath()))
                    .orElse(reference);
        } catch (RuntimeException | LinkageError exception) {
            issues.add(side + " 类型来源定位失败：" + failure(exception));
            return TypeRef.unresolved(expression == null ? "missing-expression" : expression.toString());
        }
    }

    private String failure(Throwable failure) {
        Throwable useful = failure;
        while (useful.getCause() != null && useful.getCause() != useful) useful = useful.getCause();
        String message = useful.getMessage();
        if (message == null || message.isBlank()) message = failure.getMessage();
        String type = useful.getClass().getSimpleName();
        return message == null || message.isBlank() ? type : type + "：" + message.replace('/', '.');
    }

    private TypeRef resolveClassType(ClassExpr expression) {
        try {
            String qualified = expression.getType().resolve().describe();
            return new TypeRef(shortName(qualified), qualified, true);
        } catch (RuntimeException | LinkageError exception) {
            return TypeRef.unresolved(expression.getTypeAsString());
        }
    }

    private String shortName(String qualified) {
        return qualified.replaceAll("(?:[a-z_$][\\w$]*\\.)+([A-Z_$][\\w$]*)", "$1");
    }

    private SourceLocation location(SourceWorkspace workspace, ParsedSource source, Node node) {
        Path root = workspace.project().rootDirectory();
        String relative;
        try {
            relative = root.relativize(source.path()).toString().replace('\\', '/');
        } catch (IllegalArgumentException exception) {
            relative = source.path().toString();
        }
        int line = node.getBegin().map(position -> position.line).orElse(0);
        int column = node.getBegin().map(position -> position.column).orElse(0);
        return new SourceLocation(source.module(), relative, line, column);
    }

    private String containingMethod(Node node) {
        return node.findAncestor(MethodDeclaration.class)
                .map(callable -> callable.getSignature().asString())
                .or(() -> node.findAncestor(ConstructorDeclaration.class)
                        .map(callable -> callable.getSignature().asString()))
                .orElse("<initializer>");
    }

    private String ownerType(Node node) {
        return node.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(type -> type.getFullyQualifiedName().orElse(type.getNameAsString())).orElse("");
    }
}
