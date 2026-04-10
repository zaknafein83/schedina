package it.schedina.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA converters for storing List<Integer> and List<String> as JSON text in the DB.
 */
public final class JsonListConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonListConverters() {}

    @Converter
    public static class IntList implements AttributeConverter<List<Integer>, String> {
        @Override
        public String convertToDatabaseColumn(List<Integer> list) {
            if (list == null) return "[]";
            try { return MAPPER.writeValueAsString(list); } catch (Exception e) { return "[]"; }
        }

        @Override
        public List<Integer> convertToEntityAttribute(String json) {
            if (json == null || json.isBlank()) return new ArrayList<>();
            try { return MAPPER.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return new ArrayList<>(); }
        }
    }

    @Converter
    public static class StringList implements AttributeConverter<List<String>, String> {
        @Override
        public String convertToDatabaseColumn(List<String> list) {
            if (list == null) return "[]";
            try { return MAPPER.writeValueAsString(list); } catch (Exception e) { return "[]"; }
        }

        @Override
        public List<String> convertToEntityAttribute(String json) {
            if (json == null || json.isBlank()) return new ArrayList<>();
            try { return MAPPER.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return new ArrayList<>(); }
        }
    }
}
