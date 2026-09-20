# PromptAll v3.6.1 smoke test

## Static checks completed

- Kotlin delimiter balance checked for `()`, `{}`, `[]` with no mismatch.
- Kotlin parser pass found no `expecting`, `unclosed`, `illegal escape`, `syntax error`, or `unexpected tokens` errors. Android/Compose symbols cannot fully resolve in this container because the Android SDK/Gradle dependency graph is unavailable.
- AndroidManifest XML parses successfully.
- versionName/versionCode checked as `3.6.1` / `30601`.
- No ChatGPT/Gemini/Grok HTTP(S) URL remains in app UI/source.
- User-facing share attribution no longer includes a website URL.
- No new runtime permission was added.

## Device / CI checks before Bazaar publication

1. Build signed `assembleRelease` and `bundleRelease` using the existing Bazaar signing secrets.
2. Home gallery: scroll several screens down, open a prompt, press Back, and verify the exact previous grid position is preserved.
3. Repeat Back test in list mode, Search, Favorites and a category feed.
4. App Information: verify PromptAll launcher artwork appears instead of an Info/exclamation-style symbol.
5. ChatGPT installed: tap "Open in AI" and verify no browser chooser appears; ChatGPT opens directly. Verify shared prompt text is present when ChatGPT accepts Android text sharing.
6. Gemini installed: repeat the same test.
7. Grok installed: repeat the same test.
8. For any destination that does not accept ACTION_SEND text, verify the app itself opens and the prompt is available in clipboard.
9. Uninstall one destination app and verify tapping it does not open Chrome/Play Store; a short "not installed / prompt copied" message should appear.
10. Verify normal prompt Share still opens the Android share sheet and contains no external URL.
11. Verify detail Back history through Similar Prompts still works as in v3.6.0.
