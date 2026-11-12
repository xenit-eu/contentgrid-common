package com.contentgrid.common.spring.autoconfigure.logging;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class loaders that hide logging configuration or Logback itself from code under test.
 */
final class TestClassLoaders {

    private static final Set<String> STANDARD_LOGBACK_CONFIGURATIONS = Set.of(
            "logback-test.xml", "logback.xml", "logback-spring.xml");

    private TestClassLoaders() {
    }

    static ClassLoader withoutStandardLogbackConfiguration() {
        return new FilteringClassLoader(TestClassLoaders.class.getClassLoader(), STANDARD_LOGBACK_CONFIGURATIONS,
                List.of(), Map.of());
    }

    static ClassLoader withStandardLogbackConfiguration(String resourceName, URL resource) {
        return new FilteringClassLoader(TestClassLoaders.class.getClassLoader(), STANDARD_LOGBACK_CONFIGURATIONS,
                List.of(), Map.of(resourceName, resource));
    }

    static ClassLoader withoutLogback() {
        return new FilteringClassLoader(TestClassLoaders.class.getClassLoader(), STANDARD_LOGBACK_CONFIGURATIONS,
                List.of("ch.qos.logback."), Map.of());
    }

    static ClassLoader withoutLoggingCore() {
        return new FilteringClassLoader(TestClassLoaders.class.getClassLoader(), STANDARD_LOGBACK_CONFIGURATIONS,
                List.of("com.contentgrid.common.logging."), Map.of());
    }

    private static final class FilteringClassLoader extends ClassLoader {

        private final Set<String> hiddenResources;

        private final List<String> hiddenClassPrefixes;

        private final Map<String, URL> exposedResources;

        private FilteringClassLoader(ClassLoader parent, Set<String> hiddenResources,
                List<String> hiddenClassPrefixes, Map<String, URL> exposedResources) {
            super(parent);
            this.hiddenResources = hiddenResources;
            this.hiddenClassPrefixes = hiddenClassPrefixes;
            this.exposedResources = exposedResources;
        }

        @Override
        public URL getResource(String name) {
            URL exposed = this.exposedResources.get(name);
            if (exposed != null) {
                return exposed;
            }
            return this.hiddenResources.contains(name) ? null : super.getResource(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (this.hiddenClassPrefixes.stream().anyMatch(name::startsWith)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
