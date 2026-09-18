# URI / File Input Threat Model — Phase 0

External document URIs, files, archives, publication packages, metadata and future provider payloads are untrusted input.

- Path, URI, filename, document ID and MediaStore row ID are evidence/locators, never canonical identity.
- Persisted URI text is not current access authority; Android grants must be checked/re-authorized according to the storage design.
- Future archive readers must reject absolute/path-traversal entries and bound entry count, declared sizes, decoded resources and work duration.
- Future parsers must support cancellation and typed failure; malformed input must not partially mutate canonical/user state.
- `MANAGE_EXTERNAL_STORAGE` and legacy broad external-storage permissions are outside the V1 baseline.
