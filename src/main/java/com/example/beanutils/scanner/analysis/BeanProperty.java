package com.example.beanutils.scanner.analysis;

import com.example.beanutils.scanner.model.TypeRef;
import com.github.javaparser.resolution.types.ResolvedType;

record BeanProperty(
        String name,
        ResolvedType readType,
        ResolvedType writeType,
        TypeRef readTypeRef,
        TypeRef writeTypeRef,
        String getterOwner,
        String setterOwner) {
}
