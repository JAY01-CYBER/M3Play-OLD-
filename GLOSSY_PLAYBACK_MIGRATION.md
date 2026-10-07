# Glossy ExoPlayer migration

M3Play keeps its existing Media3 queue/service, URL resolver, cache and playback-recovery logic.
Only the ExoPlayer-side setup was aligned with Glossy: a 750 ms startup/rebuffer target,
Media3 LoadControl, device-volume control, and the existing Media3 audio processor chain.

Glossy's native audio engine, DSP, JNI bridge and C++ code are intentionally NOT included.
This keeps playback fully on ExoPlayer/Media3 and avoids introducing a second audio engine.
