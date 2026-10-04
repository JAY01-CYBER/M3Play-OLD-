# M3 Play

Android music player with Jetpack Compose screens and Material 3 Expressive UI.
Login screens embed Android WebViews in Compose; the home-screen widget uses
Android RemoteViews.

## Build

Install JDK 21 and Android SDK platform 37.2 (Build Tools 37.0.0). Set
`ANDROID_HOME` or add `sdk.dir=/path/to/android-sdk` to your local, ignored
`local.properties` file.

```sh
./gradlew assembleDebug
./gradlew test lintDebug
```

The debug APK is written under `app/build/outputs/apk/debug/`. Release builds
require the signing environment variables declared in `app/build.gradle.kts`.

## Dependency baseline

Versions were checked against Google Maven, Maven Central, JitPack, and the
Gradle release service on 2026-10-04. All shared versions and dependencies live
in `gradle/libs.versions.toml`.

- Gradle 9.8.0, Android Gradle Plugin 9.4.1, Kotlin/Compose compiler 2.4.20.
- AGP built-in Kotlin for Android; KSP 2.3.12 for Room and Hilt.
- Compose BOM 2026.09.00 aligns Compose runtime, UI, foundation, and animation.
- Material 3 1.5.0-alpha29 (and its required Compose 1.13 alpha dependencies)
  is an explicit preview exception: existing screens
  use Expressive APIs unavailable in stable Material 3 1.4.0.
- Coil 3.6.3 includes the OkHttp network module, updated singleton loader,
  and Android image conversions.
- Android compile SDK 37.2 and target SDK 37; minimum supported SDK remains 24.

Stable releases are preferred. Libraries without newer stable releases keep
their existing versions; the extractor uses its published `v1.0.0` release.
The Compose BOM does not manage the Compose compiler, which follows Kotlin.

Reference: [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin),
[Coil 3 migration](https://coil-kt.github.io/coil/upgrading_to_coil3/).
