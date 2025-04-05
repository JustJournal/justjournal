package com.justjournal.model.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
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
    public Security deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = jp.getCodec().readTree(jp);
        int value = node.get("id").asInt();
        return Security.fromValue(value);
    }
}
