# Exported Components Baseline — Phase 0

The launcher `MainActivity` is exported because Android launchers must invoke it. It is the only exported component in the bootstrap manifest.

Every future Activity, Service, Receiver or Provider must declare `android:exported` explicitly. Default is `false`; changing it to `true` requires documenting the external caller contract, accepted inputs, permissions and abuse/failure behavior before merge.
