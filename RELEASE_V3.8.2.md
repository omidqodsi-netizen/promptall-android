# PromptAll Android 3.8.2

## Fixes

- Android image search now uses the same tolerant matching profile as the web image-search page, so an image that is found on the website can also be found from the Android app.
- EXIF orientation is normalized before pHash / dHash / aHash / histogram generation. This prevents the exact same photo from producing a different fingerprint when Android reads a rotated camera/gallery image.
- Existing AI fallback, prompt generation, ML Kit labels and UI remain unchanged.
- The 3.8.1 generated-prompt scrolling fix remains included.

## Version

- versionName: `3.8.2`
- versionCode: `30802`
