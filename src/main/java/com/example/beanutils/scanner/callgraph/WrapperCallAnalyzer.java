package com.example.beanutils.scanner.callgraph;

import com.example.beanutils.scanner.analysis.DirectCopyAnalyzer;
import com.example.beanutils.scanner.analysis.BeanPropertyTraceLogger;
import com.example.beanutils.scanner.discovery.CopyCallForm;
import com.example.beanutils.scanner.discovery.CopyCallSite;
import com.example.beanutils.scanner.model.CallChainStep;
import com.example.beanutils.scanner.model.CopyFinding;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.SourceLocation;
import com.example.beanutils.scanner.model.TypeRef;
import com.example.beanutils.scanner.source.ParsedSource;
import com.example.beanutils.scanner.source.SourceWorkspace;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.resolution.types.ResolvedType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WrapperCallAnalyzer {
    private final BeanPropertyTraceLogger trace;

    public WrapperCallAnalyzer() {
        this(BeanPropertyTraceLogger.silent());
    }

    public WrapperCallAnalyzer(BeanPropertyTraceLogger trace) {
        this.trace = trace;
    }

    public List<CopyFinding> analyze(SourceWorkspace workspace, List<CopyCallSite> directCalls) {
        Map<String, List<CopyCallSite>> seeds = new LinkedHashMap<>();
        for (CopyCallSite call : directCalls) {
            if (call.form() != CopyCallForm.METHOD_REFERENCE && !call.ownerType().isBlank()
                    && hasTypeVariable(call)) {
                seeds.computeIfAbsent(key(call.ownerType(), methodName(call.containingMethod())), unused -> new ArrayList<>())
                        .add(call);
            }
        }
        List<CopyFinding> findings = new ArrayList<>();
        DirectCopyAnalyzer analyzer = new DirectCopyAnalyzer(trace);
        for (ParsedSource source : workspace.parsedSources()) {
            for (MethodCallExpr call : source.compilationUnit().findAll(MethodCallExpr.class)) {
                String resolvedKey = resolvedKey(call);
                List<CopyCallSite> terminalCalls = resolvedKey == null ? List.of() : seeds.getOrDefault(resolvedKey, List.of());
                if (!terminalCalls.isEmpty()) {
                    CopyCallSite synthetic = synthetic(workspace, source, call, CopyCallForm.WRAPPER);
                    CopyFinding base = analyzer.analyze(synthetic);
                    CopyCallSite terminal = selectOverload(call, terminalCalls);
                    findings.add(asReview(base, terminal, "项目内包装方法调用，已恢复已知调用链"));
                } else if (isHigherOrderCopy(call, source)) {
                    CopyCallSite synthetic = synthetic(workspace, source, call, CopyCallForm.HIGHER_ORDER);
                    CopyFinding base = analyzer.analyze(synthetic);
                    findings.add(asReview(base, null, "复制操作通过函数对象传入，需要人工复核"));
                }
            }
        }
        return List.copyOf(findings);
    }

    private boolean hasTypeVariable(CopyCallSite call) {
        return (call.resolvedSourceType() != null && call.resolvedSourceType().isTypeVariable())
                || (call.resolvedTargetType() != null && call.resolvedTargetType().isTypeVariable());
    }

    private CopyCallSite selectOverload(MethodCallExpr call, List<CopyCallSite> terminals) {
        if (terminals.size() == 1 || call.getArguments().size() < 2) return terminals.get(0);
        Expression target = call.getArgument(1);
        String marker = target instanceof ClassExpr ? "Class" : target instanceof MethodReferenceExpr ? "Supplier" : "";
        return terminals.stream().filter(value -> value.containingMethod().contains(marker)).findFirst().orElse(terminals.get(0));
    }

    private CopyCallSite synthetic(SourceWorkspace workspace, ParsedSource source, MethodCallExpr call, CopyCallForm form) {
        Expression sourceExpression = call.getArguments().isEmpty() ? null : call.getArgument(0);
        Expression targetExpression = call.getArguments().size() < 2 ? null : targetExpression(call.getArgument(1));
        ResolvedType sourceType = resolve(sourceExpression);
        ResolvedType targetType = resolveTarget(call.getArguments().size() < 2 ? null : call.getArgument(1), targetExpression);
        return new CopyCallSite(call, sourceExpression, targetExpression, ref(workspace, sourceType, sourceExpression),
                ref(workspace, targetType, targetExpression), sourceType, targetType, Set.of(), true, null, true,
                location(workspace, source, call), call.toString(), containingMethod(call), ownerType(call), form);
    }

    private Expression targetExpression(Expression argument) {
        if (argument instanceof MethodReferenceExpr reference) {
            return reference.getScope();
        }
        return argument;
    }

    private ResolvedType resolveTarget(Expression argument, Expression target) {
        if (argument instanceof ClassExpr classExpr) {
            try {
                return classExpr.getType().resolve();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return resolve(target);
    }

    private ResolvedType resolve(Expression expression) {
        if (expression == null) return null;
        try {
            ResolvedType type = expression.calculateResolvedType();
            if (type.isConstraint()) type = type.asConstraintType().getBound();
            if (type.isWildcard() && type.asWildcard().isBounded()) return type.asWildcard().getBoundedType();
            return type;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private TypeRef ref(SourceWorkspace workspace, ResolvedType type, Expression expression) {
        if (type == null) return TypeRef.unresolved(expression == null ? "?" : expression.toString());
        TypeRef reference = new TypeRef(shortName(type.describe()), type.describe(), true);
        String rawQualifiedName = type.isReferenceType()
                ? type.asReferenceType().getQualifiedName() : type.describe().replaceAll("<.*>", "");
        return workspace.typeLocation(rawQualifiedName)
                .map(location -> reference.withOrigin(location.module(), location.relativePath()))
                .orElse(reference);
    }

    private CopyFinding asReview(CopyFinding base, CopyCallSite terminal, String reason) {
        List<CallChainStep> chain = new ArrayList<>(base.callChain());
        if (terminal != null) {
            chain.add(new CallChainStep(terminal.location(), terminal.containingMethod(),
                    "最终调用 org.springframework.beans.BeanUtils.copyProperties"));
        }
        return new CopyFinding(FindingStatus.REVIEW, base.location(), base.code(), base.sourceType(), base.targetType(),
                base.properties(), chain, base.callForm() + " · " + reason, base.module());
    }

    private boolean isHigherOrderCopy(MethodCallExpr call, ParsedSource source) {
        if (call.getArguments().size() < 3) return false;
        for (Expression argument : call.getArguments()) {
            if (argument instanceof MethodReferenceExpr reference && springReference(reference)) return true;
            if (argument instanceof NameExpr name) {
                boolean matched = source.compilationUnit().findAll(VariableDeclarator.class).stream()
                        .filter(variable -> variable.getNameAsString().equals(name.getNameAsString()))
                        .flatMap(variable -> variable.getInitializer().stream())
                        .filter(Expression::isMethodReferenceExpr)
                        .map(Expression::asMethodReferenceExpr)
                        .anyMatch(this::springReference);
                if (matched) return true;
            }
        }
        return false;
    }

    private boolean springReference(MethodReferenceExpr reference) {
        try {
            return reference.resolve().declaringType().getQualifiedName().equals("org.springframework.beans.BeanUtils");
        } catch (RuntimeException exception) {
            return reference.getScope().toString().endsWith("BeanUtils")
                    && reference.getIdentifier().equals("copyProperties");
        }
    }

    private String resolvedKey(MethodCallExpr call) {
        try {
            var declaration = call.resolve();
            return key(declaration.declaringType().getQualifiedName(), declaration.getName());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String key(String owner, String method) {
        return owner + "#" + method;
    }

    private String methodName(String signature) {
        int parenthesis = signature.indexOf('(');
        return parenthesis < 0 ? signature : signature.substring(0, parenthesis);
    }

    private SourceLocation location(SourceWorkspace workspace, ParsedSource source, Node node) {
        Path root = workspace.project().rootDirectory();
        String relative = root.relativize(source.path()).toString().replace('\\', '/');
        return new SourceLocation(source.module(), relative,
                node.getBegin().map(value -> value.line).orElse(0),
                node.getBegin().map(value -> value.column).orElse(0));
    }

    private String containingMethod(Node node) {
        return node.findAncestor(MethodDeclaration.class)
                .map(value -> value.getSignature().asString())
                .or(() -> node.findAncestor(ConstructorDeclaration.class)
                        .map(value -> value.getSignature().asString()))
                .orElse("<initializer>");
    }

    private String ownerType(Node node) {
        return node.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(value -> value.getFullyQualifiedName().orElse(value.getNameAsString())).orElse("");
    }

    private String shortName(String qualified) {
        return qualified.replaceAll("(?:[a-z_$][\\w$]*\\.)+([A-Z_$][\\w$]*)", "$1");
    }
}
