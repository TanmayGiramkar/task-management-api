# 0004. Authentication: JWT access tokens, rotating refresh tokens, per-user tasks, rate limiting

- **Status:** Accepted
- **Date:** 2026-11-09
- **Stage:** 4 (Security)

## Context

Every endpoint was public and every client saw every task. Stage 4 requires accounts with BCrypt
passwords, JWT access tokens with refresh-token rotation and revocation, per-user tasks (404, not 403, for
another user's task), and rate limiting on the auth endpoints. One service both issues and checks tokens.
Project 2 will later put a gateway in front that validates JWTs at the edge.

## Decision

**Accounts**
- `users` table (V3). Emails are stored trimmed and lower-cased with a UNIQUE constraint; registering a taken
  email returns 409. The unique constraint also covers two simultaneous registrations.
- Passwords are hashed with `BCryptPasswordEncoder` (cost 10, salted per password). BCrypt only uses
  72 bytes and Spring's encoder rejects longer input, so passwords are limited to 8–72 characters and
  72 bytes in UTF-8 (a 400 field error, not a 500).
- Login uses Spring Security's `AuthenticationManager` with `DaoAuthenticationProvider`: wrong password and
  unknown email give the same 401, and the provider runs a BCrypt comparison even for unknown emails, so
  response time does not reveal which emails have accounts.

**Access tokens**
- JWT, HS256, valid for 15 minutes. Claims: `iss`, `sub` (the user id, which never changes, unlike the
  email), `iat`, `exp`. Issued with Spring Security's `NimbusJwtEncoder`, validated by the OAuth2 Resource
  Server `NimbusJwtDecoder` (signature, `exp`/`nbf` with 60 s clock skew, issuer). No third-party JWT library.
- The decoder accepts only HS256 with our key, so `alg: none` and other algorithms are rejected.
- The key comes from `JWT_SECRET` in `.env`: Base64, at least 32 bytes. The app refuses to start without it.
  Tests generate a random key on every run, so no key is stored in the repository.
- Stateless: no server session, no cookies, so CSRF protection is off.

**Refresh tokens** (V4)
- 32 random bytes from `SecureRandom`, Base64URL. Opaque, not a JWT: the server must look it up to check
  revocation anyway, so it does not need to carry claims.
- Stored only as a SHA-256 hash. A fast hash is enough because 256 random bits cannot be guessed (passwords
  are guessable, which is why they need slow BCrypt), and a deterministic hash can be looked up directly.
- Valid for 7 days. `POST /auth/refresh` revokes the presented token and issues a new one in the same
  *family* (rotation). Presenting a token that is already revoked means it was used twice, so one copy may be
  stolen: the whole family is revoked and both the attacker and the real user must log in again
  (OAuth 2.0 Security Best Current Practice). Other logins (other families) are unaffected.
- The token row is locked during rotation (`SELECT ... FOR UPDATE`), so two parallel refreshes with the same
  token run one after the other and only one succeeds.
- Family revocation must be saved even though the request then fails, so the method uses
  `@Transactional(noRollbackFor = InvalidRefreshTokenException.class)`.
- `POST /auth/logout` revokes the refresh token and always returns 204.

**Per-user tasks** (V5)
- `tasks.owner_id` references `users`. The controller passes the user id from the JWT to `TaskService`, which
  only reads tasks through owner-filtered queries (`findByIdAndOwnerId`, `ownedBy` Specification). Another
  user's task therefore gets exactly the same 404 as a missing one, so task ids cannot be probed.
- The column is nullable because tasks created before Stage 4 have no owner. They stay in the database but
  are invisible until claimed; a later migration adds NOT NULL.
- No roles yet. Team roles (owner/admin/member) and role-based authorization come in Stage 5.

**Rate limiting**
- Bucket4j token bucket per (endpoint, client IP): login 5, register 3, refresh 10 requests per minute,
  configurable. Over the limit: 429 Problem Details with `Retry-After`.
- Buckets are kept in a Caffeine cache that forgets idle clients after 10 minutes and holds at most 100,000,
  so memory stays bounded.
- The filter runs inside the Spring Security chain, created in `SecurityConfig` rather than as a bean, so
  Spring Boot does not also register it as a plain servlet filter.

## Alternatives considered

| Alternative | Why not |
|---|---|
| **Server-side sessions** (JSESSIONID cookie) | Simple, and logout is instant. But it needs session storage shared by every instance, brings CSRF back, and Project 2's gateway needs self-contained tokens it can check without calling this service. |
| **RS256 (key pair) instead of HS256** | Lets other services verify tokens with only the public key; that is what Project 2's gateway will want. Today one service signs and verifies, so a single shared secret is simpler. Switching later changes only `SecurityConfig`. |
| **jjwt or another JWT library** | Spring Security already ships a JWT encoder and decoder (Nimbus) and the Resource Server filter that uses them. |
| **Spring Authorization Server** | A full OAuth 2.1 server (clients, consent, PKCE). Far more than an API with its own login needs. |
| **Refresh tokens as JWTs** | Would still need a database lookup to support revocation, so the self-contained format gains nothing. |
| **Long-lived access tokens, no refresh tokens** | A stolen token stays usable for its whole life, and logout cannot end it. |
| **A token deny-list to revoke access tokens instantly** | Needs a lookup on every request, which is what JWTs avoid. 15-minute expiry limits the exposure instead. |
| **Revoke all of the user's tokens on reuse** | Simpler than families, but logs the user out on every device. |
| **Return 403 for another user's task** | Tells the caller the task exists. 404 reveals nothing. |
| **Rate limiting with a hand-written counter** | Easy to get wrong (window edges, concurrency). Bucket4j is the standard token-bucket library for Java. |
| **Rate limiting in Redis now** | Needed only with several instances. Stage 6 introduces Redis; Bucket4j can move to it then. |

## Consequences

- `JWT_SECRET` must be set in `.env` before the app starts.
- Logout ends the refresh token immediately, but an access token keeps working until it expires
  (at most 15 minutes). Clients must discard it.
- Revoked and expired refresh tokens stay in the table. A scheduled clean-up can be added when the table grows.
- Rate limits are per instance and keyed by `request.getRemoteAddr()`. Behind a reverse proxy (Stage 8) the
  real client IP must come from forwarded headers (`server.forward-headers-strategy`), or every client
  would share the proxy's limit.
- `tasks.owner_id` has no index yet; Stage 6 adds indexes justified with `EXPLAIN ANALYZE`.
- The local database's tasks (IDs 10–15) have no owner until the owner assigns them with one `UPDATE`.
