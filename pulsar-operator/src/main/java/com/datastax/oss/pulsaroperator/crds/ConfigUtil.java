package com.datastax.oss.pulsaroperator.crds;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

public class ConfigUtil {
    private ConfigUtil() {}

    @SneakyThrows
    public static <T> void applyDefaultsWithReflection(T object, Supplier<T> defaultObject) {

        final Class<?> declaringClass = object.getClass();
        final List<Field> allFields = FieldUtils.getAllFieldsList(declaringClass);
        for (Field field : allFields) {
            if (field.getDeclaringClass().isPrimitive() || field.getDeclaringClass().isArray()) {
                final Object newValue = ObjectUtils.getFirstNonNull(
                        () -> readField(object, field.getName()),
                        () -> readField(defaultObject.get(), field.getName())
                );
                FieldUtils.writeField(field, object, newValue);
            }
        }
    }

    @SneakyThrows
    private static Object readField(Object object, String fieldName) {
        return FieldUtils.readField(object, fieldName, true);
    }



}
