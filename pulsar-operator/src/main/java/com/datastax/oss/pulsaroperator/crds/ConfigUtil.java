/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.pulsaroperator.crds;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

public class ConfigUtil {
    private ConfigUtil() {
    }

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
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            final Object newValue;

            if (Collection.class.isAssignableFrom(field.getType())) {
                newValue = handleCollections(
                        (Collection) readField(defaultObject.get(), field.getName()),
                        (Collection) readField(object, field.getName()));
            } else if (Map.class.isAssignableFrom(field.getType())) {
                newValue = mergeMaps(
                        (Map) readField(defaultObject.get(), field.getName()),
                        (Map) readField(object, field.getName()));
            } else if (field.getType().getName().startsWith("java.")) {
                final Object finalObj = object;
                newValue = ObjectUtils.getFirstNonNull(
                        () -> readField(finalObj, field.getName()),
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
        if (object == null) {
            return null;
        }

        final Object res = FieldUtils.readField(object, fieldName, true);
        System.out.println("read field " + fieldName + " -> " + res);
        return res;
    }

    public static <T> Map<String, T> mergeMaps(Map<String, T> parent, Map<String, T> child) {
        if (parent == null) {
            return child;
        }
        if (child == null) {
            return parent;
        }
        Map<String, T> result = new HashMap<>(parent);
        result.putAll(child);
        return result;
    }

    @SneakyThrows
    private static <T> Collection<T> handleCollections(Collection<T> parent,
                                                       Collection<T> child) {
        if (parent == null) {
            return child;
        }
        if (child == null) {
            return parent;
        }
        if (parent.isEmpty()) {
            return child;
        }
        if (child.isEmpty()) {
            return parent;
        }
        return child;
    }

}
