# Exported Components Baseline — Phase 0

The launcher `MainActivity` is exported because Android launchers must invoke it. It is the only exported component in the bootstrap manifest.

Every future Activity, Service, Receiver or Provider must declare `android:exported` explicitly. Default is `false`; changing it to `true` requires documenting the external caller contract, accepted inputs, permissions and abuse/failure behavior before merge.

Task 7 adds `PlaybackService` as a non-exported `mediaPlayback` foreground service.
Its only interface action is `androidx.media3.session.MediaSessionService`; app
controllers use an explicit component token. Session connections also require the
app UID. The private start command accepts one canonical target and an ephemeral
`content://` MP4 descriptor; generic playlist mutation commands are not granted.
There is no legacy browser action or media-button receiver. MainActivity remains
the only exported app-owned production component. Media3 1.11.1 contributes an
exported `BluetoothValidationActivity`; playback overrides it to non-exported
because this slice has no external Bluetooth/browser contract. Existing AndroidX
framework/debug components in the merged manifest are outside the source-manifest
verifier's count; that verifier must not be described as a complete merged-APK
export audit. Device/security acceptance is tracked in
the foundation Current Control Block.
