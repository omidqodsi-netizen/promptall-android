# PromptAll Android v3.6.1

## Purpose

Patch release for the v3.6 detail experience, focused on release-blocking UX issues found before Bazaar publication.

## Fixes in 3.6.1

- Preserve Home gallery scroll position when opening a prompt detail and returning.
  - The staggered-grid state is now owned by the app-level screen instead of the temporary feed composable.
  - Returning from detail restores the exact previous grid position instead of jumping to the first prompt.
- Replace the generic Info symbol on the App Information screen with the real PromptAll launcher artwork.
- Rework "Open in AI" to be app-only.
  - Removed ChatGPT/Gemini/Grok web URLs and browser fallback.
  - Targets the installed Android packages directly: ChatGPT, Gemini and Grok.
  - Uses Android ACTION_SEND + EXTRA_TEXT so supported destination apps can receive the prompt text directly.
  - Prompt is always copied to clipboard first as a fallback.
  - If a destination app does not accept text sharing, PromptAll opens the installed app itself with the prompt already available in clipboard.
  - If the destination app is not installed, PromptAll stays in-app and shows a short message; it does not open a browser or store page.
- Removed the promptall.ir domain from the user-facing share text; sharing now contains only the prompt and PromptAll attribution.
- Added Android package visibility declarations only for the three AI destination apps. No new runtime permission is added.

## Version

- versionName: `3.6.1`
- versionCode: `30601`

## Release outputs

The existing GitHub Actions workflow is version-driven and will generate:

- `promptAll-v3.6.1-release.apk`
- `promptAll-v3.6.1-release.aab`
- `mapping-v3.6.1.txt`
