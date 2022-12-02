package com.datastax.oss.pulsaroperator.crds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.SneakyThrows;

public class SerializationUtil {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static ObjectMapper yamlMapper = new ObjectMapper(YAMLFactory.builder()
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .build()
    )
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private SerializationUtil() {
    }

    @SneakyThrows
    public static <T> T deepCloneObject(T object) {
        if (object == null) {
            return null;
        }
        return (T)
                mapper.readValue(
                        mapper.writeValueAsString(object), object.getClass());
    }

    @SneakyThrows
    public static String writeAsJson(Object object) {
        return mapper.writeValueAsString(object);
    }

    @SneakyThrows
    public static <T> T readJson(String string, Class<T> objectClass) {
        return mapper.readValue(string, objectClass);
    }

    @SneakyThrows
    public static byte[] writeAsJsonBytes(Object object) {
        return mapper.writeValueAsBytes(object);
    }

    @SneakyThrows
    public static String writeAsYaml(Object object) {
        return yamlMapper.writeValueAsString(object);
    }

    @SneakyThrows
    public static <T> T readYaml(String yaml, Class<T> toClass) {
        return yamlMapper.readValue(yaml, toClass);
    }


}
