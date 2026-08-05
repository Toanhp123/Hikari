# OpenStory Plugin Package Format

OpenStory plugins are distributed as portable `.osp` ZIP archives. The host validates the complete archive before installing or initializing any runtime.

## Required package layout

Every package contains:

```text
manifest.json
```

A package may additionally contain only the entries supported by its declared runtime:

```text
selector.json
main.js
CHANGELOG.md
LICENSE
```

`manifest.json` is always required. `selector.json` is used by declarative selector plugins. `main.js` is used by JavaScript plugins.

Archive entry names are exact and case-sensitive.

## Archive validation

The host rejects a package before extraction when any of the following conditions is detected:

| Error | Meaning |
|---|---|
| `PATH_TRAVERSAL` | An entry is absolute or contains a `..` path segment |
| `DUPLICATE_ENTRY` | Two ZIP entries have the same path |
| `UNDECLARED_ENTRY` | An entry is not part of the supported package layout |
| `MISSING_MANIFEST` | `manifest.json` is absent |
| `SYMBOLIC_LINK` | An entry is a symbolic link |
| `ENTRY_COUNT_LIMIT` | The archive contains too many entries |
| `COMPRESSED_SIZE_LIMIT` | Total compressed bytes exceed the configured ceiling |
| `UNCOMPRESSED_SIZE_LIMIT` | Total expanded bytes exceed the configured ceiling |
| `SUSPICIOUS_COMPRESSION_RATIO` | An entry exceeds the allowed expansion ratio |
| `UNDECLARED_EXECUTABLE` | An executable entry is not declared by the package metadata |

Validation is performed using entry metadata before extraction. The installer must not write rejected content to its final plugin directory.

## Default safety ceilings

The reference contract defines these default limits:

| Limit | Default |
|---|---:|
| Maximum entry count | 128 |
| Maximum compressed bytes | 16 MiB |
| Maximum uncompressed bytes | 64 MiB |
| Maximum per-entry compression ratio | 100:1 |

A host may apply stricter limits. It must not silently apply weaker limits to untrusted packages.

## Executable entries

Executable content is denied by default.

A package may execute only the entry declared by its manifest and runtime contract. For example:

- declarative runtime: `selector.json`
- JavaScript runtime: `main.js`

An executable ZIP entry that is absent from the declared executable-entry set is rejected with `UNDECLARED_EXECUTABLE`.

Plugins cannot declare native libraries, shell scripts, Android components, filesystem adapters, or additional executable binaries.

## Exact package checksum

`exactPackageSha256` is the lowercase SHA-256 digest of the exact `.osp` byte sequence as downloaded or selected by the user.

The checksum is calculated over the archive bytes themselves, not:

- the extracted directory;
- a re-created ZIP;
- normalized JSON;
- individual file digests;
- transport encoding.

Example:

```json
{
  "pluginId": "community.example",
  "version": "1.2.3",
  "exactPackageSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "signature": null
}
```

The installer must compare the calculated digest with the expected digest before extraction.

## Ed25519 signatures

Signed packages use `ED25519`.

The signed UTF-8 payload is exactly:

```text
<exactPackageSha256>
<pluginId>
<version>
```

There is one line-feed character (`\n`) between fields and no trailing newline.

For example:

```text
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
community.example
1.2.3
```

Signature metadata:

```json
{
  "algorithm": "ED25519",
  "signerKeyId": "author-main",
  "signatureBase64": "c2lnbmF0dXJl"
}
```

The signature binds the immutable package bytes to the plugin ID and version. A signature valid for one plugin or version cannot be reused for another.

Signer trust is scoped by the user or repository. OpenStory does not assume one globally centralized signing authority.

## Signature states

The installer records one of these states:

| State | Meaning |
|---|---|
| `VERIFIED` | Checksum and trusted signature were verified |
| `UNSIGNED` | No package signature was supplied |
| `INVALID` | A supplied checksum or signature failed verification |

An invalid package must not initialize.

## Unsigned-package warning path

Unsigned packages remain installable only through an explicit warning flow. The user must acknowledge the warning before installation proceeds.

The host stores install provenance:

```json
{
  "source": "LOCAL_FILE",
  "sourceReference": "example.osp",
  "signatureState": "UNSIGNED",
  "unsignedWarningAcknowledged": true
}
```

Supported source values are:

- `LOCAL_FILE`
- `MANIFEST_URL`
- `REPOSITORY`

For `UNSIGNED`, `unsignedWarningAcknowledged` must be `true`. The provenance record must remain associated with the installed plugin so diagnostics and plugin-management UI can show how it was installed.

## Installation order

A conforming installer performs these steps in order:

1. Read archive metadata without extracting to the final directory.
2. Enforce entry count, byte ceilings, and compression-ratio limits.
3. Reject absolute paths, traversal, duplicate names, and symbolic links.
4. Confirm `manifest.json` exists and entries match the supported layout.
5. Parse the manifest and validate it against the [plugin API versioning policy](api-versioning.md).
6. Calculate SHA-256 over the exact archive bytes.
7. Compare the checksum with package or repository metadata.
8. Verify the Ed25519 signature when present.
9. Require and record acknowledgement for unsigned packages.
10. Extract into an isolated temporary location.
11. Re-check the declared runtime entry.
12. Atomically publish the verified package into plugin-scoped storage.

No runtime code or selector definition is initialized before all applicable checks succeed.
