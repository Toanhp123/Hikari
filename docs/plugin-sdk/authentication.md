# Plugin Authentication

Authentication is an optional host-owned capability. Existing manifests that omit
`capabilities.authentication` remain valid and are treated as having no login session.

```json
{
  "capabilities": {
    "network": {
      "hosts": ["api.example.org"]
    },
    "authentication": {
      "loginStartUrl": "https://accounts.example.org/login",
      "navigationHosts": ["accounts.example.org", "login.example.org"],
      "completion": {
        "host": "accounts.example.org",
        "pathPrefix": "/complete"
      },
      "credentialTargets": [
        {
          "host": "api.example.org",
          "pathPrefix": "/v1",
          "cookieNames": ["refresh", "session"]
        }
      ],
      "sessionTtlSeconds": 86400
    }
  }
}
```

The host enforces these rules before opening a login surface:

- `loginStartUrl` uses HTTPS and contains no user information, custom port, or
  fragment, and use a normalized lowercase hostname.
- `completion` declares a normalized navigation host and a non-blank absolute path without repeated
  slashes or dot segments.
- `navigationHosts` is non-empty, contains unique exact lowercase hostnames, and contains both the
  login and completion host. Schemes, paths, ports, whitespace, and wildcards are invalid.
- `credentialTargets` is non-empty. Each `(host, pathPrefix)` pair is unique; each host belongs to
  `capabilities.network.hosts`; and each path prefix is absolute and normalized.
- Each credential target declares a non-empty, duplicate-free cookie-name allowlist. Cookie names
  use RFC token characters only.
- `sessionTtlSeconds` is a long from `60` through `2592000` inclusive.

The runtime validates every HTTPS request and redirect before asking a managed credential provider
for headers. A session may contribute only its host/path-scoped `Cookie` header. Plugin JavaScript
never receives cookies, never supplies protected `Authorization`, `Cookie`, or
`Proxy-Authorization` headers, and cannot replace a host-owned header. If two host providers claim
the same header name case-insensitively, the request fails closed.

Authentication policy fingerprinting sorts navigation hosts, credential targets, and cookie names
before hashing. Changing any login, completion, host, path, cookie-name, or TTL rule invalidates the
old session; cookie values are never part of diagnostic or fingerprint input.
