import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "org.symera"
version = "3.0.2"

android {
    namespace = "org.symera.source"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            consumerProguardFiles("consumer-rules.pro")
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.jsoup)
    api(libs.coroutines)
    api(libs.kotlinx.serialization.json)
    api(libs.unifile)
    testImplementation(libs.junit)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "extensions-lib"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Symera Extensions SDK")
                description.set("Contracts and tools for independently developed Symera VOD and IPTV extensions")
                url.set("https://github.com/symeraorg/extensions-lib")
                licenses {
                    license {
                        name.set("Mozilla Public License 2.0")
                        url.set("https://www.mozilla.org/MPL/2.0/")
                    }
                }
                scm {
                    url.set("https://github.com/symeraorg/extensions-lib")
                    connection.set("scm:git:https://github.com/symeraorg/extensions-lib.git")
                }
            }
        }
    }
}

fun renderPublicApi(outputFile: File) {
    val aar = layout.buildDirectory.file("outputs/aar/library-release.aar").get().asFile
    require(aar.isFile) { "Release AAR was not assembled: $aar" }
    val temporaryDirectory = layout.buildDirectory.dir("tmp/publicApi").get().asFile.apply { mkdirs() }
    val classesJar = temporaryDirectory.resolve("classes.jar")
    ZipFile(aar).use { archive ->
        val entry = requireNotNull(archive.getEntry("classes.jar")) { "Release AAR has no classes.jar" }
        archive.getInputStream(entry).use { input -> classesJar.outputStream().use(input::copyTo) }
    }
    val classNames = ZipFile(classesJar).use { archive ->
        archive.entries().asSequence()
            .map { it.name }
            .filter { it.startsWith("org/symera/") && it.endsWith(".class") }
            .filterNot { it.endsWith("/R.class") || "/R\$" in it || it.endsWith("/BuildConfig.class") }
            .filterNot { it.contains("\$\$") || it.endsWith("\$WhenMappings.class") }
            .map { it.removeSuffix(".class").replace('/', '.') }
            .sorted()
            .toList()
    }
    val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
    val java21 = toolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.get()
    val executable = java21.metadata.installationPath.file(
        "bin/javap${if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""}",
    ).asFile
    val processBuilder = ProcessBuilder(
        listOf(executable.absolutePath, "-protected", "-s", "-constants", "-classpath", classesJar.absolutePath) + classNames,
    ).redirectErrorStream(true)
    processBuilder.environment().keys.removeAll { it in setOf("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS") }
    val process = processBuilder.start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    check(process.waitFor() == 0) { "javap failed:\n$output" }
    outputFile.parentFile.mkdirs()
    outputFile.writeText(output.replace("\r\n", "\n").trimEnd() + "\n")
}

val generatePublicApi = tasks.register("generatePublicApi") {
    group = "verification"
    description = "Updates the reviewed public JVM API snapshot."
    dependsOn("assembleRelease")
    doLast { renderPublicApi(rootProject.file("api/extensions-lib.api")) }
}

val checkPublicApi = tasks.register("checkPublicApi") {
    group = "verification"
    description = "Fails when the release JVM API differs from the reviewed snapshot."
    dependsOn("assembleRelease")
    doLast {
        val expected = rootProject.file("api/extensions-lib.api")
        require(expected.isFile) { "Missing API snapshot. Run :library:generatePublicApi and review it." }
        val actual = layout.buildDirectory.file("tmp/publicApi/current.api").get().asFile
        renderPublicApi(actual)
        val expectedLines = expected.readLines()
        val actualLines = actual.readLines()
        val mismatchIndex = (0 until maxOf(expectedLines.size, actualLines.size)).firstOrNull { index ->
            expectedLines.getOrNull(index) != actualLines.getOrNull(index)
        }
        check(mismatchIndex == null) {
            val lineNumber = requireNotNull(mismatchIndex) + 1
            "Public API changed at line $lineNumber. " +
                "Expected: ${expectedLines.getOrNull(mismatchIndex)}; " +
                "actual: ${actualLines.getOrNull(mismatchIndex)}. " +
                "Run :library:generatePublicApi and review the diff."
        }
    }
}

tasks.named("check") {
    dependsOn(checkPublicApi)
}
