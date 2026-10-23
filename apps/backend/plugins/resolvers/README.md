# otter-plugin-resolvers

Otter plugin that turns hoster links into playable URLs. It is a thin wrapper over the SDK's hoster registry (`@neoworks-dev/otter-sdk/hosters`): VOE, Vidara, Doodstream, Filemoon, Streamtape and Vinovo have dedicated extractors, and every other host goes through the generic page scanner (`<video>`/`<source>` tags, media URLs in the page and in its iframes, packed scripts).

## Capability

| Capability | Description |
| --- | --- |
| `resolve` (`sources`) | Hoster link in, `{ url, mime_type, headers }` out. Domain `*` (fallback) for any host. |

Errors come back with a code: `extraction_failed` when no stream is found, `source_offline` when the host is down, `blocked` behind a Cloudflare challenge.

## Development

```sh
bun test                                   # offline fixtures + live hosters
cd ../.. && bun scripts/bundle-plugins.ts  # bundles into $DATA_DIR/plugins
```
