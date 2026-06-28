package com.biocompass.pkb.persistence.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.StringJoiner;

@Converter
public class PkbVectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }

        var joiner = new StringJoiner(",", "[", "]");
        for (var value : attribute) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        var normalized = dbData.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return new float[0];
        }

        var parts = normalized.split(",");
        var vector = new float[parts.length];
        for (var index = 0; index < parts.length; index++) {
            vector[index] = Float.parseFloat(parts[index].trim());
        }
        return vector;
    }

}
