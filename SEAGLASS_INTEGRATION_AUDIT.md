# SeaGlass integration audit

- M3 Play remains a native Android Jetpack Compose application.
- The Flutter `sea_glass-main` repository is a Dart/Flutter port of the Kotlin SeaGlass implementation; its README identifies `mttmcknn/sea-glass` (Compose) as the rendering source. Flutter widgets and Dart shader code are not inserted into this Android app.
- The native Android `seaglass-core` and `seaglass-compose` source trees are copied from `SeaGlass-main/android/` (the supplied upstream Android repository). Module Gradle files retain M3 Play's compatibility settings.
- The floating top app bar uses the upstream `SeaGlassTopAppBar`, `SeaGlassContentRole.NAVIGATION_CHROME`, and shared `SeaGlassStageState`.
- The backdrop follows the upstream two-layer pattern: a sibling background recorded by `Modifier.seaGlassBackdrop(stageState)` and foreground content rendered separately.
- Foreground content top inset is status-bar height + 8dp, matching the upstream sample's scroll-content spacing pattern. It is not a 76dp reserved app-bar slot.
- The existing M3 Play navigation and playback flows are retained.
- No Gradle build or device runtime test is claimed by this package.
