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

## Bundled content plugin

`bundled-plugins/mangadex-content` is the official bundled package exercising the Wave 06
`CONTENT` contract. It implements `content.search` and `content.resolveUrl` against MangaDex.
The build packages it into `app/src/main/assets/plugins/mangadex-content.osp`; production startup
provisions it from the same app-owned descriptor registry as the MyAnimeList catalog package.
Android instrumentation validates and executes that production artifact through the installer,
Room state store, transactional package storage, and Hilt-provided JavaScript/HTTP runtime.

The production bundle is a list, not a singleton. Every bundled `.osp` must be pinned by plugin
ID, version, asset path, and detached SHA-256 in the app descriptor registry. Additional catalog
or content packages may join that list without changing the generic runtime contract.

Run the deterministic fixture-backed contract test with:

```text
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.MangaDexContentContractIntegrationTest
```

The live test is opt-in because it depends on the external MangaDex service. It verifies real
search and URL resolution, then persists an automated mapping, promotes it to a protected user-URL
mapping, and confirms a later automation pass cannot overwrite that protected choice:

```text
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.MangaDexLiveContentIntegrationTest \
  -Pandroid.testInstrumentationRunnerArguments.openstoryLiveMangaDex=true
```
