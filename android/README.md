# SpaceMuse AI — Android Client

## Status

This is a Kotlin/Compose project skeleton. **It has not been Gradle-built**
in the environment it was scaffolded in (no JDK or Android SDK were
available there — see `docs/development/development-log.md`). Treat it as
reviewed-but-unverified source until you open it in Android Studio.

## Setup

1. Open this `android/` directory in Android Studio (Hedgehog 2023.1.1+).
2. Studio will prompt to generate the Gradle wrapper (`gradlew`/`gradlew.bat`
   + `gradle/wrapper/gradle-wrapper.jar`) on first sync — accept it. These
   wrapper binary/script files are intentionally not checked into this
   commit since they can't be produced or verified without a local Gradle
   install.
3. Requires JDK 17 and Android SDK Platform 34.
4. Sync Gradle, then Run on an emulator or device (minSdk 26).

## Module map

See `docs/architecture/mobile-architecture.md`.
