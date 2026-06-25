fun String.capitalize() = replaceFirstChar { it.uppercaseChar() }
fun String.toKebabCase() = replace(Regex("([A-Z])"), "-$1").replace(Regex("([a-zA-Z])(\\d)"), "$1-$2").lowercase()

val features = listOf("springBoot3", "springBoot4")
val sourceSets = the<SourceSetContainer>()

features.forEach { name ->
    val mainSourceSet = sourceSets.create(name) {
        java {
            srcDir("src/shared/java")
        }
        resources {
            srcDir("src/shared/resources")
        }
    }

    configure<JavaPluginExtension> {
        registerFeature(name) {
            usingSourceSet(mainSourceSet)
            capability("com.contentgrid.common", project.name + "-spring-boot-support", project.version.toString())
            capability(
                "com.contentgrid.common",
                project.name + "-" + name.toKebabCase(),
                project.version.toString()
            )
            withJavadocJar()
            withSourcesJar()
        }
    }

    tasks.named("assemble") {
        dependsOn(tasks.named(mainSourceSet.jarTaskName))
    }

    val testSourceSet = sourceSets.create("${name}Test") {
        java {
            srcDir("src/sharedTest/java")
        }
        resources {
            srcDir("src/sharedTest/resources")
        }
        compileClasspath += mainSourceSet.output
        runtimeClasspath += mainSourceSet.output
    }

    configurations[testSourceSet.implementationConfigurationName].extendsFrom(configurations[mainSourceSet.implementationConfigurationName])
    configurations[testSourceSet.compileOnlyConfigurationName].extendsFrom(configurations[mainSourceSet.compileOnlyConfigurationName])
    configurations[testSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations[mainSourceSet.runtimeOnlyConfigurationName])

    tasks.register<Test>("${name}Test") {
        description = "Runs tests for $name"
        group = "verification"
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        useJUnitPlatform()
    }

    tasks.named("check") {
        dependsOn("${name}Test")
    }

    // A build-only platform configuration: wired into resolve-time classpaths but not
    // into the published runtimeElements/apiElements, so it doesn't appear in metadata.
    val platformConfig = configurations.create("${name}Platform") {
        isCanBeConsumed = false
        isCanBeResolved = false
        isCanBeDeclared = true
    }
    listOf(
        mainSourceSet.compileClasspathConfigurationName,
        mainSourceSet.runtimeClasspathConfigurationName,
        testSourceSet.compileClasspathConfigurationName,
        testSourceSet.runtimeClasspathConfigurationName,
    ).forEach { configName ->
        configurations.named(configName).configure { extendsFrom(platformConfig) }
    }
}

fun createConfiguration(configName: String) {
    val commonConfig = configurations.create("common${configName.capitalize()}") {
        isCanBeConsumed = false
        isCanBeResolved = false
        isCanBeDeclared = true
    }
    features.forEach { feature ->
        configurations.named("${feature}${configName.capitalize()}").configure {
            isCanBeResolved = false
            isCanBeResolved = false
            extendsFrom(commonConfig)
        }
    }
}

createConfiguration("api")

listOf("implementation", "compileOnly", "runtimeOnly").forEach { configName ->
    createConfiguration(configName)
    createConfiguration("test${configName.capitalize()}")
}

tasks.named("jar") { enabled = false }
tasks.named("sourcesJar") { enabled = false }
tasks.named("javadocJar") { enabled = false }

val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["apiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["runtimeElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["sourcesElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["javadocElements"]) { skip() }

dependencies {
    "springBoot3Platform"(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    "springBoot4Platform"(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
}