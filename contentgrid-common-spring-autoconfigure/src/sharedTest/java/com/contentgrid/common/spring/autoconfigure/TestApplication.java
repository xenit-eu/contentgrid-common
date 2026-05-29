package com.contentgrid.common.spring.autoconfigure;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.FilteredClassLoader;

@SpringBootApplication
public class TestApplication {

    static FilteredClassLoader filteringJar(String artifactId) {
        String segment = "/" + artifactId + "/";
        File jarFile = Arrays.stream(System.getProperty("java.class.path", "").split(File.pathSeparator))
                .map(File::new)
                .filter(f -> f.getPath().replace('\\', '/').contains(segment) && f.getName().endsWith(".jar"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No jar found for artifact: " + artifactId));
        Set<String> classNames = new HashSet<>();
        try (var jar = new JarFile(jarFile)) {
            jar.stream()
                    .filter(e -> e.getName().endsWith(".class"))
                    .map(e -> e.getName().replace('/', '.').substring(0, e.getName().length() - ".class".length()))
                    .forEach(classNames::add);
        } catch (Exception e) {
            throw new AssertionError("Failed to read jar for artifact: " + artifactId, e);
        }
        return new FilteredClassLoader(classNames::contains);
    }

    static boolean isSpringBoot3() {
        return SpringBootVersion.getVersion().startsWith("3.");
    }

    static boolean isSpringBoot4() {
        return SpringBootVersion.getVersion().startsWith("4.");
    }
}
