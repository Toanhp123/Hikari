# LNReader engine v3 — replacement architecture

## Decision

Keep Hikari's Plugin Manager, immutable admitted artifact snapshot, `SourceId`
(`lnreader:<id>`), `SourceMediaRef` (`lnreader-v1:`), common SourceRegistry,
Library, cache, progress, and Novel Reader. Replace the *source-facing JS host*
with versioned modules built together, rather than shipping a historical host
bundle plus a separately appended compatibility script.

Entry point: `tool/lnreader/host.js`. Implementation:

- `engine/native_bridge.js` — serialized JS/Android calls and fixed safe error codes.
- `engine/web_compat.js` — browser primitives used by LNReader v3 bundles.
- `engine/fetch.js` — `@libs/fetch`, Response-like interface and encoding.
- `engine/storage.js` — LNReader v3 keys, source-scoped persistent storage.
- `engine/modules.js` — one explicit CommonJS import allowlist, real pinned
  cheerio, dayjs and htmlparser2; no permissive empty shims.
- `LnReaderHttpHeaders` — validates tokens/values and rejects hop-by-hop,
  authority, content-length and encoding headers managed by OkHttp; does **not**
  maintain an ad hoc allowlist of provider-specific header names.

Native network still enforces approved HTTPS origins, public DNS, bounded request
count, redirects, response size, deadline and credentials stripping.

## Contract and evidence

Source: https://github.com/lnreader/lnreader/blob/master/src/plugins/pluginManager.ts
and https://github.com/lnreader/lnreader-plugins/blob/master/src/types/plugin.ts
and https://github.com/lnreader/lnreader-plugins/blob/master/src/lib/fetch.ts .
Plugin test methodology: `docs/testing.md` in lnreader-plugins.

A module **loading** is not evidence that a plugin **works**. Installation
admission rejects known unsupported modules. Use offline replay first, then
Android instrumentation, then opt-in live website tests. Providers may be
unavailable or return anti-bot screens; that is not necessarily an engine bug.

## Compatibility profile: v3-text-alpha

Currently covered: compiled CommonJS bundles using cheerio, dayjs, htmlparser2,
`@libs/fetch` (`fetchApi` plus `fetchText` with selected encodings),
`@libs/storage` source storage, `@libs/filterInputs`, `@libs/novelStatus`,
`@libs/defaultCover`, `@libs/isAbsoluteUrl`, urlencode, URL, Headers,
URLSearchParams, text-only FormData, and a bounded timer.

Intentionally **not fully supported**: `fetchProto`, binary file downloads,
`@libs/aes`, `@libs/utils`, full WebView local/session storage and browser
extensions, arbitrary globals and modules. No provider-wide compatibility claim.
The full v3 contract requires these to be implemented and Android-tested.

## Verification

1. `node --experimental-vm-modules tool/lnreader/engine_contract_test.mjs`
   (source modules, fake third-party exports, fake network/storage).
2. `npm ci --ignore-scripts --prefix tool/lnreader`
3. `npm --prefix tool/lnreader run build` (regenerates Android asset)
4. `node tool/lnreader/host_contract_smoke.mjs`
5. `node tool/lnreader/upstream_api_smoke.mjs`
6. `flutter test`, `flutter analyze`, Android instrumentation `NovelRuntimeTest`,
   `NovelAdmissionTest`, then real-device provider smoke tests. **Not verified
   by step 1.**

Because `host.js` is a generated Android asset committed to the repository,
**do not ship an APK until step 3 is done**. Android Gradle checks the
engine-profile marker and rejects the old generated asset. On an offline machine without npm
packages, this migration remains a source patch only, not a production fix.

## Next compatibility iterations (explicit scope)

- Native byte transport + `Response.arrayBuffer()`/`blob()`, verified with
  replay and bounded memory before enabling `fetchProto` / `fetchFile`.
- Pinned, licensed implementations of `protobufjs`, `@noble/ciphers` AES GCM,
  UTF8 helpers and URL parsing where a real plugin uses them. Do not ship
  fake objects merely to satisfy admission.
- Plugin-specific settings and WebView authenticated sessions, gated by
  explicit consent; no CAPTCHA bypasses or ambient cookies.
- Replay test corpus with pinned upstream bundles for NovelDex, Hako,
  Light Novel VN, 69shu, WuxiaWorld, WTR-Lab, search/detail/chapter.

## Rollback

Apply source patch in one Git change and regenerate the JS asset. Before
running the Android build, inspect `git diff --stat` and test the generated
artifact. A failed migration is reverted as one commit/patch; no Library or
progress DB migration is required because IDs and adapters are unchanged.
