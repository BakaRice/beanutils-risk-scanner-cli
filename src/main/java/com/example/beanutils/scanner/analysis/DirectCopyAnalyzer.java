package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.discovery.CopyCallForm;
import com.example.beanutils.scanner.discovery.CopyCallSite;
import com.example.beanutils.scanner.model.CallChainStep;
import com.example.beanutils.scanner.model.CopyFinding;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.PropertyFinding;
import com.example.beanutils.scanner.model.PropertyMapping;
import com.example.beanutils.scanner.model.ReviewReason;
import com.example.beanutils.scanner.model.TypeRef;
import com.github.javaparser.ast.expr.NullLiteralExpr;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

public final class DirectCopyAnalyzer {
    private final BeanPropertyResolver propertyResolver;
    private final BeanPropertyTraceLogger trace;
    private final TypeCompatibilityEngine compatibility = new TypeCompatibilityEngine();

    public DirectCopyAnalyzer() {
        this(BeanPropertyTraceLogger.silent());
    }

    public DirectCopyAnalyzer(Consumer<String> traceOutput) {
        this(new BeanPropertyTraceLogger(traceOutput));
    }

    public DirectCopyAnalyzer(BeanPropertyTraceLogger trace) {
        this.trace = trace;
        this.propertyResolver = new BeanPropertyResolver(trace);
    }

    public CopyFinding analyze(CopyCallSite call) {
        if (call.form() == CopyCallForm.METHOD_REFERENCE) {
            trace.error("?", "method-reference-no-concrete-bean-types");
            return finding(call, FindingStatus.REVIEW, List.of(), List.of(new ReviewReason(
                    "METHOD_REFERENCE_TYPES_UNKNOWN",
                    "BeanUtils.copyProperties 以方法引用传递，静态扫描无法恢复每次执行时实际的 Source Bean 和 Target Bean 类型",
                    "CALL")));
        }
        if (call.sourceExpression() instanceof NullLiteralExpr) {
            trace.error(call.sourceType().qualifiedName(), "source-null-literal");
        }
        if (call.targetExpression() instanceof NullLiteralExpr) {
            trace.error(call.targetType().qualifiedName(), "target-null-literal");
        }
        if (call.sourceExpression() instanceof NullLiteralExpr || call.targetExpression() instanceof NullLiteralExpr) {
            List<ReviewReason> reasons = new ArrayList<>();
            if (call.sourceExpression() instanceof NullLiteralExpr) reasons.add(new ReviewReason("SOURCE_NULL_LITERAL",
                    "Source 参数是 null 字面量，无法建立 Source Bean 属性模型", "SOURCE"));
            if (call.targetExpression() instanceof NullLiteralExpr) reasons.add(new ReviewReason("TARGET_NULL_LITERAL",
                    "Target 参数是 null 字面量，无法建立 Target Bean 属性模型", "TARGET"));
            return finding(call, FindingStatus.REVIEW, List.of(), reasons);
        }
        if (call.resolvedSourceType() == null) {
            trace.error(call.sourceType().qualifiedName(), "source-type-unresolved");
        }
        if (call.resolvedTargetType() == null) {
            trace.error(call.targetType().qualifiedName(), "target-type-unresolved");
        }
        if (call.resolvedSourceType() == null || call.resolvedTargetType() == null) {
            List<ReviewReason> reasons = resolutionIssues(call);
            if (call.resolvedSourceType() == null) reasons.add(new ReviewReason("SOURCE_TYPE_UNRESOLVED",
                    "Source 表达式 “" + expression(call.sourceExpression()) + "” 的具体 Java 类型无法解析，不能列出其全部属性",
                    "SOURCE"));
            if (call.resolvedTargetType() == null) reasons.add(new ReviewReason("TARGET_TYPE_UNRESOLVED",
                    "Target 表达式 “" + expression(call.targetExpression()) + "” 的具体 Java 类型无法解析，不能列出其全部属性",
                    "TARGET"));
            return finding(call, FindingStatus.REVIEW, List.of(), reasons);
        }
        if (call.resolvedSourceType().isTypeVariable()) {
            trace.error(call.resolvedSourceType(), "source-type-variable");
        }
        if (call.resolvedTargetType().isTypeVariable()) {
            trace.error(call.resolvedTargetType(), "target-type-variable");
        }
        if (call.resolvedSourceType().isTypeVariable() || call.resolvedTargetType().isTypeVariable()) {
            List<ReviewReason> reasons = new ArrayList<>();
            if (call.resolvedSourceType().isTypeVariable()) reasons.add(new ReviewReason("SOURCE_TYPE_VARIABLE",
                    "Source 的静态类型是类型变量 “" + call.resolvedSourceType().describe()
                            + "”，调用点无法确定实际 Bean 类型和完整属性", "SOURCE"));
            if (call.resolvedTargetType().isTypeVariable()) reasons.add(new ReviewReason("TARGET_TYPE_VARIABLE",
                    "Target 的静态类型是类型变量 “" + call.resolvedTargetType().describe()
                            + "”，调用点无法确定实际 Bean 类型和完整属性", "TARGET"));
            return finding(call, FindingStatus.REVIEW, List.of(), reasons);
        }
        BeanPropertyResolution sourceResolution = propertyResolver.resolve(call.resolvedSourceType());
        BeanPropertyResolution targetResolution = propertyResolver.resolve(call.resolvedTargetType());
        var sources = sourceResolution.properties();
        var targets = targetResolution.properties();
        List<PropertyFinding> properties = new ArrayList<>();
        Set<String> ignoredMismatches = new LinkedHashSet<>();
        Set<String> propertyNames = new TreeSet<>();
        propertyNames.addAll(sources.keySet());
        propertyNames.addAll(targets.keySet());
        propertyNames.remove("class");
        for (String propertyName : propertyNames) {
            BeanProperty source = sources.get(propertyName);
            BeanProperty target = targets.get(propertyName);
            if (source == null) {
                properties.add(targetOnly(target));
                continue;
            }
            if (target == null) {
                properties.add(sourceOnly(source));
                continue;
            }
            if (source.readType() == null || target.writeType() == null) {
                properties.add(sameNameNotCopyable(source, target));
                continue;
            }
            var decision = compatibility.decide(source.readType(), target.writeType());
            boolean ignored = call.ignoredProperties().contains(propertyName)
                    || (call.form() == CopyCallForm.EDITABLE && !editableAllows(call, target));
            FindingStatus status = ignored ? FindingStatus.IGNORED : decision.status();
            if (ignored && decision.status() == FindingStatus.RISK) {
                ignoredMismatches.add(propertyName);
            }
            properties.add(new PropertyFinding(propertyName, source.readTypeRef(), target.writeTypeRef(),
                    source.getterOwner(), target.setterOwner(), PropertyMapping.MAPPED, status,
                    decision.oldAssignable(), decision.newDecision(),
                    ignored ? "属性被当前 BeanUtils 重载显式排除；" + decision.reason() : decision.reason()));
        }
        FindingStatus status = aggregate(call, properties, ignoredMismatches,
                sourceResolution.complete() && targetResolution.complete());
        List<ReviewReason> reviewReasons = status == FindingStatus.REVIEW
                ? reviewReasons(call, sourceResolution, targetResolution, properties) : List.of();
        return finding(call, status, properties, reviewReasons);
    }

