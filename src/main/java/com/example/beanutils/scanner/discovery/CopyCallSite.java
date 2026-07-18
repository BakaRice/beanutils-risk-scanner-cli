package com.example.beanutils.scanner.discovery;

import com.example.beanutils.scanner.model.SourceLocation;
import com.example.beanutils.scanner.model.TypeRef;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.Set;
import java.util.List;

public record CopyCallSite(
        Node node,
        Expression sourceExpression,
        Expression targetExpression,
        TypeRef sourceType,
        TypeRef targetType,
        ResolvedType resolvedSourceType,
        ResolvedType resolvedTargetType,
        Set<String> ignoredProperties,
        boolean ignoredPropertiesResolved,
        TypeRef editableType,
        boolean resolutionComplete,
        SourceLocation location,
        String code,
        String containingMethod,
        String ownerType,
        CopyCallForm form,
        List<String> resolutionIssues) {

    public CopyCallSite {
        ignoredProperties = ignoredProperties == null ? Set.of() : Set.copyOf(ignoredProperties);
        code = code == null ? "" : code.replaceAll("\\s+", " ").trim();
        containingMethod = containingMethod == null ? "" : containingMethod;
        ownerType = ownerType == null ? "" : ownerType;
        resolutionIssues = resolutionIssues == null ? List.of() : List.copyOf(resolutionIssues);
    }

    public CopyCallSite(Node node, Expression sourceExpression, Expression targetExpression, TypeRef sourceType,
                        TypeRef targetType, ResolvedType resolvedSourceType, ResolvedType resolvedTargetType,
                        Set<String> ignoredProperties, boolean ignoredPropertiesResolved, TypeRef editableType,
                        boolean resolutionComplete, SourceLocation location, String code, String containingMethod,
                        String ownerType, CopyCallForm form) {
        this(node, sourceExpression, targetExpression, sourceType, targetType, resolvedSourceType,
                resolvedTargetType, ignoredProperties, ignoredPropertiesResolved, editableType, resolutionComplete,
                location, code, containingMethod, ownerType, form, List.of());
    }
}
