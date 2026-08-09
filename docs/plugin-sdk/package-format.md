# OpenStory Plugin Package Format

An OpenStory plugin package uses the `.osp` extension and is a bounded ZIP archive with
this canonical layout:

```text
manifest.json
main.js
assets/<relative-file>
```

`assets/` is optional. Every archive entry must be a regular bounded file with a relative
normalized path. Installers reject absolute paths, parent traversal, links, duplicate
entries, unsupported compression behavior, oversized files, and oversized archives.

## Manifest

`manifest.json` is a UTF-8 serialized `PluginManifest`. It declares identity, semantic
version, protocol major, provided services, optional metadata, and the exact network hosts
available to the script. The only executable entry is `main.js`.

The manifest describes package behavior; it does not attest to the bytes of the archive
that contains it. Archive integrity and signatures are detached provenance supplied by an
installer or repository index.

## Script and assets

`main.js` communicates only through the versioned serialized plugin protocol and
host-controlled capabilities. Package files do not receive Android APIs, filesystem paths,
raw network clients, reflection, or managed credential values.

Assets are addressed relative to `assets/`. Their presence does not grant filesystem or
network access to plugin code.
