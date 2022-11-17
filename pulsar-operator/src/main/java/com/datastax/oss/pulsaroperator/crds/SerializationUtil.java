package com.datastax.oss.pulsaroperator.crds;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

public class SerializationUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

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
    public static byte[] writeAsJsonBytes(Object object) {
        return mapper.writeValueAsBytes(object);
    }
}
