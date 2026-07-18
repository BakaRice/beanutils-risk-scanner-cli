package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.discovery.CopyCallForm;
import com.example.beanutils.scanner.discovery.CopyCallSite;
import com.example.beanutils.scanner.model.CallChainStep;
import com.example.beanutils.scanner.model.CopyFinding;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.PropertyFinding;
import com.example.beanutils.scanner.model.PropertyMapping;
import com.example.beanutils.scanner.model.TypeRef;
import com.github.javaparser.ast.expr.NullLiteralExpr;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class DirectCopyAnalyzer {
    private final BeanPropertyResolver propertyResolver = new BeanPropertyResolver();
    private final TypeCompatibilityEngine compatibility = new TypeCompatibilityEngine();

    public CopyFinding analyze(CopyCallSite call) {
        if (call.form() == CopyCallForm.METHOD_REFERENCE
                || call.sourceExpression() instanceof NullLiteralExpr
                || call.targetExpression() instanceof NullLiteralExpr
                || call.resolvedSourceType() == null || call.resolvedTargetType() == null) {
            return finding(call, FindingStatus.REVIEW, List.of());
        }
        if (call.resolvedSourceType().isTypeVariable() || call.resolvedTargetType().isTypeVariable()) {
            return finding(call, FindingStatus.REVIEW, List.of());
        }
        Map<String, BeanProperty> sources = propertyResolver.resolve(call.resolvedSourceType());
        Map<String, BeanProperty> targets = propertyResolver.resolve(call.resolvedTargetType());
        if (!propertyResolver.canResolve(call.resolvedSourceType())
                || !propertyResolver.canResolve(call.resolvedTargetType())) {
            return finding(call, FindingStatus.REVIEW, List.of());
        }
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
        FindingStatus status = aggregate(call, properties, ignoredMismatches);
        return finding(call, status, properties);
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

    private FindingStatus aggregate(CopyCallSite call, List<PropertyFinding> properties, Set<String> ignoredMismatches) {
        if (!call.resolutionComplete() || !call.ignoredPropertiesResolved()) {
            return FindingStatus.REVIEW;
        }
        if (properties.stream().anyMatch(property -> property.status() == FindingStatus.RISK)) {
            return FindingStatus.RISK;
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

    private CopyFinding finding(CopyCallSite call, FindingStatus status, List<PropertyFinding> properties) {
        List<CallChainStep> chain = List.of(new CallChainStep(call.location(), call.containingMethod(), call.ownerType()));
        return new CopyFinding(status, call.location(), call.code(), call.sourceType(), call.targetType(), properties,
                chain, call.form().name(), call.location().module());
    }
}
