# APK Size Report — v3.8.3 Lite

Measured reference APK (the working build before this optimization): approximately **22.34 MB**.

Largest entries inside that APK were:

- `lib/arm64-v8a/libmlkitcommonpipeline.so` — 10,989,136 bytes
- `lib/armeabi-v7a/libmlkitcommonpipeline.so` — 6,928,228 bytes
- `assets/mlkit_label_default_model/mobile_ica_8bit_with_metadata_tflite` — 3,042,638 bytes raw / 1,989,552 bytes compressed

Those ML Kit files accounted for about **19.9 MB of compressed APK size**.

v3.8.3 removes the ML Kit Image Labeling dependency while keeping the current PromptAll fingerprint search (`pHash`, `dHash`, `aHash`, histogram) and Gemini fallback. Based on the measured previous APK contents, the release APK is expected to drop to roughly **2.5–4 MB** depending on the final R8/resource output produced by GitHub Actions.

The actual size must be confirmed from the new signed GitHub Actions artifact.
