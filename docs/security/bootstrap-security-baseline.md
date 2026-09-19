# Bootstrap Security Baseline

## URI and file input

External files, document URIs, archives, publication packages, metadata and future provider payloads are untrusted input.

- Never derive canonical identity directly from a path, URI, filename or provider row ID.
- Persist only grants explicitly acquired through Android APIs; a stored URI is never proof that current access still exists.
- Reject archive entries that normalize outside the intended extraction/read namespace (for example `../` traversal or absolute paths).
- Bound archive entry count, declared sizes and decoded image/resource work before future readers ingest untrusted media.
- Do not grant URI permissions more broadly or longer than the concrete operation requires.
- Broad storage permissions (`MANAGE_EXTERNAL_STORAGE`, legacy read/write external storage) are outside the V1 baseline.

## Exported Android components

The launcher activity is the only exported component in the bootstrap manifest. Every future Activity, Service, Receiver and Provider must declare `android:exported` explicitly and default to `false` unless an external contract requires exposure. Any exported component requires an intent/input threat review before merge.

The first local playback slice declares only `FOREGROUND_SERVICE` and
`FOREGROUND_SERVICE_MEDIA_PLAYBACK` for its internal Media3 service, with service
type `mediaPlayback`. It adds no network or broad storage permission. Explicit
same-app controllers and the app-supplied progress sink preserve the playback
boundary; runtime URI data is not persisted as progress.

## Network and cleartext

Cleartext traffic is disabled at application level. Future provider networking must use HTTPS by default and must not relax the global policy to accommodate one endpoint.

## Logging and secrets

- Never log auth tokens, cookies, passwords, encryption keys, full user paths, document URIs or signed/expiring media URLs.
- Keep credentials out of Gradle scripts, source control and build artifacts.
- Local developer secrets belong in ignored local files/environment; future production secrets use an explicit secret-storage design.
- Diagnostic IDs should prefer app-owned opaque IDs over external locators.

## Backup

Platform backup is disabled during bootstrap. Q-BACK-001 defines the product backup contract as an explicit versioned logical export; Android Auto Backup/D2D inclusion remains deferred until an explicit size/security matrix is approved.
