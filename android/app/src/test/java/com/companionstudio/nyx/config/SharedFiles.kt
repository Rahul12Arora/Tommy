package com.companionstudio.nyx.config

import java.io.File

/** The repo's `shared/` folder; Gradle passes its path to unit tests (android/app/build.gradle.kts). */
object SharedFiles {
    val dir: File = File(checkNotNull(System.getProperty("nyx.sharedDir")) { "nyx.sharedDir isn't set; run tests through Gradle" })

    val defaultConfig: String get() = dir.resolve("default-config.json").readText()

    /** The `.json` files in `fixtures/valid` or `fixtures/invalid` (the folders also hold a `.gitkeep`). */
    fun fixtures(kind: String): List<File> =
        dir.resolve("fixtures/$kind").listFiles { file -> file.extension == "json" }.orEmpty().sortedBy { it.name }

    fun fixture(kind: String, name: String): String = dir.resolve("fixtures/$kind/$name.json").readText()

    /** `valid/minimal.json`, parsed: the starting point for tests that change one thing. */
    val minimalConfig: NyxConfig
        get() = (parseConfig(fixture("valid", "minimal")) as ConfigResult.Valid).config
}
