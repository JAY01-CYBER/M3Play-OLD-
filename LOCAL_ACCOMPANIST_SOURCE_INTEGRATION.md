# Accompanist Lyrics: local source integration

The production source from the supplied `accompanist-lyrics-core` and
`accompanist-lyrics-ui` archives is included directly in this Android app.

## Source layout
- `app/src/main/java/com/mocharealm/accompanist/lyrics/core/`: core models,
  parsers, timing utilities and exporters (29 Kotlin production source files).
- `app/src/main/java/com/mocharealm/accompanist/lyrics/ui/`: UI composables,
  karaoke rendering, text preparation, playback timeline and scrolling
  (76 Kotlin production source files).
- Android-specific implementations from the UI repository are inlined into the
  corresponding source files because this app compiles a single Android source
  set rather than a Kotlin Multiplatform module.
- Upstream license and README copies are retained alongside each package.

## Dependencies
The Maven dependencies `com.mocharealm.accompanist:lyrics-core` and
`com.mocharealm.accompanist:lyrics-ui`, plus their version-catalog aliases, have
been removed. The optional `gaze:capsule` shape usage was replaced with Compose's
`RoundedCornerShape`.

The app still uses its existing AndroidX Compose, coroutines and
kotlinx-serialization dependencies; those are required by the source and the
rest of M3Play. This change removes the two Accompanist Maven artifacts, not all
third-party dependencies in the app.

## Validation
The Gradle build was attempted with `bash gradlew :app:assembleDebug`, but the
environment could not resolve `services.gradle.org` to download the Gradle
distribution. Therefore a successful build cannot be claimed from this
environment. Please run the build locally in Android Studio or with the Gradle
wrapper in a network-enabled environment.

Word-by-word highlighting depends on word/syllable timestamps being present in
the fetched or parsed lyrics. Standard line-timed LRC alone cannot guarantee
accurate word-level highlighting.


## Timestamp and word-sync update (ArchiveTune/QRC source)

- Added `QrcLyricsParser.kt`, adapted from the uploaded ArchiveTune lyrics source with its GPL-3.0 attribution retained.
- `LyricsUtils.parseLyrics` now preserves per-word start/end timestamps for Enhanced LRC (`<mm:ss.xx>`), NetEase YRC (`(startMs,durationMs,flags)word`), and QRC (`word(startMs,durationMs,...)`) inputs.
- Repeated LRC line timestamps and `[offset:...]` are applied to both line and word timestamps.
- `AccompanistLyrics.parse` checks QRC before the Accompanist Core parser because Core does not include a QRC parser.
- Existing TTML parsing still preserves timed spans. When a source only has line-level LRC timestamps, the existing estimated-timing path can animate words, but these are estimates rather than provider-supplied exact timings.

Build/test note: `bash gradlew :app:testUniversalDebugUnitTest` was attempted. It failed before compilation because this environment could not resolve `services.gradle.org` to download Gradle 9.8. Run the same test task in a network-enabled Android Studio/Gradle environment.
