# Changes in this build

## Playback ("Error playing") and stability
- innertube `PlayerResponse`: all risky fields have defaults, so one missing field no longer kills a client.
- `NewPipe.getStreamUrl`: falls back to the plain URL if throttling-parameter deobfuscation fails.
- `YTPlayerUtils`: clients are tried in order and validated with a real range request; the first built stream is used if none validates.
- `MusicService` / `DownloadUtil`: no more `contentLength!!` / `split("codecs=")[1]`.
- `PlayerConnection`: queue windows without metadata are filtered (the Queue screen no longer hits `metadata!!`).
- `App` + `MainActivity`: crash logger with a "crashed last time" dialog; queue files are cleared after a startup crash.

## SeaGlass (replaces the liquid-glass experiment)
- Added the SeaGlass library as local modules `:seaglass-core` and `:seaglass-compose` (minSdk lowered 26 -> 24; a grep found the API 31/33 calls guarded, but this was not verified by compiling or running on API 24-25).
- `MainActivity` marks the NavHost as the SeaGlass backdrop (`seaGlassBackdrop`) and provides `LocalSeaGlassStageState`.
- Bottom navigation and the mini player are SeaGlass surfaces (`Modifier.seaGlass`, material derived from the current Material theme via `toSeaGlassMaterials()`).

- Shared helper `ui/component/SeaGlassStyle.kt` (`Modifier.seaGlassSurface`, `rememberSeaGlassMaterial`).
- SeaGlass surfaces on: top search pill, filter/assist chips, Home greeting card, release-notes card,
  settings category cards, floating action buttons, dialogs (`Dialog.kt`), bottom-sheet menus,
  and the 6 player-screen buttons.
- Dialogs, sheets and player buttons use `useStage = false` (lightweight tinted glass), because the app
  backdrop is not behind them there.
- Not changed: direct `Card(` / `Surface(` / `AlertDialog(` calls in individual screens, queue sheet, lyrics.

- Build fix: `MiniPlayer.kt` was missing its SeaGlass imports (CI log: unresolved `toSeaGlassMaterials`, `seaGlass`, `SeaGlassContentRole`).

## Startup-crash handling
- `CrashActivity` (separate `:crash` process, plain Views) shows the crash text with Copy / Restart buttons,
  even when MainActivity or Compose is what crashed.
- `CrashGuard`: after 2 startup crashes in a row SeaGlass is disabled automatically (plain Material surfaces,
  no backdrop capture), so the app can still open.
- `seaGlassSurface`, `MainActivity` and `MiniPlayer` honour that safe mode.
- `ExitInfoReporter` (Android 11+): if the previous run died from a native crash, ANR, memory kill or signal
  (things a Java crash handler cannot see), the reason is shown once on the crash screen at the next launch.

- `SelfLogcat`: the app records its own logcat (own process only, no permission needed) to `filesDir`.
  After a crash, the last lines of that run are shown on the crash screen (Java crashes, native crashes,
  ANRs and memory kills alike). `REASON_CRASH` is now reported too, de-duplicated against the Java handler.

- Crash screen has a **Share report** button: it sends the full text report and, for native crashes/ANRs, the
  complete raw tombstone (`crash_reports/` in filesDir, shared through the existing FileProvider), so nothing is
  cut off or mangled by copy/paste.

## Material 3 Expressive
- Bottom navigation is now the expressive `ShortNavigationBar` / `ShortNavigationBarItem`
  (material3 1.5.0-alpha29). `NavigationBarHeight` is 64dp to match.
- Theme already used `MaterialExpressiveTheme` with `MotionScheme.expressive()`; unchanged.
- The earlier liquid glass / Haze experiment was removed completely.
