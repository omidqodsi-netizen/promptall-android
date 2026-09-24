# PromptAll image-search architecture

## Privacy boundary

Raw user images never leave the Android device. PromptAll v3.7.0 decodes the selected/shared URI locally, downsamples it in memory, derives numeric fingerprints and local ML Kit semantic labels, sends only those small derived values, then releases the bitmap.

## Server scope

`PromptAll Image Search 1.0.0` is a separate WordPress plugin and REST namespace. It does not modify the current PromptAll API implementation.

Only published posts of the admin-selected Prompt post type are indexed. Media Library browsing is never exposed to users.

## Storage

No WordPress/MySQL table is created.

- Index: sharded JSON files under a private plugin data directory.
- Pending indexing queue: file-based.
- Daily per-installation quota: 1024-shard, date-scoped file cache to keep lock/write size small under heavier traffic.
- Network abuse guard: hashed IP key only; raw IP is not persisted by the plugin.
- No per-search history is stored.

## Index updates

- Initial rebuild queues all published prompt IDs.
- Small WP-Cron batches process the queue to limit host CPU spikes.
- Publishing/updating a prompt queues only that post.
- Unpublishing/deleting a prompt removes it from the search index.

## Matching in 1.0

The first production-safe version uses a hybrid model:

- perceptual fingerprints for exact/near-duplicate matching,
- color similarity for low-level visual ranking,
- the bundled ML Kit image-labeling model on Android for semantic hints,
- precomputed prompt-text tokens in the file index for a semantic score.

There is no cloud AI request and no server-side model inference. This keeps the feature private and lightweight while improving results for images that are not pixel-identical. The REST namespace and index format remain versioned so a future full vector-embedding engine can be added without breaking older app versions.

## Compatibility contract
The existing `promptall/v1` endpoints are not modified. The WordPress feature can be installed, indexed and kept disabled before Android v3.7.0 is released. Older app versions never call the new namespace and continue to work normally.
