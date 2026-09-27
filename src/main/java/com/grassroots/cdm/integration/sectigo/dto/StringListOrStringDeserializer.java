package com.grassroots.cdm.integration.sectigo.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Jackson deserializer that seamlessly accepts SANs as either a JSON array (["a.com", "b.com"])
 * or a delimited String ("a.com, b.com").
 */
public class StringListOrStringDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.START_ARRAY) {
            List<String> list = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    list.add(text.trim());
                }
            }
            return list;
        } else if (token == JsonToken.VALUE_STRING) {
            String val = p.getText();
            if (val == null || val.isBlank()) {
                return Collections.emptyList();
            }
            return Arrays.stream(val.split("[,;\\s]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return Collections.emptyList();
    }
}
