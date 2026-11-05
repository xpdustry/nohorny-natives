import com.xpdustry.nohorny.natives.NoHornyNativeLookup
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

buildscript {
    dependencies {
        classpath("com.xpdustry:build-logic")
    }
}

plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

group = "com.xpdustry"
version =
    libs.versions.nohorny.natives.get() + if (findProperty("is_release").toString().toBoolean()) "" else "-SNAPSHOT"
description = "The image classifier of NoHorny, running ViT models with OpenCV."

repositories {
    mavenCentral()
}

dependencies {
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)
    compileOnlyApi(libs.jspecify)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

val repository = "xpdustry/nohorny-natives"

publishing {
    publications.register<MavenPublication>("maven") {
        from(components["java"])
        pom {
            name = project.name
            description = provider { project.description }
            url = "https://github.com/$repository"

            licenses {
                license {
                    name = "The MIT License"
                    url = "https://opensource.org/licenses/MIT"
                }
            }

            organization {
                name = "xpdustry"
                url = "https://xpdustry.com"
            }

            developers {
                developer {
                    id = "Phinner"
                    timezone = "Europe/Brussels"
                }

                developer {
                    id = "ZetaMap"
                    timezone = "Europe/Paris"
                }
            }

            scm {
                connection = "scm:git:https://github.com/$repository.git"
                developerConnection = "scm:git:ssh://git@github.com/$repository.git"
                url = "https://github.com/$repository"
            }

            issueManagement {
                system = "GitHub"
                url = "https://github.com/$repository/issues"
            }

            ciManagement {
                system = "GitHub Actions"
                url = "https://github.com/$repository/actions"
            }
        }
    }

    repositories {
        maven {
            name = "xpdustry"
            val release = !version.toString().endsWith("-SNAPSHOT")
            url = uri("https://maven.xpdustry.com/${if (release) "releases" else "snapshots"}")
            credentials(PasswordCredentials::class)
        }
    }
}

signing {
    isRequired = providers.environmentVariable("CI").isPresent
    useInMemoryPgpKeys(
        providers.gradleProperty("signingKey").orNull,
        providers.gradleProperty("signingPassword").orNull,
    )
    sign(publishing.publications)
}

spotless {
    java {
        target("src/**/*.java", ".github/scripts/*.java")
        palantirJavaFormat()
        formatAnnotations()
        importOrder("", "\\#")
        forbidModuleImports()
        forbidWildcardImports()
        licenseHeader("// SPDX-License-Identifier: MIT")
    }
    // The default ktlint_official style is far more invasive than the IntelliJ one
    kotlinGradle {
        target("*.gradle.kts", "build-logic/*.gradle.kts")
        ktlint().editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea", "max_line_length" to "120"))
    }
}

tasks.withType<JavaCompile> {
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
        disable("MissingSummary", "InlineMeSuggester")
        option("NullAway:OnlyNullMarked")
        check("NullAway", CheckSeverity.ERROR)
    }
}

val cmakeDirectory = layout.buildDirectory.dir("cmake")
val nativesDirectory = layout.buildDirectory.dir("generated/natives")
val hostNativesDirectory = nativesDirectory.map { it.dir(NoHornyNativeLookup.directory(NoHornyNativeLookup.host())) }

// cmake --build reconfigures on its own when the CMake files change, so this only runs once
val cmakeConfigure = tasks.register<Exec>("cmakeConfigure") {
    description = "Configure the CMake build of the native library."
    // CMake reads backslashes as escapes
    val output = hostNativesDirectory.get().asFile.absolutePath.replace('\\', '/')
    inputs.property("output", output)
    outputs.file(cmakeDirectory.map { it.file("CMakeCache.txt") })
    commandLine(
        "cmake",
        "-S",
        layout.projectDirectory.asFile.absolutePath,
        "-B",
        cmakeDirectory.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DNOHORNY_OUTPUT_DIRECTORY=$output",
    )
}

val cmakeBuild = tasks.register<Exec>("cmakeBuild") {
    description = "Compile the native library of the host platform with CMake."
    dependsOn(cmakeConfigure)
    inputs.files("CMakeLists.txt", "cmake", "src/main/cpp").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(nativesDirectory)
    // Without a job count, the Makefile generator spawns as many jobs as there are sources
    val jobs = Runtime.getRuntime().availableProcessors()
    commandLine("cmake", "--build", cmakeDirectory.get().asFile.absolutePath, "--config", "Release", "--parallel", jobs)
}

val prebuilt = providers.gradleProperty("prebuilt_natives").map { layout.projectDirectory.dir(it) }
sourceSets.main {
    resources.srcDir(if (prebuilt.isPresent) prebuilt else cmakeBuild.map { it.outputs.files.singleFile })
}

val verifyNatives = tasks.register("verifyNatives") {
    description = "Check that the natives of every supported platform are bundled."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    val resources = files(tasks.processResources)
    val expected = NoHornyNativeLookup.PLATFORMS.map(NoHornyNativeLookup::resource)
    inputs.files(resources)
    doLast {
        val missing = expected.filterNot { resources.singleFile.resolve(it).isFile }
        if (missing.isNotEmpty()) {
            throw GradleException("Missing natives, pass every platform with -Pprebuilt_natives=<dir>: $missing")
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(verifyNatives)
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:-missing", true)
}

tasks.jar {
    // A stable name for the release assets
    archiveFileName = "${project.name}.jar"
}
