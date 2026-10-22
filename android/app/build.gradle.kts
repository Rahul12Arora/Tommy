plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// The repo's shared/ folder: the config contract used by the app, the backend and the console
val sharedDir: File = rootProject.file("../shared")

android {
    namespace = "com.companionstudio.nyx"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.companionstudio.nyx"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // The local backend through `adb reverse tcp:3000 tcp:3000`, on the emulator and a USB phone (D-142)
            buildConfigField("String", "CONFIG_BASE_URL", "\"http://127.0.0.1:3000\"")
            // Coverage report: ./gradlew createDebugUnitTestCoverageReport (targets in docs/ROADMAP.md)
            enableUnitTestCoverage = true
        }
        release {
            // Blank until M15 sets the production HTTPS address; blank = no config checks (D-146)
            buildConfigField("String", "CONFIG_BASE_URL", "\"\"")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG picks the debug-only Room fallback (docs/ARCHITECTURE.md §14.1)
        buildConfig = true
    }
    lint {
        warningsAsErrors = true
        // Versions are pinned in docs/TECH_STACK.md and only change through approval, so these
        // "newer version available" checks would just fail CI whenever a new release ships
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable", "OldTargetApi")
    }
    testOptions {
        unitTests.all { test ->
            // Config tests read the fixtures and defaults straight from shared/ (docs/ARCHITECTURE.md §14.2).
            // Declared as an input so a changed fixture re-runs the tests instead of reusing the last result
            test.systemProperty("nyx.sharedDir", sharedDir.path)
            test.inputs.dir(sharedDir).withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

// Room writes its schema here; the files are committed so every schema change shows up in review (§14.1)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The built-in config is shared/default-config.json, copied into the app's assets at build time (D-43).
// Only that one file is copied, so the schema and fixtures stay out of the APK
abstract class CopyDefaultConfig : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val defaultConfig: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile
        target.deleteRecursively()
        defaultConfig.get().asFile.copyTo(target.resolve("default-config.json"))
    }
}

androidComponents {
    onVariants { variant ->
        val copyTask = tasks.register<CopyDefaultConfig>("copy${variant.name.replaceFirstChar(Char::uppercase)}DefaultConfig") {
            defaultConfig.set(sharedDir.resolve("default-config.json"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyTask, CopyDefaultConfig::outputDir)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
