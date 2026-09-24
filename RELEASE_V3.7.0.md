# PromptAll Android v3.7.0

## Main feature: یافتن پرامپت با تصویر

- New privacy-first image search inside the existing Search tab.
- User can choose an image from the phone or Share an image from another Android app directly to PromptAll.
- Raw image bytes are never sent to PromptAll servers.
- The app computes a compact local fingerprint on-device:
  - pHash
  - dHash
  - 24-bin RGB histogram
  - up to 8 semantic image labels from the bundled on-device ML Kit model
- The AI model runs locally on the phone; no cloud AI call is required.
- Only the fingerprint + small semantic labels are sent to the new independent backend namespace `promptall-search/v1`.
- Search results show similarity percentage and match label:
  - همان تصویر
  - بسیار نزدیک
  - مشابه
- Results open the normal PromptAll prompt-detail screen and support Favorites.
- Daily quota is enforced server-side; default is 3 searches per installation per day.
- The UI shows remaining daily searches.
- If the image-search plugin is disabled/unavailable, all existing PromptAll features keep working normally.

## Share-to-PromptAll

Android now registers an `ACTION_SEND` handler for `image/*`.
Users can share an image from Telegram, browser, gallery, etc. and select PromptAll. The app switches to Search and starts the local fingerprint/search flow.

## Backward compatibility

The existing endpoints used by v3.6.1 and older are unchanged:
- `/wp-json/promptall/v1/prompts`
- `/wp-json/promptall/v1/categories`

The new feature uses a separate namespace:
- `/wp-json/promptall-search/v1/status`
- `/wp-json/promptall-search/v1/search`

## Version

- versionName: `3.7.0`
- versionCode: `30700`

## Important backend pairing

Install `PromptAll Image Search 1.0.0`, configure the correct Prompt post type / meta fields, build the initial index, and only then enable image search. The plugin may be installed and indexed before v3.7.0 is published; older Android versions are unaffected.
