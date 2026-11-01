# Device unlock (WebAuthn)

**Status:** Locked in [`PRODUCT.md`](PRODUCT.md) §3 (2026-11-01). README To Do **#3** (passphrase usability) is this work.

This file is the decided implementation plan for making ledger unlock usable without treating the Keycloak sign-in password as the encryption secret.

## What this is (and is not)

Sign-in and unlock stay **two different credentials**. Sign-in is Keycloak (OIDC). Unlock unwraps the household DEK in the browser so envelope, asset, and report ciphertext can be read.

Some users do not see that difference and do not want to type a second secret after login. **Device unlock** is the answer: after one successful passphrase unlock, they may register **this browser/device**. Later visits on that device unlock with the platform authenticator (Touch ID, Face ID, Windows Hello). The passphrase and recovery file remain the fallback on every machine.

This is **not**:

- Using the Keycloak login password to unwrap the DEK (the app never receives that password after OIDC redirect)
- Synced cloud passkeys in v1 (iCloud / Google Password Manager would let that cloud account unlock the ledger)
- Replacing first-time key setup (passphrase + recovery file stay mandatory)
- Admin-visible decryption, or a server-side way to unlock a user’s ledger

```text
Passphrase wrap ──┐
Recovery wrap  ───┼── same household DEK ── ledger / assets ciphertext
Device wrap(s) ───┘     one wrap per registered authenticator
```

Each household member has **their own** device list. Devices are per user, not household-wide.

## Why not “use my login password”

After login, a-cruet has a session cookie, not the Keycloak password. There is nothing to derive a KEK from.

Binding the two would also be a product trap: admin **Reset sign-in password** already exists and **does not** reset the encryption passphrase. Users who think the secrets are the same would get a new login password and still be locked out of their envelopes.

The profile checkbox idea is the right **UX**. The backing secret is a **device wrap**, not the login password.

## Decisions

| Item | Decision |
|------|----------|
| Authenticator | **Platform authenticator only** (this device). No roaming / synced passkeys in v1 |
| Crypto binding | WebAuthn **PRF** (`prf` / hmac-secret) derives a wrap key; unwrap the same DEK already wrapped by passphrase and recovery |
| Server stores | Credential id, wrapped DEK, algorithm, user-chosen display name, created/last-used timestamps — **ciphertext + operational metadata**, never a usable DEK |
| Fallback | Passphrase on every device; **Forgot passphrase?** + recovery file still works when no device is at hand |
| Reset after device unlock | **Yes** — an unlocked session (device or passphrase) can set a **new** passphrase and download a new recovery file **without** the old passphrase. Same DEK re-wrap as rotate; device wraps stay valid |
| First setup | Unchanged — passphrase + recovery file before any ledger feature |
| Adding a device | Must already be unlocked (passphrase or an existing device) on **this** browser, then register |
| Removing a device | Deletes that wrap only. Other devices, passphrase, and recovery file still work |
| Passphrase rotate / forgot-passphrase | Re-wraps passphrase + recovery only. **Device wraps stay valid** (same DEK). Rotate while unlocked does **not** ask for the old passphrase; `/keys/forgot-passphrase` still accepts the recovery file for users who have no device |
| Sign out / Lock | Clears the in-tab session (`sessionStorage` DEK). Does **not** unregister the device |
| Cap | **10** registered devices per user |
| Idle timeout | Unchanged for v1 (30 minutes in-tab). Shared-tab session and “until sign-out” are later, not this feature |
| Origin | User app only (`acruet.home.bradandmarsha.com`). Admin host does not unlock ledgers |
| Unsupported browser | Hide “Add this device” / device-primary unlock; passphrase form only |
| Admin | No device list or count on `/users` in v1 |

Key loss becomes: unrecoverable if the user loses **passphrase**, **recovery file**, and **every registered device**. Admins still cannot decrypt.

## Data model

New Flyway migration (V13), user-scoped. Do **not** add columns onto `user_encryption_key` for each device — one row per credential.

- `user_unlock_device` — `id`, `user_id`, `credential_id` (unique), `wrapped_dek`, `wrap_algorithm`, `display_name`, `created_at`, `last_used_at`
- Foreign key to `acruet_user` with `ON DELETE CASCADE`
- Unique `(user_id, credential_id)`

Display name is plaintext operational metadata (same class as email): “Kitchen iPad”, not a secret. Default to a short platform hint if the user does not rename it.

## API (user WAR)

Path `/keys/devices` (signed-in + key-setup complete):

