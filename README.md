# Pantry

An Android app (Kotlin, Jetpack Compose, Room). This repository currently holds the F1 skeleton:
build configuration, an app shell and the Room foundation.

## Prerequisites

- **JDK 17** installed, with `JAVA_HOME` pointing at it or `java` on your `PATH`. The build pins
  `jvmToolchain(17)` and does not download a JDK for you.
- **Android SDK** with platform 35 and build-tools 35, located through `ANDROID_HOME` or a
  git-ignored `local.properties` containing `sdk.dir=/path/to/sdk`.

The Gradle wrapper pins Gradle itself, so no separate Gradle install is needed.

## Commands

```
./gradlew assembleDebug assembleRelease   # build both variants
./gradlew testDebugUnitTest               # run the JVM unit tests (Robolectric)
./gradlew lintDebug                       # Android lint
./gradlew installDebug                    # install the debug build on an attached device or emulator
```

Schema changes follow `docs/migrations.md`.
