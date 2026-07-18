package com.example.beanutils.scanner.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

record BeanPropertyResolution(Map<String, BeanProperty> properties, boolean complete, List<String> issues) {
    BeanPropertyResolution {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    static BeanPropertyResolution incomplete(String issue) {
        return new BeanPropertyResolution(Map.of(), false, List.of(issue));
    }
}
