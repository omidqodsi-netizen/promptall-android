# promptAll 3.4.3 — Market release

This project builds a signed APK and AAB that can be used for the existing
promptAll Android listing, provided the same signing key is used for the update.

## What changed in 3.4.3

- Home opens with a randomized selection from older API pages.
- The newest prompts stay visible in a dedicated horizontal row at the top.
- “نمایش بیشتر” switches the main feed to newest-first chronological browsing.
- “نمایش تصادفی” switches back to the randomized feed.
- The center refresh button returns to random mode and creates a fresh mix.
- Offline cache keeps the latest page first and a randomized pool after it.

## GitHub signing setup

Repository Actions must have these four secrets configured with the same release
key used for the already published app:

- `PROMPTALL_KEYSTORE_BASE64`
- `PROMPTALL_STORE_PASSWORD`
- `PROMPTALL_KEY_ALIAS`
- `PROMPTALL_KEY_PASSWORD`

Never commit the keystore, passwords, or Base64 value to the repository.

## Build

Every push to `main`, or a manual workflow run, creates versioned files based on
`versionName` in `app/build.gradle.kts`:

- `promptAll-v3.4.3-release.apk`
- `promptAll-v3.4.3-release.aab`
- `mapping-v3.4.3.txt`

Download them from the successful GitHub Actions run under
`promptAll-v3.4.3-release`.
