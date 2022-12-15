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
    public static <T> T applyDefaultsWithReflection(T object, Supplier<T> defaultObject) {
        final Class<?> declaringClass;
        if (object == null) {
            final T defObject = defaultObject.get();
            if (defObject == null) {
                return null;
            }
            object = (T) defObject.getClass().getConstructor().newInstance();
            declaringClass = defObject.getClass();
        } else {
            declaringClass = object.getClass();
        }
        final List<Field> allFields = FieldUtils.getAllFieldsList(declaringClass);
        for (Field field : allFields) {
            final Object newValue;
            if (field.getType().getName().startsWith("java.")) {
                final Object parent = object;
                newValue = ObjectUtils.getFirstNonNull(
                        () -> readField(parent, field.getName()),
                        () -> readField(defaultObject.get(), field.getName())
                );
            } else {
                newValue = applyDefaultsWithReflection(
                        readField(object, field.getName()),
                        () -> readField(defaultObject.get(), field.getName())
                );
            }
            FieldUtils.writeField(field, object, newValue, true);
        }
        return object;
    }

    @SneakyThrows
    private static Object readField(Object object, String fieldName) {
        return FieldUtils.readField(object, fieldName, true);
    }
}