    private PropertyFinding sourceOnly(BeanProperty source) {
        return new PropertyFinding(source.name(), propertyType(source, true), TypeRef.unresolved("Target 中不存在"),
                propertyOwner(source, true), "", PropertyMapping.SOURCE_ONLY, FindingStatus.SAFE,
                false, "NO_SAME_NAME_TARGET", "Source 属性在 Target 中没有同名 JavaBean 属性，两版本都不会复制");
    }

    private PropertyFinding targetOnly(BeanProperty target) {
        return new PropertyFinding(target.name(), TypeRef.unresolved("Source 中不存在"), propertyType(target, false),
                "", propertyOwner(target, false), PropertyMapping.TARGET_ONLY, FindingStatus.SAFE,
                false, "NO_SAME_NAME_SOURCE", "Target 属性在 Source 中没有同名 JavaBean 属性，两版本都不会复制");
    }

    private PropertyFinding sameNameNotCopyable(BeanProperty source, BeanProperty target) {
        boolean missingGetter = source.readType() == null;
        boolean missingSetter = target.writeType() == null;
        String decision = missingGetter && missingSetter ? "SKIPPED_NO_GETTER_OR_SETTER"
                : missingGetter ? "SKIPPED_NO_GETTER" : "SKIPPED_NO_SETTER";
        String reason = missingGetter && missingSetter
                ? "属性同名，但 Source 缺少 public getter 且 Target 缺少 public setter，两版本都不会复制"
                : missingGetter
                ? "属性同名，但 Source 缺少 public getter，两版本都不会复制"
                : "属性同名，但 Target 缺少 public setter，两版本都不会复制";
        return new PropertyFinding(source.name(), propertyType(source, true), propertyType(target, false),
                propertyOwner(source, true), propertyOwner(target, false),
                PropertyMapping.SAME_NAME_NOT_COPYABLE, FindingStatus.SAFE, false, decision, reason);
    }

    private TypeRef propertyType(BeanProperty property, boolean sourceSide) {
        if (sourceSide && property.readType() != null) return property.readTypeRef();
        if (!sourceSide && property.writeType() != null) return property.writeTypeRef();
        if (property.readType() != null) return property.readTypeRef();
        if (property.writeType() != null) return property.writeTypeRef();
        return TypeRef.unresolved("类型不可解析");
    }