- `GET /keys/devices` — list this user’s devices (no wrapped DEK in the list payload)
- `POST /keys/devices/attestation-options` — WebAuthn create-challenge
- `POST /keys/devices` — finish registration `{ credentialId, clientData, attestation, wrappedDek, displayName }`
- `POST /keys/devices/assertion-options` — WebAuthn get-challenge (allowCredentials = this user’s ids)
- `POST /keys/devices/unlock` — finish assertion; returns that credential’s `wrappedDek` + PRF inputs the client needs to unwrap
- `DELETE /keys/devices/{id}` — remove this device
- `PUT /keys/devices/{id}` — rename only

Existing `/keys/wrapped-dek`, `/keys/unlock`, rotate, and forgot-passphrase stay. Device registration never sends a passphrase to the server.

Reuse session + key-setup gates and per-user write rate limits.

## UI

### Unlock (envelopes, assets, reports, `/keys/unlock`)

Do **not** show Touch ID, Face ID, and Windows Hello as three buttons. A device only has one platform authenticator.

**This device is registered**

1. Primary: **Unlock with Touch ID** (or Face ID / Windows Hello / **Unlock with this device** when the platform cannot be named).
2. OS prompt; success unwraps the DEK into the existing session.
3. Secondary: **Use passphrase instead**.
4. **Forgot passphrase?** unchanged.

**This device is not registered**

Today’s passphrase form. After a successful unlock, one offer: **Also unlock on this device next time.** Decline keeps passphrase-only here.

### Manage devices (`/profile`)

Roster, not biometric brands:

- Kitchen iPad — added … — **Remove**
- Brad’s MacBook — added … — **Remove**
- **Add this device** — hidden if already registered or PRF/platform is unavailable; requires an unlocked session

Copy on the toggle/section: registering a device means anyone who can unlock **this** hardware and is signed in as you can read the ledger. Remove a lost phone from this list.

Bump `UserNav.STATIC_ASSET_VERSION` when JS ships.

## Rollout

Three releases. Verify each with `docker build --target build`. Brad owns commit/PR unless asked that turn. Do not mark README To Do **#3** done until release 3.

### Release 1 — Schema + API

- Flyway `V13__unlock_devices.sql` and schema version bump
- Repository + `KeyResource` device endpoints
- Registration/assertion option builders (origin / RP id = user hostname)
- No UI change; passphrase unlock unchanged

**Verify:** unit tests for cap (10), cascade delete, unique credential id; package succeeds. ✅ (2026-11-01)

### Release 2 — Profile roster + WebAuthn client

- `acruet-crypto.js`: PRF wrap/unwrap helpers next to passphrase and recovery wraps ✅ (2026-11-01)
- Profile section: list, rename, remove, add this device (unlocked session required) ✅
- Hide add when the browser lacks platform + PRF ✅
- Cache-bust ✅

**Verify (after deploy):** add this browser, reload profile and see it, remove it, passphrase still unlocks. Second browser is not listed until added there.

### Release 3 — Unlock surfaces + docs

- Device-primary button on `/envelopes`, `/assets`, `/reports`, `/keys/unlock` when this credential exists ✅ (2026-11-01)
- Post-passphrase “Also unlock on this device next time” ✅
- **Change passphrase while unlocked** — `/keys/rotate` omits the current-passphrase field when the DEK is already in session (device or passphrase). Issue a new recovery file. `/keys/forgot-passphrase` stays as the no-device path (upload recovery file) ✅
- Marketing / key-setup hint: sign-in password ≠ ledger passphrase; device unlock is optional; a registered device can also reset a forgotten passphrase ✅
- Point [`PRODUCT.md`](PRODUCT.md) §3 at this file and lock the table above ✅
- Mark README To Do **#3** done ✅
- Leave tab-shared session and “until sign-out” idle **off** this item ✅

**Verify (after deploy):** registered Mac unlocks with platform prompt; passphrase fallback works; new browser asks for passphrase then can add; remove device and that browser falls back to passphrase; rotate passphrase and device unlock still works; **forget the passphrase, unlock with the Mac, set a new passphrase, iPhone still unlocks.**

## Out of scope

- Binding unlock to the Keycloak sign-in password
- Synced / roaming passkeys
- Cross-device QR “use phone to unlock laptop” in v1
- Changing the 30-minute `sessionStorage` idle timeout or sharing the DEK across tabs
- Device list or counts on admin `/users`
- Requiring a device (passphrase-only users stay valid)
- Hardware security keys as a first-class v1 path (platform authenticators only)
