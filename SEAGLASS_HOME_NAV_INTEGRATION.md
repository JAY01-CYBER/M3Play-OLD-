# SeaGlass Home + Navigation integration

## Source audit
- The app's embedded `seaglass-compose` sources match the attached upstream SeaGlass repository for `SeaGlassStage.kt`, `SeaGlassComponents.kt`, `SeaGlassTheme.kt`, and `SeaGlassUiKit.kt`.
- Home greeting card already uses the app's `seaGlassSurface` adapter, which calls upstream `Modifier.seaGlass`.
- Home quick-action chips also use the same adapter through `ChipsRow`.

## Changes
- Replaced the stock Material 3 `ShortNavigationBar` with upstream `SeaGlassPillNavBar`.
- Preserved the existing route selection, Explore double-tap search behavior, and selected-tab scroll-to-top behavior.
- Chose the upstream `LIGHTWEIGHT_TINT` tier for this device after the earlier Mali/RenderThread native crash. This keeps SeaGlass's SDF contour, material tint, etched glyphs, and bevel treatment while avoiding the full AGSL/RenderNode backdrop pipeline.
- Removed app-wide SeaGlass backdrop recording for this initial Home/navigation rollout and passes a null stage state.

## Verification
`bash gradlew :app:compileDebugKotlin --no-daemon` was attempted but Gradle 9.8 could not be downloaded because `services.gradle.org` DNS resolution failed. Source compilation and device rendering therefore remain unverified.
