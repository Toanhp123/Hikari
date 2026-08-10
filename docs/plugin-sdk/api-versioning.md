# OpenStory Plugin Protocol Versioning

Every plugin manifest declares the protocol major used by `main.js`:

```json
{
  "protocol": 1
}
```

The current protocol is major `1`. The host accepts a package only when its declared
major is supported exactly. A different major is rejected before JavaScript execution;
the host does not infer compatibility or install an automatic adapter.

## When to change the major

Increment the protocol major for any incompatible wire change, including:

- renaming or removing an operation such as `catalog.search`;
- changing a required request or response field;
- changing validation, pagination, identifier, or error semantics incompatibly;
- changing the `host.http`, `host.html`, or `host.log` capability contract;
- requiring an existing package to rewrite `main.js`.

Backward-compatible optional fields can be added within the current major only when old
packages remain valid and the host gives the field a safe default. The tested Kotlin
serializers in `:plugins:api` are the source of truth; prose examples do not override them.

## Release checklist

Before publishing a protocol change:

1. Classify the wire change as compatible or incompatible.
2. Update the serializers and validation tests in `:plugins:api`.
3. Update the MyAnimeList reference manifest and package when the contract changes.
4. Run `./gradlew :plugins:api:test :plugins:runtime:testDebugUnitTest --stacktrace`.
5. Update these SDK pages from the tested wire contract.
