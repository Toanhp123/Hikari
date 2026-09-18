# Logging and Secrets Policy — Phase 0

Production diagnostics must not contain credentials, auth tokens, cookies, passwords, encryption keys, full filesystem paths, full document URIs, or signed/expiring content URLs.

Prefer app-owned opaque IDs and typed failure categories in diagnostics. Developer-only secrets belong in ignored local files or environment variables. No signing key or production credential may be committed to source control or embedded in Gradle configuration.
