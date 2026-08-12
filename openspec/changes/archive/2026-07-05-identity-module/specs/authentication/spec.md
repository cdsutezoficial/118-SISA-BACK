# Authentication Specification

## Purpose

Local-credential login issuing JWT access + refresh tokens, mandatory
first-access password change, and refresh-token exchange. LlaveMX OAuth login
and password-reset-by-email are out of scope for this slice (stub port only).

## Requirements

### Requirement: Authenticate with Local Credentials

The system MUST validate `username` + `password` against the stored `User`
and Bcrypt hash. On success, it MUST issue an access token (30-minute TTL,
claims `sub` = userId and `roles` = the user's `RoleType` names only) and a
refresh token (1-day TTL, persisted as a hash, never the plain value). On
failure, it MUST increment `failedLoginAttempts`; reaching 3 MUST transition
`status` to `LOCKED` automatically. A `LOCKED` account MUST be rejected even
with the correct password.

#### Scenario: Valid credentials issue both tokens
- GIVEN an `ACTIVE` `User` with correct credentials
- WHEN `AuthenticateUseCase` is invoked
- THEN an access token (30 min, `sub`+`roles` claims) and a refresh token (1 day, hash-stored) are returned

#### Scenario: Invalid password increments failed attempts
- GIVEN an `ACTIVE` `User` with `failedLoginAttempts = 0`
- WHEN authentication is attempted with a wrong password
- THEN the attempt is rejected and `failedLoginAttempts` becomes 1

#### Scenario: Third consecutive failure locks the account
- GIVEN a `User` with `failedLoginAttempts = 2`
- WHEN authentication is attempted with a wrong password again
- THEN the attempt is rejected and `status` becomes `LOCKED`

#### Scenario: Locked account rejects even correct credentials
- GIVEN a `User` with `status = LOCKED`
- WHEN authentication is attempted with the correct password
- THEN the attempt is rejected and no tokens are issued

#### Scenario: First-access login still issues tokens
- GIVEN a `User` with `mustChangePassword = true` and correct credentials
- WHEN `AuthenticateUseCase` is invoked
- THEN tokens are issued normally, so the user can call `ChangePasswordUseCase` next

### Requirement: Mandatory First-Access Password Change

While a `User` has `mustChangePassword = true`, the system MUST reject every
operation for that user except `ChangePasswordUseCase`. On a successful
password change, the system MUST set `mustChangePassword = false`, after
which normal operations resume.

#### Scenario: Blocked operation while change is pending
- GIVEN a `User` with `mustChangePassword = true`
- WHEN that user calls any use case other than `ChangePasswordUseCase`
- THEN the operation is rejected

#### Scenario: Successful change lifts the block
- GIVEN a `User` with `mustChangePassword = true`
- WHEN `ChangePasswordUseCase` succeeds with a new password
- THEN `mustChangePassword` becomes `false` and subsequent operations succeed normally

### Requirement: Exchange Refresh Token for New Access Token

The system MUST accept a refresh token and, if it is valid (matches a
non-expired, non-revoked stored hash) AND the owning `User`'s current status
is not `LOCKED`, issue a new access token (30 min, same `sub`+`roles` claim
shape) without requiring credentials again. An expired, revoked, or
unrecognized refresh token MUST be rejected, requiring the user to
re-authenticate with credentials. A refresh token belonging to a `User` whose
status became `LOCKED` after the token was issued MUST also be rejected, even
though the token itself is still within its TTL and was never revoked —
`LOCKED` takes effect immediately for already-issued refresh tokens, not just
for future login attempts. `mustChangePassword = true` does NOT block refresh
(mirrors the login exception, so the user can still reach
`ChangePasswordUseCase` after refreshing).

#### Scenario: Valid refresh token yields a new access token
- GIVEN a refresh token issued at login that is not expired or revoked, and the owning `User` is `ACTIVE`
- WHEN it is presented to the refresh exchange
- THEN a new access token is returned without asking for credentials

#### Scenario: Expired refresh token is rejected
- GIVEN a refresh token whose 1-day TTL has elapsed
- WHEN it is presented to the refresh exchange
- THEN the request is rejected and the user must re-authenticate

#### Scenario: Revoked or unknown refresh token is rejected
- GIVEN a refresh token that was explicitly revoked, or one that does not match any stored hash
- WHEN it is presented to the refresh exchange
- THEN the request is rejected and the user must re-authenticate

#### Scenario: Refresh token of a since-LOCKED user is rejected
- GIVEN a refresh token issued while the `User` was `ACTIVE`, not expired or revoked
- AND the `User`'s status has since become `LOCKED` (e.g. 3 failed login attempts on another device)
- WHEN the refresh token is presented to the refresh exchange
- THEN the request is rejected, even though the token itself is still valid

#### Scenario: Refresh token of a mustChangePassword user still succeeds
- GIVEN a refresh token issued for a `User` with `mustChangePassword = true`
- WHEN it is presented to the refresh exchange
- THEN a new access token is returned normally, so the user can still reach `ChangePasswordUseCase`
