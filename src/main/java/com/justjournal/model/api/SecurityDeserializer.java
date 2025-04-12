package com.justjournal.model.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.justjournal.model.Security;

import java.io.IOException;

public class SecurityDeserializer extends StdDeserializer<Security> {

    public SecurityDeserializer() {
        this(null);
    }

    public SecurityDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Security deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        int value;
        JsonNode node = jp.getCodec().readTree(jp);
        if (node.isInt()) {
            value = node.asInt();
        } else if (node.isTextual()) {
            try {
            value = Integer.parseInt(node.asText());
            } catch (NumberFormatException e) {
                return Security.PRIVATE;
            }
        } else {
            return Security.PRIVATE;
        }
        return Security.fromValue(value);
    }
}
