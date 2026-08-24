# Catalog Protocol

This document defines the stable host/plugin contract for catalog providers. Plugins provide bounded facts; the host owns canonical identity and presentation policy.

## `catalog.home` and `catalog.search`

Catalog listing operations require `sourceId`, `title`, and `contentType`. Presentation metadata such as artwork, authors, score, genres, popularity, publication status, and latest-update information is optional. A valid listing payload may omit any optional field.

The host renders missing optional listing metadata as a degraded/placeholder state. It does **not** call `catalog.details` merely to fill optional listing fields.

## `catalog.details`

A successful details response represents the provider's **Full metadata level** for that source. Full is a lifecycle level, not a promise that every optional field is populated. Missing optional fields remain valid provider output and do not trigger hidden provider hopping or host-side self-healing.

## Latest-update labels

`latestUpdate.releaseLabel` is optional opaque presentation text supplied by the provider. When present, it is already the complete label the provider intends the user to see. Examples include:

- `56`
- `Ch. 56`
- `Vol. 4 Ch. 56`
- another bounded provider-formatted label

The host must not prepend `Ch. `, parse numeric chapter identity from the label, or combine it with a timestamp from another source. Ordinary UI bounds and ellipsis are allowed.

## External identifiers

Catalog listing and details payloads may include up to 32 `externalIdentifiers`. Identifiers are optional bounded facts; their absence is ordinary missing evidence.

Each identifier contains a stable `namespace`, `value`, and one host-defined scope:

- `WORK` — work-level identity evidence. A matching value can become strong reconciliation evidence, but it never overrides incompatible medium/lineage or contradictory strong identity evidence.
- `PUBLICATION` — publication-level identity evidence.
- `EDITION` — edition/re-release identity evidence.
- `PROVIDER_RECORD` — provider-record identity. It is useful for direct source/provenance reasoning and is not, by itself, cross-provider work proof.

Plugins must not attach confidence, trust, quality, priority, or weighting values to identifiers. The host owns all reconciliation and fusion policy.

## Scores

A catalog score is a bounded `(value, scale)` pair. Both numbers must be finite, `scale` must be positive, and `value` must be within `0..scale`. Providers report their native scale; they do not report host trust or weighting. Host policy may normalize a score as `value / scale` when a canonical comparison or fusion rule explicitly requires it.
