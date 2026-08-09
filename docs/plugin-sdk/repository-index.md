# OpenStory Plugin Repository Index

A repository publishes a UTF-8 JSON `RepositoryIndex` containing schema version `1` and a
bounded list of detached `PluginArtifact` records.

Each artifact record contains:

- the lowercase reverse-domain plugin ID;
- the semantic plugin version;
- an HTTPS package download URL;
- the lowercase SHA-256 of the exact `.osp` bytes;
- an optional non-blank detached Ed25519 signature.

The pair `(pluginId, version)` is unique within an index. Installers hash the exact
downloaded or imported bytes before extraction and compare that digest with trusted
detached provenance. Package contents cannot replace or weaken this check.

Repository transport, index provenance, signing-key trust, and package-byte verification
are host responsibilities. Plugin JavaScript never receives signature keys, raw package
paths, or managed credentials.
