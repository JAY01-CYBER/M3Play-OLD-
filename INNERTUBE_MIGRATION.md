# InnerTube migration

M3Play's previous `com.jay.innertube` module has been replaced with the newer
Glossy/Metrolist InnerTube implementation.

Changes:
- Migrated the full Glossy InnerTube source and tests.
- Repackaged `com.metrolist.innertube` -> `com.jay.innertube` to keep M3Play call sites stable.
- Kept the `:innertube` project path unchanged.
- Switched the module to M3Play's existing Kotlin/JVM 21 module style.
- Added the Glossy/MetrolistExtractor dependency to the M3Play version catalog.
- Updated existing M3Play InnerTube imports to the migrated package.
- No application call-site API changes were required based on the static API comparison.
