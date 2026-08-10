# Plugin Contract Testing

The `:plugins:api` serializers and validators define the wire contract. Package authors
should validate JSON examples against those types instead of copying Kotlin interfaces.

## Repository checks

Run the current SDK contract check and API tests:

```bash
bash scripts/tests/plugin-sdk-current-contract-test.sh
./gradlew :plugins:api:test --stacktrace
```

Runtime and package-install changes also run:

```bash
./gradlew :plugins:runtime:testDebugUnitTest --stacktrace
```

## Reference fixture

The canonical manifest fixture is
`plugins/api/src/test/resources/reference-plugins/myanimelist/manifest.json`. The bundled
counterpart is `bundled-plugins/myanimelist-catalog`, whose `main.js` implements
`catalog.home`, `catalog.search`, `catalog.details`, and `catalog.filters` through the same
protocol and `host.http` capability available to third-party packages.

A contract test should cover:

1. strict manifest decoding and protocol-major compatibility;
2. every declared operation's request and response JSON;
3. invalid identifiers, URLs, pagination tokens, collection sizes, and remote hosts;
4. package layout plus detached lowercase SHA-256 verification;
5. capability denial and safe error codes;
6. cancellation and execution-budget behavior for runtime integrations.
