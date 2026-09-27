# PromptAll image-search architecture

## Privacy boundary

Raw user images never leave the Android device. PromptAll decodes the selected/shared URI locally, downsamples it in memory, derives numeric perceptual fingerprints (pHash/dHash/aHash + histogram), sends only those small derived values, then releases the bitmap.

The lightweight Android release no longer bundles ML Kit Image Labeling. This is intentional: the normal site matcher works from the perceptual fingerprints and the explicit Gemini fallback remains available only when the user chooses it.

## Server scope

`PromptAll Image Search` is a separate WordPress plugin and REST namespace. It does not modify the current PromptAll API implementation.

Only published posts of the admin-selected Prompt post type are indexed. Media Library browsing is never exposed to users.

## Storage

No per-search image is stored by the Android app or the search endpoint.

- Index: sharded JSON files under a private plugin data directory.
- Pending indexing queue: file-based.
- Daily per-installation quota: date-scoped cache.
- Network abuse guard: hashed IP key only; raw IP is not persisted by the plugin.
- No per-search history is stored.

## Index updates

- Initial rebuild queues all published prompt IDs.
- Small WP-Cron batches process the queue to limit host CPU spikes.
- Publishing/updating a prompt queues only that post.
- Unpublishing/deleting a prompt removes it from the search index.

## Matching

The lightweight Android client uses:

- perceptual fingerprints for exact/near-duplicate matching,
- color histogram similarity for low-level visual ranking,
- precomputed server index data for candidate ranking,
- optional explicit Gemini fallback for semantic analysis and prompt generation.

The normal search path does not call cloud AI. Gemini is a separate user-triggered path and uses the user's own network/VPN connection.

## Android exact-match parity

The Android client sends `client_type=web` for the fingerprint search endpoint intentionally. The backend web profile contains browser/device-tolerant rescue thresholds because Android Bitmap scaling and browser Canvas/GD scaling can differ by a few hash bits even for the same source image. This does not upload the raw image and does not change the quota model.

Android also normalizes EXIF orientation before fingerprinting so gallery/camera images match the visual orientation used by browsers.

## Compatibility contract

The existing `promptall/v1` endpoints are not modified. Older app versions never call the new namespace and continue to work normally.
