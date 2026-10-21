# Security notes (current state)

## Implemented
- **Passwords:** BCrypt cost 12. Inputs are limited to 72 bytes (BCrypt truncates silently beyond that) and rejected, not truncated.
- **JWT:** HS256-family, signed with `JWT_SECRET`. The app fails at startup if the secret is blank, under 32 bytes, or the `.env.example` placeholder. Invalid, tampered and expired tokens are rejected without exceptions leaking to the client.
- **Stateless sessions, CSRF disabled:** deliberate. Authentication is an `Authorization: Bearer` header that browsers never attach on their own, so there is no ambient credential for CSRF to abuse. If cookies are ever introduced this must be revisited.
- **Login:** unknown email and wrong password return the same error, and the unknown-email path still performs a BCrypt comparison to avoid a timing oracle.
- **Ownership:** projects are loaded only via `findByIdAndUserId`. Someone else's project and a missing project are both `404`, so ids cannot be probed.
- **Errors:** one error shape; unexpected exceptions return a generic 500 and are logged server-side only.
- **CORS:** explicit origin allow-list from `CORS_ORIGIN`, no credentials.
- **Outbound requests:** the GitHub connector builds URLs from a fixed base. Owner/repo names must match `[A-Za-z0-9_.-]{1,100}`. Search keywords have `:` and `"` stripped so user text cannot inject search qualifiers. The HTTP client only follows redirects to the same scheme, host and port, so an `Authorization` header never follows a redirect elsewhere.
- **Untrusted content:** everything returned by connectors (README text, descriptions) is treated as data. Text is truncated to a configured limit. `RepositoryInspection` is documented as untrusted and must only reach an LLM as quoted data.

## Known gaps (not yet built)
- **No rate limiting** on `/api/auth/*` yet (credential-stuffing risk). Add before any public deployment.
- Registration reveals whether an email is already registered (`409`). Standard trade-off; revisit if enumeration matters.
- JWTs cannot be revoked before expiry (default 120 minutes) and there is no refresh flow.
- No SSRF guard for arbitrary user-supplied URLs yet. It is required before the web connector exists; the GitHub connector does not accept user URLs.
- No prompt-injection pipeline yet (no LLM calls exist).
