# OpenStory Plugin API Versioning Policy

Every plugin manifest declares the host contract it targets:

```json
{
  "api": {
    "major": 1,
    "minor": 3
  }
}
```

OpenStory uses a two-part plugin API version:

```text
major.minor
```

There is no patch component in the plugin API compatibility contract.

## Compatibility rule

A plugin API version is supported by a host only when:

```text
plugin.major == host.major
plugin.minor <= host.minor
```

The host rejects the plugin before runtime initialization when either condition is not satisfied.

| Plugin API | Host API | Result |
|---|---|---|
| `1.3` | `1.5` | Supported |
| `1.5` | `1.5` | Supported |
| `1.6` | `1.5` | Rejected: host minor is too old |
| `2.0` | `1.5` | Rejected: major versions differ |
| `1.5` | `2.0` | Rejected: major versions differ |

A newer host minor remains compatible with plugins targeting an older minor of the same major.

A plugin must not assume APIs or behavior introduced after the minor version declared in its manifest.

## Major-version changes

Increment the major version for an incompatible contract change, including:

- removing or renaming a public plugin method;
- removing or renaming a required serialized field;
- changing the meaning of an existing field or method;
- changing an accepted value into an invalid value;
- introducing a new required method, field, capability, or behavior;
- changing identifiers, pagination, synchronization, or error semantics incompatibly.

A host does not load a plugin targeting a different API major.

Migration between major versions requires an explicit adapter, plugin update, or separate compatibility layer. Compatibility must not be inferred automatically.

## Minor-version changes

Increment the minor version only for backward-compatible additions, including:

- adding an optional serialized field with a safe default;
- adding an optional capability;
- adding an enum value that older hosts are allowed to ignore or reject safely;
- adding an API that existing plugins are not required to implement;
- clarifying behavior without changing the existing contract.

A minor release must preserve the behavior and serialized forms required by every earlier minor within the same major.

A change that requires all existing plugins to be modified is not a minor change.

## Plugin author requirements

A plugin author must:

1. Declare the lowest API minor required by the plugin.
2. Increment the declared minor when using a newer compatible API feature.
3. Increment the major when adopting an incompatible contract generation.
4. Avoid declaring a newer minor when the plugin does not use it.
5. Test the plugin against the contract suite for its declared API version.

## Host requirements

Before runtime initialization, the host must:

1. Parse the manifest API version.
2. Require an exact major-version match.
3. Reject a plugin minor newer than the host minor.
4. Accept an older or equal plugin minor within the same major.
5. Validate the remaining manifest, package, permissions, and runtime contract.
6. Report the plugin and host API versions when compatibility validation fails.

## Examples

For a host implementing API `1.5`:

```text
plugin 1.0 -> accepted
plugin 1.3 -> accepted
plugin 1.5 -> accepted
plugin 1.6 -> rejected
plugin 2.0 -> rejected
```

For a host implementing API `2.0`:

```text
plugin 1.5 -> rejected
plugin 2.0 -> accepted
plugin 2.1 -> rejected
```

## Release checklist

Before publishing a plugin API change:

- classify the change as compatible or incompatible;
- verify that minor additions preserve all earlier contracts;
- increment the appropriate version component;
- update contract fixtures and compatibility tests;
- update manifest and package examples;
- document migration requirements for a major-version change.
