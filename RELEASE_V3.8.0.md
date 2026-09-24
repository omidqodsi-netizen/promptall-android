# PromptAll Android v3.8.0

## Main update

- Native app UI for the improved image-search workflow.
- Normal search remains privacy-first: pHash + dHash + aHash + histogram + on-device ML Kit labels.
- Raw image bytes are not sent to PromptAll during normal image search.
- The AI fallback is always visible after an image is selected, even when normal results exist.
- Gemini AI is called directly from the Android device so the user's own VPN/network route is used.
- AI automatically tries multiple Gemini models if the first model fails.
- Gemini first analyzes the image, PromptAll returns candidate prompts, then Gemini performs a second verification pass.
- Exact face identity is intentionally not required; pose, composition, clothing, environment, camera framing, lighting and style are emphasized.
- If verified PromptAll candidates are weak or absent, the generated prompt for the user's image is still available.
- Generated Persian/English prompt is shown high in the AI panel with copy actions.
- Share-to-PromptAll remains supported.
- Existing Home, Categories, Trending, Favorites, Prompt Detail, Similar Prompts and Open-in-AI flows remain unchanged.

## Version

- versionName: `3.8.0`
- versionCode: `30800`

## Backend pairing

Use PromptAll Image Search `1.3.5` or newer so `/status` supplies the client AI configuration required for direct Gemini calls.
Older Android versions continue to ignore the new JSON fields.

## Privacy note

- Normal image search: image never leaves the device; only derived fingerprints/labels are sent to PromptAll.
- AI fallback: only after the user taps the AI button, the selected image is sent directly from the device to Gemini. It is not uploaded to PromptAll.

## Release outputs

The existing version-driven GitHub Actions workflow should generate:

- `promptAll-v3.8.0-release.apk`
- `promptAll-v3.8.0-release.aab`
- `mapping-v3.8.0.txt`

