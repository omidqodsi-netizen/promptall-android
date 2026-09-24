# PromptAll Android v3.7.0 smoke test

## Static checks completed

- AndroidManifest XML parses successfully.
- versionName/versionCode are `3.7.0` / `30700`.
- Kotlin delimiter balance checked for the changed source files.
- Image-search Retrofit body contains only installation ID, versionCode, compact numeric fingerprints and up to 8 on-device semantic labels; no image/base64/url field exists.
- No storage/media runtime permission was added.
- `ACTION_SEND image/*` was added with `singleTop` handling for warm-app shares.
- Existing `promptall/v1` calls remain unchanged.

## Device / CI checks required before release

1. Install and configure PromptAll Image Search 1.0.0 on the site.
2. Keep the feature disabled while the initial index is building.
3. Verify the plugin diagnostics show image + prompt text for sample prompt posts.
4. Build the complete index and verify queue reaches zero.
5. Enable image search in the plugin.
6. Build Android release APK/AAB with the existing signing secrets.
7. Open Search; verify the image-search card shows `3 از 3 جستجو باقی مانده` on a fresh install.
8. Pick an image that already exists in PromptAll; verify the matching prompt appears near the top.
9. Pick a resized/compressed copy of the same image; verify near-match behavior.
10. Pick a visually similar but different image and review ranked similarity results; verify the bundled on-device labeler does not require a cloud request.
11. After 3 successful searches, verify the fourth request is blocked server-side and normal text search still works.
12. Share an image from Telegram/browser/gallery to PromptAll and verify Search opens automatically.
13. Verify the selected user image does not appear in WordPress Media Library and no upload file is created on the server.
14. Open a result, then Back; verify image-search results remain visible.
15. Verify Home, text Search, Categories, Trending, Favorites, Detail, Similar Prompts and Open-in-AI still work.
16. Test an older released app (v3.6.1) while the new plugin is active and confirm all old features work unchanged.

17. Launch PromptAll from an image Share intent immediately after a cold start; verify backend status is checked before local image analysis and disabled service does not consume a search.
