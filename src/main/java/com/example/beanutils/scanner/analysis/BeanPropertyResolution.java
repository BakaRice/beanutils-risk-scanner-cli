package com.example.beanutils.scanner.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

record BeanPropertyResolution(Map<String, BeanProperty> properties, boolean complete) {
    BeanPropertyResolution {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    static BeanPropertyResolution incomplete() {
        return new BeanPropertyResolution(Map.of(), false);
    }
}
