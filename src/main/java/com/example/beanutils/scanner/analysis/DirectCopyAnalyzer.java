package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.discovery.CopyCallForm;
import com.example.beanutils.scanner.discovery.CopyCallSite;
import com.example.beanutils.scanner.model.CallChainStep;
import com.example.beanutils.scanner.model.CopyFinding;
import com.example.beanutils.scanner.model.FindingStatus;
import com.example.beanutils.scanner.model.PropertyFinding;
import com.example.beanutils.scanner.model.TypeRef;
import com.github.javaparser.ast.expr.NullLiteralExpr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        for (BeanProperty target : targets.values()) {
            BeanProperty source = sources.get(target.name());
            if (target.writeType() == null) {
                if (!target.name().equals("class") && source != null && source.readType() != null) {
                    TypeRef targetType = target.readType() == null
                            ? TypeRef.unresolved("缺少 setter") : target.readTypeRef();
                    properties.add(new PropertyFinding(target.name(), source.readTypeRef(), targetType,
                            FindingStatus.SAFE, false, "SKIPPED_NO_SETTER",
                            "Target 属性缺少 public setter，Spring 5.0.7 和 5.3.1 都不会复制"));
                }
                continue;
            }
            if (source == null || source.readType() == null) {
                continue;
            }
            var decision = compatibility.decide(source.readType(), target.writeType());
            boolean ignored = call.ignoredProperties().contains(target.name())
                    || (call.form() == CopyCallForm.EDITABLE && !editableAllows(call, target));
            FindingStatus status = ignored ? FindingStatus.IGNORED : decision.status();
            if (ignored && decision.status() == FindingStatus.RISK) {
                ignoredMismatches.add(target.name());
            }
            properties.add(new PropertyFinding(target.name(), source.readTypeRef(), target.writeTypeRef(), status,
                    decision.oldAssignable(), decision.newDecision(),
                    ignored ? "属性被当前 BeanUtils 重载显式排除；" + decision.reason() : decision.reason()));
        }
        properties.sort(Comparator.comparing(PropertyFinding::propertyName));
        FindingStatus status = aggregate(call, properties, ignoredMismatches);
        return finding(call, status, properties);
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
