# promptAll 3.6.0 — Market release

This project builds a signed APK and AAB that can update the existing PromptAll Android listing, provided the same signing key is used.

## Current release highlights

- Category discovery and Trending flow from v3.5 remain intact.
- Prompt cards now open a dedicated full-screen detail experience.
- Detail view includes hero image, full prompt, Copy, Favorite and Share.
- Similar prompts are loaded independently without changing the current Home/category feed.
- "Open in AI" copies the prompt and opens ChatGPT, Gemini or Grok through public HTTPS app/web links.
- Navigating through similar prompts keeps a local detail history so Back returns naturally.

## GitHub signing setup

Repository Actions must have these four secrets configured with the same release key used for the already published app:

- `PROMPTALL_KEYSTORE_BASE64`
- `PROMPTALL_STORE_PASSWORD`
- `PROMPTALL_KEY_ALIAS`
- `PROMPTALL_KEY_PASSWORD`

Never commit the keystore, passwords, or Base64 value to the repository.

## Build

Every push to `main`, or a manual workflow run, creates versioned files based on `versionName` in `app/build.gradle.kts`:

- `promptAll-v3.6.0-release.apk`
- `promptAll-v3.6.0-release.aab`
- `mapping-v3.6.0.txt`

Download them from the successful GitHub Actions run under `promptAll-v3.6.0-release`.
