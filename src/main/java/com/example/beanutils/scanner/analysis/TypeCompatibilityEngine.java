package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.model.FindingStatus;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;

final class TypeCompatibilityEngine {
    private boolean complete;

    Decision decide(ResolvedType source, ResolvedType target) {
        complete = true;
        if (source == null || target == null || source.isTypeVariable() || target.isTypeVariable()
                || source.isWildcard() || target.isWildcard()) {
            return unknown();
        }
        boolean raw = isRaw(source) || isRaw(target);
        if (!complete) {
            return unknown();
        }
        if (raw) {
            return new Decision(FindingStatus.REVIEW, oldAssignable(source, target), "UNKNOWN",
                    "raw type 已丢失泛型参数，需要人工复核");
        }
        boolean oldAssignable = oldAssignable(source, target);
        boolean newAssignable = newAssignable(source, target);
        if (!complete) {
            return unknown();
        }
        if (newAssignable) {
            return new Decision(FindingStatus.SAFE, oldAssignable, "ASSIGNABLE",
                    "Spring 5.3 泛型感知检查可赋值");
        }
        if (oldAssignable) {
            return new Decision(FindingStatus.RISK, true, "REJECTED",
                    "原始类型可赋值，但泛型参数不兼容，升级后将不再复制");
        }
        return new Decision(FindingStatus.SAFE, false, "REJECTED_BOTH",
                "原始类型也不可赋值，升级前后都会跳过");
    }

    private boolean newAssignable(ResolvedType source, ResolvedType target) {
        if (boxingPair(source, target)) {
            return true;
        }
        try {
            return target.isAssignableBy(source);
        } catch (RuntimeException | LinkageError exception) {
            complete = false;
            return false;
        }
    }

    private boolean oldAssignable(ResolvedType source, ResolvedType target) {
        if (boxingPair(source, target)) {
            return true;
        }
        try {
            if (source.isArray() && target.isArray()) {
                return oldAssignable(source.asArrayType().getComponentType(), target.asArrayType().getComponentType());
            }
            if (source.isReferenceType() && target.isReferenceType()) {
                var sourceDeclaration = source.asReferenceType().getTypeDeclaration();
                var targetDeclaration = target.asReferenceType().getTypeDeclaration();
                return sourceDeclaration.isPresent() && targetDeclaration.isPresent()
                        && targetDeclaration.orElseThrow().isAssignableBy(sourceDeclaration.orElseThrow());
            }
            return source.describe().equals(target.describe());
        } catch (RuntimeException | LinkageError exception) {
            complete = false;
            return false;
        }
    }

    private boolean boxingPair(ResolvedType source, ResolvedType target) {
        try {
            if (source.isPrimitive() && target.isReferenceType()) {
                return primitiveWrapper(source.asPrimitive(), target.asReferenceType());
            }
            if (target.isPrimitive() && source.isReferenceType()) {
                return primitiveWrapper(target.asPrimitive(), source.asReferenceType());
            }
        } catch (RuntimeException | LinkageError ignored) {
            complete = false;
            return false;
        }
        return false;
    }

    private boolean primitiveWrapper(ResolvedPrimitiveType primitive, ResolvedReferenceType wrapper) {
        return switch (primitive.describe()) {
            case "boolean" -> wrapper.getQualifiedName().equals("java.lang.Boolean");
            case "byte" -> wrapper.getQualifiedName().equals("java.lang.Byte");
            case "short" -> wrapper.getQualifiedName().equals("java.lang.Short");
            case "int" -> wrapper.getQualifiedName().equals("java.lang.Integer");
            case "long" -> wrapper.getQualifiedName().equals("java.lang.Long");
            case "char" -> wrapper.getQualifiedName().equals("java.lang.Character");
            case "float" -> wrapper.getQualifiedName().equals("java.lang.Float");
            case "double" -> wrapper.getQualifiedName().equals("java.lang.Double");
            default -> false;
        };
    }

    private boolean isRaw(ResolvedType type) {
        try {
            return type.isReferenceType() && type.asReferenceType().isRawType()
                    && type.asReferenceType().getTypeDeclaration().map(value -> !value.getTypeParameters().isEmpty()).orElse(false);
        } catch (RuntimeException | LinkageError exception) {
            complete = false;
            return false;
        }
    }

    private Decision unknown() {
        return new Decision(FindingStatus.REVIEW, false, "UNKNOWN",
                "类型信息不完整或依赖类缺失，需要人工复核");
    }

    record Decision(FindingStatus status, boolean oldAssignable, String newDecision, String reason) {
    }
}
