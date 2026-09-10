package com.quantlab.common.config;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public class LocalDateToInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateStr = p.getText();

        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        try {
            LocalDate localDate = LocalDate.parse(dateStr); // parses yyyy-MM-dd
            return localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            throw new JsonParseException(p, "Invalid date format: " + dateStr + ". Expected format: yyyy-MM-dd");
        }
    }
}
