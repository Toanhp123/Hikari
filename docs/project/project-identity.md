# Project Identity

Date: 2026-08-07
Status: **CANONICAL naming policy**

The repository intentionally uses two names for different ownership surfaces. They are
not aliases that should be mechanically replaced.

## Hikari

`Hikari` is the product and repository identity:

- Android application display name;
- repository name;
- root Gradle project name.

The current source keeps `rootProject.name = "Hikari"` and the Android string resource
`app_name = Hikari`.

## OpenStory

`OpenStory` is the technical and ecosystem identity:

- Kotlin and Android namespace family `app.openstory`;
- Android application ID `app.openstory`;
- plugin API, package, and community repository terminology;
- host protocol and user-agent family, including `OpenStory/1.0`;
- app-owned database and internal technical type names where already established.

## Change policy

A product rename does not automatically authorize a package/application-ID migration,
plugin ecosystem rename, protocol change, database rename, or user-agent change. Each
surface has compatibility and persistence consequences and requires an explicit reviewed
decision.

Likewise, technical types using `OpenStory` do not change the user-facing application
name. Contributors preserve these roles unless a task explicitly changes the relevant
identity boundary.

Repository cleanup, module moves, and verification-script renames do not authorize an
identity change. IDE metadata is intentionally excluded from the repository and is not a
source of naming or JDK policy.
