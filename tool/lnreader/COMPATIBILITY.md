# LNReader v3 plugin API compatibility

**Source of truth:** [LNReader `pluginManager.ts`](https://github.com/lnreader/lnreader/blob/master/src/plugins/pluginManager.ts) and the [plugin types](https://github.com/lnreader/lnreader-plugins/blob/master/src/types/plugin.ts). Inspect these when updating the compatibility layer, not provider-by-provider trial and error.

The module names below are the upstream **CommonJS `require` table**, not a promise that every plugin using a module is compatible. All invocation is bounded by QuickJS, source origins, native network/storage policies and explicit admission. Only tested subsets are exposed; unsupported imports fail admission.

| Upstream module | Hikari contract | Notes |
|---|---|---|
| `cheerio` | Partial | Bundled `cheerio/slim`, CSS selector subset; not browser DOM |
| `htmlparser2` | Supported `Parser` | Actual library from host bundle, not handwritten tokenizer |
| `dayjs` | Supported | Bundled pinned Day.js |
| `@libs/novelStatus` | Supported | Upstream v3 string constants |
| `@libs/filterInputs` | Supported | Upstream `FilterTypes` enum; filter UI not implemented |
| `urlencode` | Partial | Percent `encode` / `decode` via JS standard functions |
| `@libs/isAbsoluteUrl` | Supported classification | No network access granted by predicate |
| `@libs/defaultCover` | Supported constant | URL only, loaded through existing media policy |
| `@libs/fetch` | Partial | HTTPS `fetchApi`, UTF-8 `fetchText`; no `fetchProto` or binary file download |
| `@libs/storage` | Partial | `storage` get/set/delete/clearAll/getAllKeys + read-only webview snapshots, per-source native storage |
| `@libs/aes` | Unsupported | No compatible bounded AES-GCM implementation vetted |
| `@libs/utils` | Unsupported | Requires binary data/API compatibility review |
| `@libs/url` | Hikari legacy | `absoluteUrl`, not an LNReader upstream module |

Browser/global support is deliberately limited: text-only `FormData`, basic `URL`/`URLSearchParams`, bounded synchronous `setTimeout` for `await new Promise(resolve => setTimeout(resolve, ms))`; no ambient `fs`, WebView, React Native, arbitrary CommonJS resolution or background timers. Web Storage snapshots are read-only and source-scoped. Only UTF-8 text fetch is currently supported.

## Reproducible verification

1. `npm ci --ignore-scripts --prefix tool/lnreader`
2. `npm --prefix tool/lnreader run build`
3. `node tool/lnreader/host_contract_smoke.mjs`
4. `node tool/lnreader/upstream_api_smoke.mjs`
5. Run `NovelAdmissionTest` and `NovelRuntimeTest` on Android with actual QuickJS.
6. Pin compiled upstream plugin bundle versions and verify Search → Detail → Chapters → Read using deterministic HTTP replay. Separately verify provider online behavior.

The Node checks use a mocked native call and **do not** establish Android/QuickJS compatibility, trust, or real provider success. Do not mark a plugin compatible solely because its CommonJS imports succeed during admission.

New API surface should be added only after checking LNReader's module implementation, associated license, security impact, and a runnable contract test. Do not use permissive dummy modules to make admission pass.
