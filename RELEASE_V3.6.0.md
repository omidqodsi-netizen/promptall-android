# PromptAll Android v3.6.0

## Scope

This release keeps the v3.5 navigation/category work intact and adds a focused prompt-detail experience.

## New in 3.6.0

- New full-screen prompt detail page matching the existing dark/purple PromptAll visual language.
- Tapping prompt cards from Home, Search, Favorites, Trending, or a category opens details.
- Large hero image, title, type badge, complete prompt text and a prominent copy action.
- Favorite and Share actions inside the detail page.
- "Open in AI" section for ChatGPT, Gemini and Grok.
  - The prompt is copied first.
  - Then the selected AI service opens via its public web/app link.
  - This avoids relying on unsupported prompt-prefill deep-link parameters.
- Similar prompts section.
  - Same-category results are preferred when the source category is known.
  - Search/title similarity and already-loaded local content are used as fallback signals.
  - Current prompt is excluded and duplicates are removed.
- Similar-prompt navigation maintains a small in-app detail history so Back returns to the previous prompt before returning to the feed.
- Detail screen hides the bottom navigation to keep the reading/action experience focused.

## Version

- versionName: `3.6.0`
- versionCode: `30600`

## Release build

The existing GitHub Actions workflow reads `versionName` automatically and will generate:

- `promptAll-v3.6.0-release.apk`
- `promptAll-v3.6.0-release.aab`
- `mapping-v3.6.0.txt`

The repository secrets must still point to the same signing key used for the Bazaar release.
