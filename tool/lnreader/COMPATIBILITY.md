# LNReader v3 compatibility (Hikari)

The current engine's single source of truth is [`ENGINE_REBUILD.md`](ENGINE_REBUILD.md).
The v3 **text profile** is *not* full upstream compatibility. The supported
module names and the safety envelope are versioned separately from plugin
versions. Import success is not an indication of Search/Detail/Chapter success.

| Module | Compatibility level |
|---|---|
| `cheerio`, `htmlparser2`, `dayjs` | Pinned real JS libraries, bundled offline |
| `@libs/fetch` | `fetchApi`, `fetchText` with native HTTPS origin policy and selected text encodings; NO `fetchProto`/`fetchFile` yet |
| `@libs/storage` | Source-scoped persistent storage, read-only WebView state |
| `@libs/novelStatus`, `@libs/filterInputs`, `@libs/defaultCover`, `@libs/isAbsoluteUrl`, `urlencode` | Explicit v3 module table |
| `@libs/aes`, `@libs/utils` | Not supported until vetted byte/Crypto transport tests |
| Browser WebView and arbitrary native modules | Not supported |

The JavaScript host is assembled from `engine/*.js` by esbuild into one
Android asset. **There is no separately appended polyfill file.** To reproduce
and validate the asset, follow `ENGINE_REBUILD.md`. Fix generic API gaps at
this layer and add contract/replay tests; do not patch per-provider identifiers
or weaken the HTTPS, origin, DNS or size limits to mask failures.
