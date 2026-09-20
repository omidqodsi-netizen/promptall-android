# PromptAll v3.6.0 smoke test

## Static checks completed

- Kotlin source delimiter balance checked for `()`, `{}`, `[]`.
- Kotlin compiler parser pass showed no syntax/illegal-escape/unclosed-token errors (Android/Compose references cannot resolve in this container because Android SDK/Gradle dependencies are not installed).
- Version bumped to `3.6.0` / `30600`.
- No new Android permissions added.
- Existing GitHub Actions workflow remains version-driven and should emit 3.6.0 release names.

## Device / CI checks to run after push

1. Build `assembleRelease` and `bundleRelease` with the existing Bazaar signing secrets.
2. Open a prompt from Home and verify detail page/back navigation.
3. Open a prompt from Search, Favorites, Trending and a category.
4. Tap Copy and verify the temporary `پرامپت کپی شد` state.
5. Toggle Favorite from detail and verify the heart updates and Favorites screen syncs.
6. Share a prompt and verify the Android share sheet includes title + prompt + PromptAll attribution.
7. Tap ChatGPT, Gemini and Grok and verify prompt is in clipboard before the destination opens.
8. Open a similar prompt, then press Back and verify it returns to the previous detail before the feed.
9. Verify similar prompts exclude the current prompt and do not duplicate IDs.
10. Verify detail layout on a small phone and a tall 9:16 phone, including long prompt text.
11. Verify RTL/Persian text alignment and English prompt text readability.
12. Verify bottom navigation is hidden on detail and restored after returning to the feed.
