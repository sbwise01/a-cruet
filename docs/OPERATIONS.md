# a-cruet operations — Keycloak admin console

**Status:** Live runbook (2026-11-01). These steps are **not** in GitOps. The `KeycloakOIDCClient` CRs in `wise-k8s` (`acruet`, `acruet-admin`) cannot assign `realm-management` client roles, dedicated-scope mappings, or post-logout host roots. The Keycloak operator treats each CR as desired state and **drops console-only settings** when it re-applies the client.

Console: [https://auth.home.bradandmarsha.com](https://auth.home.bradandmarsha.com) → realm **`wise-k8s`**.

CRs:

- `wise-k8s/iac/kustomize/keycloak/base/oidc-client-acruet.yaml`
- `wise-k8s/iac/kustomize/keycloak/base/oidc-client-acruet-admin.yaml`

Tracked as a GitOps gap in `wise-k8s` README To Do (Keycloak a-cruet manual client config) and [`KEYCLOAK.md`](../../wise-k8s/docs/KEYCLOAK.md) Phase 5.1.

---

## When to run these steps

| Trigger | What to redo |
|---------|----------------|
| First cluster / new realm | All sections below |
| **Keycloak operator re-applies a `KeycloakOIDCClient` CR** | The matching client section — see [After the operator re-applies a CR](#after-the-operator-re-applies-a-cr) |
| You edit or `kubectl apply` either OIDC client CR | Same — Flux + operator will reconcile; then redo that client’s console steps |
| Admin `/users` shows *Could not read Keycloak administrator roles* | [Service account `acruet-admin`](#service-account-acruet-admin) |
| Approve signup or Grant/Revoke admin returns 403 | [Service account `acruet-admin`](#service-account-acruet-admin) |
| Admin host login works but the user has no `a-cruet-admin` in the token | [OIDC client `acruet`](#oidc-client-acruet) dedicated scope |
| Logout cannot return to the app host root | [OIDC client `acruet`](#oidc-client-acruet) post-logout URIs |

Last known wipe: **2026-09-06** — operator recreated the Keycloak and operator pods and re-applied `acruet-admin`. Service-account `realm-management` roles vanished. Restored **2026-10-31**.

---

## After the operator re-applies a CR

Do this **every time** the Keycloak operator reconciles `acruet` or `acruet-admin`, including:

- Any commit that changes those YAML files
- `flux reconcile kustomization keycloak -n flux-system`
- Operator or `keycloak-0` pod recreation
- Keycloak version bump

The CR does not list console mappings, so a successful reconcile is not “config is still correct.”

1. Identify which client was applied (`kubectl -n keycloak get keycloakoidcclient` and `managedFields` / `status` timestamps).
2. If **`acruet-admin`** was applied → run [Service account `acruet-admin`](#service-account-acruet-admin) in full (service-account roles **and** dedicated scope).
3. If **`acruet`** was applied → run [OIDC client `acruet`](#oidc-client-acruet) (default `roles` scope, dedicated `a-cruet-admin` claim, post-logout URIs).
4. Confirm `acruet` namespace secret `acruet-admin-api` `client-secret` still matches the Keycloak `acruet-admin` client secret (same value as `keycloak` secret `acruet-admin-oidc-client`).
5. Verify:
   - [https://acruet-admin.home.bradandmarsha.com/users](https://acruet-admin.home.bradandmarsha.com/users) lists provisioned users **without** the Keycloak roles banner; Grant/Revoke is visible
   - Approve / Grant admin does not return 403
   - An `a-cruet-admin` user can still reach the admin host after login

Optional cluster check (after the console steps):

```bash
kubectl -n keycloak get keycloakoidcclient acruet acruet-admin \
  -o custom-columns=NAME:.metadata.name,GEN:.metadata.generation,OBSERVED:.status.observedGeneration
```

A new `generation` or a fresh operator `Apply` time on `managedFields` means redo the steps for that client.

---

## Service account `acruet-admin`

Needed so the admin app can call the Keycloak Admin API (list realm roles, grant/revoke `a-cruet-admin`, approve/provision, suspend, reset sign-in password).

Token endpoint can return **200** while Admin API returns **403** if these roles are missing from the **token**. Both layers below are required.

### Service account roles

1. **Clients** → **`acruet-admin`**
2. **Service account roles** → **Assign role** → filter by clients → **`realm-management`**
3. Assign **`manage-users`**, **`view-users`**, **`query-users`**, and **`view-realm`**
   - `view-realm` is required for Grant/Revoke (`GET /roles/a-cruet-admin`)

### Dedicated scope (roles must appear in the access token)

4. **Clients** → **`acruet-admin`** → **Client scopes** → open **`acruet-admin-dedicated`** → **Scope**
5. **Recommended (verified):** leave **Full scope allowed** **OFF** → **Assign role** → filter by clients → **`realm-management`** → assign the same four roles as step 3
6. **Homelab shortcut:** turn **Full scope allowed** **ON** on that dedicated scope instead of step 5

If Admin API is still 403 after steps 3–5, assign **`realm-admin`** from `realm-management` to the service account (broader fallback).

### Pre-flight (optional)

From a host that can reach auth; use the client secret from SOPS / the `acruet-admin-api` secret — do not commit it.

```bash
TOKEN=$(curl -s -X POST "https://auth.home.bradandmarsha.com/realms/wise-k8s/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=acruet-admin -d client_secret='…' \
  | jq -r .access_token)
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" \
  "https://auth.home.bradandmarsha.com/admin/realms/wise-k8s/users?email=test@example.com&exact=true"
# expect 200 (not 403)
```

---

## OIDC client `acruet`

User and admin Tomcat sign-in. Redirect URIs and web origins are in the CR; the following are console-only.

### Default scope `roles`

1. **Clients** → **`acruet`** → **Client scopes**
2. Ensure **`roles`** is a **Default** client scope (needed so realm roles appear on the token)

### Dedicated scope — `a-cruet-admin` on the access token

3. Open **`acruet-dedicated`** (or the dedicated scope row for `acruet`)
4. Map the realm role **`a-cruet-admin`** into the **access token** (admin WAR checks this role on every request)

### Valid post-logout redirect URIs

Keycloak `+` and the CR `redirectUris` (`/auth/callback` only) do **not** cover host roots.

5. **Clients** → **`acruet`** → **Valid post logout redirect URIs** — add both:

   - `https://acruet.home.bradandmarsha.com/`
   - `https://acruet-admin.home.bradandmarsha.com/`

---

## Realm role `a-cruet-admin`

1. Realm **`wise-k8s`** → **Realm roles** → create **`a-cruet-admin`** if it is missing (not a client role)
2. **First admin only:** assign that role to the bootstrap user in the console. Ongoing grant/revoke is the admin UI → Admin API (needs [Service account `acruet-admin`](#service-account-acruet-admin))

Do not use the Approve/provision path to create a Keycloak user that already exists (README To Do **#2**).

---

## Symptom → section

| Symptom | Likely missing |
|---------|----------------|
| `/users` lists people but hides Grant/Revoke and shows the Keycloak roles banner | `acruet-admin` service-account roles or dedicated-scope mappings |
| `/users` used to list people, then showed nobody (older app) or the banner (current app) | Same — operator re-applied `acruet-admin` |
| Approve creates no Keycloak user / 403 in admin logs | Same |
| Signed-in user cannot open the admin host (403) | Realm role not assigned, or `acruet` dedicated scope not putting `a-cruet-admin` on the token |
| Logout lands on Keycloak instead of the app | Post-logout host roots on `acruet` |
