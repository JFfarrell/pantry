package ie.pantry.testutil

import java.io.File

/** Locates the repository root from the current working directory, whatever it is under Gradle. */
object RepoPaths {

    /** Walks up from the working directory to the first ancestor holding `settings.gradle.kts`. */
    fun repoRoot(): File {
        var dir = File("").absoluteFile
        while (true) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: error("no ancestor of ${File("").absoluteFile} holds settings.gradle.kts")
        }
    }
}
