# SeaGlass full GPU backdrop integration

- Android 13+ uses SeaGlass `AGSL_FULL` (`RuntimeShader` + RenderNode) when GPU backdrop is enabled.
- `rememberSeaGlassStageState()` is created once at the app root. A neutral M3 Play surface is recorded with `Modifier.seaGlassBackdrop(stageState)` before the foreground composition.
- The stage is exposed with `LocalSeaGlassStageState`; SeaGlass components can now read it.
- Android hardware acceleration is explicitly enabled at application and MainActivity level.
- `CrashGuard` tracks the current GPU test session. If Android reports a newer `REASON_CRASH_NATIVE` exit, `ExitInfoReporter` disables only GPU backdrop for the next launch; lightweight SeaGlass surfaces remain available.
- Android 12 and lower, or a guarded fallback session, use `LIGHTWEIGHT_TINT`.

The AGSL path must still be tested on the target Mali device. This source change cannot guarantee vendor-driver stability.