    private String propertyOwner(BeanProperty property, boolean sourceSide) {
        if (sourceSide && !property.getterOwner().isBlank()) return property.getterOwner();
        if (!sourceSide && !property.setterOwner().isBlank()) return property.setterOwner();
        return !property.getterOwner().isBlank() ? property.getterOwner() : property.setterOwner();
    }

    private boolean editableAllows(CopyCallSite call, BeanProperty property) {
        if (call.editableType() == null) {
            return true;
        }
        String editable = call.editableType().qualifiedName();
        return editable.equals(property.setterOwner());
    }

    private FindingStatus aggregate(CopyCallSite call, List<PropertyFinding> properties,
                                    Set<String> ignoredMismatches, boolean propertyModelsComplete) {
        if (properties.stream().anyMatch(property -> property.status() == FindingStatus.RISK)) {
            return FindingStatus.RISK;
        }
        if (!call.resolutionComplete() || !call.ignoredPropertiesResolved() || !propertyModelsComplete) {
            return FindingStatus.REVIEW;
        }
        if (properties.stream().anyMatch(property -> property.status() == FindingStatus.REVIEW)) {
            return FindingStatus.REVIEW;
        }
        if (!ignoredMismatches.isEmpty()) {
            return FindingStatus.IGNORED;
        }
        if (call.form() == CopyCallForm.EDITABLE
                && properties.stream().anyMatch(property -> property.status() == FindingStatus.IGNORED)) {
            return FindingStatus.IGNORED;
        }
        return FindingStatus.SAFE;
    }

    private List<ReviewReason> reviewReasons(CopyCallSite call, BeanPropertyResolution sourceResolution,
                                             BeanPropertyResolution targetResolution,
                                             List<PropertyFinding> properties) {
        List<ReviewReason> reasons = resolutionIssues(call);
        if (!call.resolutionComplete() && call.resolutionIssues().isEmpty()) {
            reasons.add(new ReviewReason("CALL_RESOLUTION_INCOMPLETE",
                    "无法通过符号解析确认当前 copyProperties 调用的声明方法确实是 Spring BeanUtils；本次仅根据 import 和调用语法识别",
                    "CALL"));
        }
        if (!call.ignoredPropertiesResolved()) {
            reasons.add(new ReviewReason("IGNORE_PROPERTIES_UNRESOLVED",
                    "ignoreProperties 参数不是可静态确定的字符串集合，无法确认哪些同名属性会被排除", "CALL"));
        }
        addPropertyModelReasons(reasons, "SOURCE", call.sourceType(), sourceResolution);
        addPropertyModelReasons(reasons, "TARGET", call.targetType(), targetResolution);
        for (PropertyFinding property : properties) {
            if (property.status() != FindingStatus.REVIEW) continue;
            String code = property.reason().contains("raw type")
                    ? "RAW_GENERIC_PROPERTY" : "PROPERTY_COMPATIBILITY_UNKNOWN";
            reasons.add(new ReviewReason(code,
                    "同名属性 “" + property.propertyName() + "” 的兼容性无法确定：" + property.reason(),
                    "PROPERTY", property.propertyName()));
        }
        return List.copyOf(new LinkedHashSet<>(reasons));
    }

    private void addPropertyModelReasons(List<ReviewReason> reasons, String side, TypeRef type,
                                         BeanPropertyResolution resolution) {
        if (resolution.complete()) return;
        String label = side.equals("SOURCE") ? "Source" : "Target";
        if (resolution.issues().isEmpty()) {
            reasons.add(new ReviewReason(side + "_PROPERTY_MODEL_INCOMPLETE",
                    label + " Bean “" + type.qualifiedName() + "” 的类型声明、继承链或 getter/setter 未能完整解析",
                    side));
            return;
        }
        for (String issue : resolution.issues()) {
            reasons.add(new ReviewReason(side + "_PROPERTY_MODEL_INCOMPLETE",
                    label + " Bean “" + type.qualifiedName() + "” 的属性模型不完整：" + issue, side));
        }
    }

    private List<ReviewReason> resolutionIssues(CopyCallSite call) {
        List<ReviewReason> reasons = new ArrayList<>();
        for (String issue : call.resolutionIssues()) {
            String subject = issue.startsWith("Source") ? "SOURCE" : issue.startsWith("Target") ? "TARGET" : "CALL";
            reasons.add(new ReviewReason("SYMBOL_RESOLUTION_FAILED", issue, subject));
        }
        return reasons;
    }

    private String expression(Object expression) {
        return expression == null ? "<不存在>" : expression.toString();
    }

    private CopyFinding finding(CopyCallSite call, FindingStatus status, List<PropertyFinding> properties,
                                List<ReviewReason> reviewReasons) {
        List<CallChainStep> chain = List.of(new CallChainStep(call.location(), call.containingMethod(), call.ownerType()));
        return new CopyFinding(status, call.location(), call.code(), call.sourceType(), call.targetType(), properties,
                chain, call.form().name(), call.location().module(), reviewReasons);
    }
}
