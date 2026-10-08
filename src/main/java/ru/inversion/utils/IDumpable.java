package ru.inversion.utils;

import java.util.LinkedHashMap;
import java.util.Map;

@FunctionalInterface
public interface IDumpable {

    void dump( Map<String,Object> properties );

    default Map<String,Object> dump( ) {
        Map<String,Object> properties = new LinkedHashMap<>();
        dump(properties);
        return properties;
    }
}
