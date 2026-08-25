# OpenStory Plugin Repository Index

A repository publishes a bounded UTF-8 JSON index using schema `1`:

```json
{
  "schema": 1,
  "artifacts": [
    {
      "pluginId": "org.example.catalog",
      "version": "1.0.0",
      "downloadUrl": "https://plugins.example.org/org.example.catalog-1.0.0.osp",
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "signatureEd25519": "optional-detached-signature"
    }
  ]
}
```

Each artifact requires:

- a lowercase reverse-domain `pluginId`;
- a semantic `version`;
- an HTTPS `downloadUrl` without user information;
- a lowercase 64-character `sha256` digest of the exact `.osp` bytes;
- an optional non-blank detached Ed25519 signature.

The pair `(pluginId, version)` must be unique and an index contains at most 10,000
artifacts. Repository transport, index provenance, signing-key trust, download limits,
and exact package-byte verification are host responsibilities. Plugin JavaScript receives
none of the repository credentials, signature keys, or raw package paths.

Repository metadata does not advertise or carry login cookies. Authentication declarations live
inside the signed plugin manifest and follow [authentication.md](authentication.md); session data is
local host state and is never serialized into an index or package.
