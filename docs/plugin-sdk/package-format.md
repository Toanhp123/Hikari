# OpenStory Plugin Package Format

An OpenStory plugin is a bounded ZIP archive with the `.osp` extension and this layout:

```text
manifest.json
main.js
assets/<relative-file>  # optional
```

Only regular files are accepted. Entry names must be normalized relative paths; absolute
paths, `.`/`..` segments, backslashes, blank segments, duplicate entries, directories,
symbolic links, unsupported top-level files, oversized entries, and oversized archives
are rejected. `manifest.json` and `main.js` are required.

## Manifest

`manifest.json` is UTF-8 JSON. A catalog-only package has this shape:

```json
{
  "id": "org.example.catalog",
  "name": "Example Catalog",
  "version": "1.0.0",
  "protocol": 1,
  "entry": "main.js",
  "provides": ["CATALOG"],
  "operations": ["catalog.home", "catalog.search", "catalog.details", "catalog.filters"],
  "languages": ["en"],
  "homepageUrl": "https://example.org/",
  "sourceUrl": "https://example.org/source",
  "capabilities": {
    "network": {
      "hosts": ["api.example.org", "cdn.example.org"]
    }
  }
}
```

- `id` is a lowercase reverse-domain identifier.
- `version` is a semantic version.
- `protocol` is the supported protocol major.
- `entry` must be exactly `main.js`.
- `provides` contains `CATALOG`, `CONTENT`, or both and cannot be empty.
- `operations` optionally declares the exact wire operations implemented by `main.js`. Every
  declared operation must belong to a service in `provides`. Protocol `1` packages that omit
  `operations` keep the legacy service-level behavior for compatibility; new or updated packages
  should declare the exact operations they implement.
- language tags are normalized lowercase values.
- metadata URLs are HTTPS.
- network hosts are exact lowercase hostnames; wildcards, schemes, ports, and paths are invalid.
- `capabilities.reader.offlineDownload` optionally declares whether a package that implements
  `content.chapter` can safely persist complete chapter bodies for explicit offline use. It defaults
  to `true` for backward compatibility. Set it to `false` for online-only documents such as remote
  image-page descriptors.
- `capabilities.reader.remoteImages` is an explicit opt-in for chapter documents that contain remote
  image-page URLs fetched by the host Reader. It defaults to `false`; image blocks from packages that
  do not declare this capability are rejected before rendering. This is deliberately separate from
  the JavaScript network allowlist: plugin code still cannot fetch undeclared hosts, while the Reader
  may retrieve only sanitized HTTPS image descriptors for a package that opted into remote images.
  Remote-image capability currently requires `offlineDownload: false`, because the host does not yet
  persist complete page assets. A reader capability is only valid when `content.chapter` is supported.
- `capabilities.authentication` optionally declares a guarded host-owned login flow and scoped
  credential targets. See [authentication.md](authentication.md) for the exact JSON contract and
  validation rules.

The manifest describes execution permissions. It does not attest to its containing archive.
The installer verifies the SHA-256 of the exact `.osp` bytes against detached provenance.

## Script and assets

`main.js` exposes operations through `globalThis.openstoryPlugin` and communicates with
the host only through the serialized protocol and host-controlled capabilities. Plugin
JavaScript never receives Android APIs, filesystem paths, raw network clients, reflection,
or plaintext managed credentials.

Files under `assets/` are package data, not a filesystem capability. Runtime behavior and
wire examples are documented in [javascript-runtime.md](javascript-runtime.md).
