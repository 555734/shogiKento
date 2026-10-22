# Pinned native engine cache

These gzip files contain the exact four executable objects from the verified v1.8.4 APK. `manifest.json` records the source build, APK SHA-256, uncompressed object hashes and build-recipe hash. `scripts/restore_native_cache.py` verifies every hash and restores executable permissions. Any change to the native recipe requires a fresh build from the pinned source. This cache contains no additional model and does not increase APK size.

Engine license: GPL v3, in `app/src/main/assets/licenses/YaneuraOu-GPL-3.0.txt`. Complete corresponding source, including the engine and build scripts, is available in each Actions source ZIP; `scripts/prepare_yaneura.py` downloads the exact upstream source commit `c1b80eaa09fe13d5f12b1599d1ae4d53c224de30`, verified by SHA-256, and `scripts/build_yaneura.py` applies the documented build configuration. The original generated artwork and notices are preserved.
