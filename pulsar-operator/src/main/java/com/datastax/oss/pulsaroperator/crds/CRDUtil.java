package com.datastax.oss.pulsaroperator.crds;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

public class CRDUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    private CRDUtil() {
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
}
