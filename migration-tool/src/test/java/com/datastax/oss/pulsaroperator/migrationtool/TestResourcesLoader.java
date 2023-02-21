package com.datastax.oss.pulsaroperator.migrationtool;

import com.datastax.oss.pulsaroperator.mocks.MockResourcesResolver;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.SneakyThrows;

public class TestResourcesLoader {
    private TestResourcesLoader() {
    }


    @SneakyThrows
    public static void importPathFromClasspath(String resourcePath, MockResourcesResolver resourcesResolver) {
        final File file = new File(TestResourcesLoader.class.getResource(resourcePath).toURI());
        importPathFromFile(file, resourcesResolver);
    }

    @SneakyThrows
    public static void importPathFromFile(File file, MockResourcesResolver resourcesResolver) {
        if (file.isDirectory()) {
            Files.list(file.toPath()).forEach(path -> importPathFromFile(path.toFile(), resourcesResolver));
        } else {
            resourcesResolver.importResource(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        }
    }
}
