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
package com.datastax.oss.pulsaroperator.migrationtool;

import com.datastax.oss.pulsaroperator.mocks.MockResourcesResolver;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Predicate;
import lombok.SneakyThrows;

public class TestResourcesLoader {
    private TestResourcesLoader() {
    }

    @SneakyThrows
    public static void importPathFromClasspath(String resourcePath, MockResourcesResolver resourcesResolver) {
        importPathFromClasspath(resourcePath, resourcesResolver, (s) -> true);
    }

    @SneakyThrows
    public static void importPathFromClasspath(String resourcePath, MockResourcesResolver resourcesResolver,
                                               Predicate<String> filterOutFile) {
        final File file = new File(TestResourcesLoader.class.getResource(resourcePath).toURI());
        importPathFromFile(file, resourcesResolver, filterOutFile);
    }

    @SneakyThrows
    public static void importPathFromFile(File file, MockResourcesResolver resourcesResolver,
                                          Predicate<String> filterOutFile) {
        if (file.isDirectory()) {
            Files.list(file.toPath()).forEach(path -> importPathFromFile(path.toFile(), resourcesResolver, filterOutFile));
        } else {
            if (filterOutFile.test(file.getName())) {
                resourcesResolver.importResource(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            }
        }
    }
}
