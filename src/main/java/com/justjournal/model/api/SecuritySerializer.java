package com.justjournal.model.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.justjournal.model.Security;

import java.io.IOException;

public class SecuritySerializer extends StdSerializer<Security> {

    public SecuritySerializer() {
        this(null);
    }

    public SecuritySerializer(Class<Security> t) {
        super(t);
    }

    @Override
    public void serialize(Security security, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeNumberField("id", security.getId());
        jsonGenerator.writeStringField("name", security.getName());
        jsonGenerator.writeEndObject();
    }
}
