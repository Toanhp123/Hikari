# OpenStory Community Repository Index

A community repository is an HTTPS-hosted JSON document that lists immutable OpenStory plugin package artifacts.

The index is discovery and update metadata. It does not grant a plugin additional capabilities, bypass manifest validation, or replace package checksum/signature verification.

## Schema version

The current repository schema version is `1`.

```json
{
  "schemaVersion": 1,
  "repositoryId": "community.main",
  "artifacts": []
}
```

Unknown schema versions are rejected with:

```text
UNSUPPORTED_SCHEMA_VERSION
```

## Artifact record

Each version is represented by one immutable artifact record:

```json
{
  "pluginId": "community.example",
  "version": "1.2.3",
  "packageUrl": "https://repo.example/community.example/1.2.3.osp",
  "exactPackageSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "signature": {
    "algorithm": "ED25519",
    "signerKeyId": "author-main",
    "signatureBase64": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
  },
  "changelogUrl": "https://repo.example/community.example/1.2.3-changelog.md",
  "declaredCapabilities": [
    "NETWORK"
  ],
  "rollback": {
    "version": "1.2.2",
    "packageUrl": "https://repo.example/community.example/1.2.2.osp",
    "exactPackageSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  }
}
```

Fields:

| Field | Meaning |
|---|---|
| `pluginId` | Stable plugin ID from the package manifest |
| `version` | Published plugin version |
| `packageUrl` | HTTPS URL for the exact `.osp` artifact |
| `exactPackageSha256` | SHA-256 of the exact artifact bytes |
| `signature` | Optional Ed25519 package signature metadata |
| `changelogUrl` | Optional HTTPS changelog URL |
| `declaredCapabilities` | Capabilities displayed before installation/update |
| `rollback` | Optional metadata for a known previous artifact |

The package manifest remains authoritative for runtime behavior. Repository capability metadata is used for preview and change review; installation must reject discrepancies that violate package or permission policy.

## Immutable version artifacts

The pair:

```text
pluginId + version
```

identifies one immutable artifact identity.

For an existing pair, a repository must not silently change:

- `packageUrl`;
- `exactPackageSha256`;
- signature metadata.

Publishing conflicting artifact identities for the same plugin and version produces:

```text
IMMUTABLE_VERSION_CONFLICT
```

A corrected package must use a new version. Repositories must not replace bytes behind an existing version URL while keeping the same checksum metadata.

## Update behavior

A host determines updates by comparing installed and repository versions, then presents:

- target version;
- changelog;
- checksum/signature state;
- capability changes;
- available rollback metadata.

The host downloads the target artifact and independently validates the package format, manifest, checksum, signature, and permissions. Presence in a repository is not sufficient for trust.

## Rollback metadata

Rollback metadata points to a previously published immutable artifact:

```json
{
  "version": "1.2.2",
  "packageUrl": "https://repo.example/community.example/1.2.2.osp",
  "exactPackageSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
}
```

The host still validates the rollback artifact as a normal package. Rollback metadata does not override local user data, plugin-scoped storage policy, or compatibility checks.

## Forward compatibility and unknown fields

Repository parsing preserves unknown optional fields when a document is decoded and encoded again.

Example input:

```json
{
  "schemaVersion": 1,
  "repositoryId": "community.main",
  "futureTopLevel": {
    "enabled": true
  },
  "artifacts": [
    {
      "pluginId": "community.example",
      "version": "1.0.0",
      "packageUrl": "https://repo.example/example-1.0.0.osp",
      "exactPackageSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "signature": null,
      "changelogUrl": null,
      "declaredCapabilities": [],
      "rollback": null,
      "futureArtifactField": "keep-me"
    }
  ]
}
```

After round-trip encoding, `futureTopLevel` and `futureArtifactField` remain present.

Forward compatibility never permits ignoring a known security field. Known checksum, signature, package URL, capability, rollback, plugin ID, version, and schema-version fields must be parsed and validated according to the current contract.

## Repository transport requirements

Repository indexes and artifacts are fetched through the host-controlled network stack.

A production repository should use HTTPS for:

- index URL;
- package URLs;
- changelog URLs;
- rollback artifact URLs.

Redirects must remain subject to the host's network and declared-host policy. Authentication material, cookies, or private repository credentials must never be stored in the public index document.

## Complete minimal example

```json
{
  "schemaVersion": 1,
  "repositoryId": "community.main",
  "artifacts": [
    {
      "pluginId": "community.example",
      "version": "1.0.0",
      "packageUrl": "https://repo.example/community.example/1.0.0.osp",
      "exactPackageSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "signature": null,
      "changelogUrl": null,
      "declaredCapabilities": [],
      "rollback": null
    }
  ]
}
```

## Validation errors

| Error | Meaning |
|---|---|
| `UNSUPPORTED_SCHEMA_VERSION` | The index schema version is not supported |
| `IMMUTABLE_VERSION_CONFLICT` | One plugin/version pair points to conflicting immutable artifacts |

Additional transport, URL, checksum, signature, and manifest failures are reported by the host's package-download and package-validation layers.


## Security-field validation

Known security fields fail closed. Repository/plugin IDs and versions use canonical formats; artifact, changelog, and rollback URLs use HTTPS; checksums are lowercase SHA-256 values; an Ed25519 signature decodes to exactly 64 bytes; and rollback metadata must reference a strictly older, distinct artifact. Identical duplicate artifact records are rejected explicitly, while conflicting records for one plugin/version remain `IMMUTABLE_VERSION_CONFLICT`.
