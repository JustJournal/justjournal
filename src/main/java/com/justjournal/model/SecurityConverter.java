package com.justjournal.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SecurityConverter implements AttributeConverter<Security, Integer> {
    @Override
    public Integer convertToDatabaseColumn(Security security) {
        return security == null ? null : security.getId();
    }

    @Override
    public Security convertToEntityAttribute(Integer value) {
        return value == null ? null : Security.fromValue(value);
    }
}
