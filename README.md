# M3 Play

Android music player with Jetpack Compose screens and Material 3 Expressive UI.
Login screens embed Android WebViews in Compose; the home-screen widget uses
Android RemoteViews.

## Lyrics and player effects

Open **Settings → Lyrics** to choose a separate lyrics screen or lyrics inside
the player, word highlighting, inactive line blur, auto scrolling, background
motion, and frosted glass. Tap the player artwork to open lyrics; use Back to
return. These switches are saved across app restarts.

Choose **Apple Music** under **Settings → Appearance → Player background style**
for animated artwork colors. Background motion stops when playback pauses or
the app leaves the foreground. Frosted glass adds blurred artwork on Android 12+
and translucent lyric panels; earlier Android versions retain the tinted panels.

Enhanced LRC `<mm:ss.xxx>` markers provide word timing. For ordinary synced LRC,
word timing is estimated between lines. Plain lyrics remain readable without
artificial timing. Lines sharing a timestamp highlight together for unison vocals
or translations. Precise word timing depends on the lyric source.

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
their existing versions; the extractor uses official NewPipe `v0.26.5`. The previous extractor fork
disabled global HTTPS verification during initialization and has been removed.
The Compose BOM does not manage the Compose compiler, which follows Kotlin.
The official extractor release is documented [here](https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.26.5).

Reference: [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin),
[Coil 3 migration](https://coil-kt.github.io/coil/upgrading_to_coil3/).

## Verification and maintenance

```sh
python3 scripts/check-kotlin-headers.py
./gradlew assembleDebug test lintDebug --warning-mode all
./gradlew :app:minifyReleaseWithR8 --warning-mode all
```

The regression tests exercise backup archive validation and the extractor's HTTP,
proxy authentication, response decoding, and TLS initialization behavior. Android
checks run on pull requests and branch pushes. `release.yml` is the single signed
release workflow; release signing still requires repository secrets.

Kotlin sources carry license notices. Existing upstream notices are preserved;
consult those notices and the root `LICENSE` for licensing information.

Four `UnusedResources` lint warnings are intentional: the `media3_icon_*` drawable
aliases in `values.xml` override icons referenced by Media3's `CommandButton`.
Keep these overrides when cleaning resources. No lint baseline was added.

The files under `linux/` are legacy packaging helpers for a separate Flutter
project. This Android repository does not contain that Linux application.

## Material 3 and performance architecture

Functional UI icons use Google's Material Symbols Rounded vectors, including
selected navigation states and media controls. App/provider logos are retained.
Shared playback progress, lifecycle-aware UI collection, serial queue persistence,
partial widget updates, and shared artwork caching reduce redundant work.
See [architecture and device validation](docs/ARCHITECTURE.md) and
[icon provenance](third-party/material-symbols/manifest.json).

Battery, CPU and GPU savings require device measurements; no percentage is claimed.

### Lyrics sources

Unison and Better Lyrics are available in Settings → Lyrics, with individual switches and a preferred provider. Existing LRCLIB and KuGou preferences remain supported. Automatic lookup prefers synced lyrics and falls back to other enabled sources when a provider fails or has no match. Refetch existing saved lyrics to change their source.

Lyrics from Unison (https://unison.boidu.dev). Better Lyrics uses its public cached API; uncached songs may require authorization unavailable to third-party apps and will fall back automatically. TTML timing is preserved for word animation and overlapping vocals.

### Accompanist lyrics engine

The lyrics screen uses [Accompanist Lyrics Core](https://github.com/6xingyv/accompanist-lyrics-core)
0.5.0 and [Accompanist Lyrics UI](https://github.com/6xingyv/accompanist-lyrics-ui)
2.0.0-rc.2 (the published UI release candidate). Core parses the rich vocal model;
UI renders syllable highlighting, duet alignment, accompaniment, captions, breathing dots
and chained scrolling. Provider time formats are normalized before parsing, with the
existing LRC/TTML reader retained for provider variants. No provider credentials are required
by these libraries. See [third-party notices](third-party/accompanist/NOTICE.md).

Word animation, inactive blur, automatic follow and the separate screen remain configurable.
Turning off lyrics motion uses a still, accessible list. Apple Music backgrounds retain
visible artwork with background motion both enabled and disabled.
