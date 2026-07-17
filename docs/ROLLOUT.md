# a-cruet rollout plan

Deploy **a-cruet** on **wise-k8s** as an envelope-budgeting web application with **client-side encryption**, **Keycloak OIDC** authentication, and **admin-gated signup**.

Product decisions are locked in [`PRODUCT.md`](PRODUCT.md). Keycloak integration is documented in [`wise-k8s/KEYCLOAK.md`](../wise-k8s/KEYCLOAK.md) **Phase 5**. Update the **Progress** table as phases complete.

---

## Architecture target

```text
Internet
    │
    ▼
ingress-nginx (public)  ──►  acruet.home.bradandmarsha.com  ──►  acruet-user (Tomcat, 2 replicas)
                                                                    │
                                                                    ├── OIDC ──► auth.home.bradandmarsha.com / realms/wise-k8s
                                                                    │
                                                                    └── JDBC ──► acruet-db (CNPG, 3×20Gi)

LAN / homelab network
    │
    ▼
ingress-nginx-internal  ──►  acruet-admin.home.bradandmarsha.com  ──►  acruet-admin (Tomcat, 1 replica)
                                                                          │
                                                                          ├── OIDC (same client acruet) + role a-cruet-admin
                                                                          └── Admin API ──► Keycloak (client acruet-admin)

Outbound SMTP  ──►  smtp.protonmail.ch:587  (verification + approval + suspend/offboard notices)
```

**Data flow:** Sensitive ledger payloads are **encrypted in the browser** before REST API calls. Postgres stores ciphertext + plaintext operational metadata only.

---

## Scope

| In scope (v1) | Out of scope (later) |
|---------------|----------------------|
| Maven multi-module, two WARs (user + admin) | Shared household / multi-user ledger |
| Dedicated `acruet-cnpg` cluster | CAPTCHA on signup |
| Public user + internal admin ingress | Keycloak SMTP (app uses Proton) |
| Signup → verify email → admin approval → provision | Social login |
| Client-side envelope encryption + recovery file | Mobile app |
| Ledger: deposit, withdraw, transfer, archive | Multi-currency |
| Client-side CSV + stacked area reports | Server-side plaintext reports |
| Admin: approve/reject, suspend, offboard, role grant | Full per-API audit log |
| GitHub Actions + Flux image automation | Mailpit in production |

---

## Conventions (match `wise-k8s` patterns)

| Item | Value |
|------|-------|
| User hostname | `acruet.home.bradandmarsha.com` |
| Admin hostname | `acruet-admin.home.bradandmarsha.com` |
| User ingress class | `nginx` (public) |
| Admin ingress class | `nginx-internal` |
| TLS | cert-manager + `letsencrypt-prod` |
| DNS | external-dns annotations on Ingress |
| Storage class | `csi-rbd-sc` |
| Java / Tomcat | Java 17, Tomcat 10.1 (`tomcat:10.1-jre17-temurin`) |
| REST | JAX-RS Jersey 3.x |
| App repo | `a-cruet` |
| Cluster manifests | `wise-k8s/iac/kustomize/acruet/`, `acruet-cnpg/` |
| Secrets | SOPS-encrypted in `wise-k8s` |
| OIDC issuer | `https://auth.home.bradandmarsha.com/realms/wise-k8s` |
| OIDC clients | `acruet` (sign-in), `acruet-admin` (service account) |
| Realm role | `a-cruet-admin` |

---

## Progress

| Phase | Status |
|-------|--------|
| 0 — Decisions | ✅ Complete (2026-07-12) — `PRODUCT.md` |
| 1 — Repository scaffold | ✅ Complete (2026-07-12) |
| 2 — `acruet-cnpg` database | ✅ Complete — cluster healthy, DB connection verified |
| 3 — Platform deploy (shells + ingress + secrets) | ✅ Complete (2026-07-12) |
| 4 — OIDC sign-in (user + admin) | ✅ Complete (2026-07-12) — images `1.0.0`; non-admin 403 test deferred |
| 5 — Signup + SMTP + verification + image automation | ✅ Complete (2026-07-15) — signup + verify E2E; throttling/re-apply/image-automation verify deferred |
| 6 — Admin approval + Keycloak provisioning | ✅ Complete (2026-07-14) — approve path + first OIDC login; reject E2E deferred → Phase 12 |
| 7 — Client encryption + key lifecycle | ✅ Complete (2026-07-15) — setup, unlock, idle timeout, rotation |
| 8 — Ledger core | Pending |
| 9 — Client-side reports | Pending |
| 10 — Admin ops (suspend, offboard, cron) | Pending |
| 11 — CI/CD + Flux image automation | ✅ Merged into Phase 5 — CI in `a-cruet`; CD manifests in `wise-k8s` |
| 12 — Index tiles + E2E verification | Pending |
| 13 — Non-technical README summary | ✅ Complete (2026-07-12) — `README.md` |

---

## Phase 0 — Decisions ✅ complete (2026-07-12)

**Goal:** Lock product and technical choices before implementation.

**Deliverable:** [`PRODUCT.md`](PRODUCT.md) sections 1–6 locked.

**Key outcomes:**

- No Keycloak self-registration; app provisions users on admin approval
- Confidential OIDC client `acruet`; service account `acruet-admin`
- Proton Mail SMTP for transactional email
- Client-side encryption; admins see metadata counts only

---

## Phase 1 — Repository scaffold ✅ complete (2026-07-12)

**Goal:** Runnable Maven multi-module project with two WAR modules and shared libraries.

### Module layout (suggested)

```text
a-cruet/
├── pom.xml                    # parent POM
├── acruet-core/               # domain models, shared DTOs
├── acruet-crypto-client/      # JS bundling or shared crypto constants (optional split)
├── acruet-user-war/           # user Tomcat WAR
├── acruet-admin-war/          # admin Tomcat WAR
└── Dockerfile                 # multi-target build (`user`, `admin`)
```

### Tasks

1. Parent POM — Java 17, Jersey 3.1.5, Jakarta Servlet 6, JUnit 5
2. Health endpoint `/health` on both WARs (match `wise-home-index`)
3. Placeholder static landing pages (user + admin)
4. Flyway or Liquibase migration skeleton (schema version table only)
5. JDBC config from env vars (CNPG secret keys injected later)
6. GitHub Actions — `.github/workflows/pr.yml` (Docker `build` target + semver bump) and `release.yml` (push `sbwise/acruet-user`, `sbwise/acruet-admin`)

### Verify

Java and Maven are **not required on the host**. Use Docker for build and test:

```bash
chmod +x scripts/verify-phase1.sh
./scripts/verify-phase1.sh
```

Or run steps individually:

```bash
# Unit tests + WAR packaging
docker run --rm -v "$PWD:/workspace" -w /workspace maven:3.9-eclipse-temurin-17 mvn -q -B clean verify

# Runtime images (build targets: user, admin)
docker build --target user -t acruet-user:local .
docker build --target admin -t acruet-admin:local .

# Health checks
docker run --rm -p 8080:8080 acruet-user:local
curl -fsS http://localhost:8080/health
```

**Verified 2026-07-12:** `scripts/verify-phase1.sh` — Maven verify in container, both images build, `/health` returns `{"status":"UP"}`, landing pages render.

---

## Phase 2 — `acruet-cnpg` database

**Goal:** HA Postgres ready before app data migrations.

**Status:** ✅ Complete — cluster healthy, DB connection verified (2026-07-12).

**Pattern:** Copy `wise-k8s/iac/kustomize/keycloak-cnpg/` → `acruet-cnpg/`.

### Manifests

| Resource | Notes |
|----------|--------|
| Namespace | `acruet-cnpg` |
| `Cluster` | `acruet-db`, 3 instances, 20Gi, `csi-rbd-sc` |
| `Database` | e.g. `acruet` owned by `acruet` |
| Flux | `iac/kustomize/fluxcd/kustomizations/acruet-cnpg.yaml` |

### Sizing

| Setting | Value |
|---------|--------|
| `instances` | 3 |
| `storage.size` | 20Gi |
| `minSyncReplicas` | 1 |
| `imageName` | `ghcr.io/cloudnative-pg/postgresql:17.6-standard-trixie` |

### Verify

```bash
flux get kustomizations acruet-cnpg
kubectl -n acruet-cnpg get cluster,pods
kubectl -n acruet-cnpg get secret acruet-db-app -o jsonpath='{.data.uri}' | base64 -d
```

---

## Phase 3 — Platform deploy (shells + ingress + secrets)

**Goal:** Both Tomcat deployments on wise-k8s with TLS, ingress classes, and SOPS secrets — app shells only.

**Status:** ✅ Complete (2026-07-12) — Flux reconciled; SOPS secrets decrypt via `flux-sops` IRSA.

### Manifests (`wise-k8s/iac/kustomize/acruet/`)

| Resource | Notes |
|----------|--------|
| Namespace | `acruet` |
| Deployments | `acruet-user` (2 replicas), `acruet-admin` (1 replica) |
| Services | ClusterIP :8080 |
| Certificates | user + admin hostnames |
| Ingress (user) | `ingressClassName: nginx`, public |
| Ingress (admin) | `ingressClassName: nginx-internal` |
| Secrets (SOPS) | OIDC + SMTP placeholders (`base/secrets/`); DB via cross-namespace sync |
| DB credential sync | CronJob + bootstrap Job (mirror keycloak pattern) |
| Flux | `iac/kustomize/fluxcd/kustomizations/acruet.yaml`, depends on `acruet-cnpg` + ingress + cert-manager |
| Images (v1) | `sbwise/acruet-user:0.1.2`, `sbwise/acruet-admin:0.1.2` |

### wise-home-index annotations

- User tile: public scope
- Admin tile: private scope (`index.home.bradandmarsha.com/*` annotations)

### SOPS secrets

Secrets are SOPS-encrypted with AWS KMS (`alias/sops`). Flux `acruet` Kustomization uses `decryption.provider: sops`; `kustomize-controller` IRSA role `flux-sops` must be active (restart controller pods after annotating the ServiceAccount).

```bash
cd wise-k8s/iac/kustomize/acruet/base
sops secrets/acruet-oidc.yaml   # edit in place
sops secrets/acruet-smtp.yaml
```

### Verify

```bash
flux get kustomizations acruet
curl -sI https://acruet.home.bradandmarsha.com/health
curl -sI https://acruet-admin.home.bradandmarsha.com/health   # from LAN
```

**Verified 2026-07-12:**

| Check | Result |
|-------|--------|
| `flux get kustomizations acruet` | Ready |
| Pods | `acruet-user` 2/2, `acruet-admin` 1/1 Running |
| Certificates | `acruet-user-certificate`, `acruet-admin-certificate` Ready |
| Ingress classes | user `nginx`, admin `nginx-internal` |
| SOPS secrets | `acruet-oidc`, `acruet-smtp` decrypted and applied |
| DB sync | Bootstrap Job + CronJob; `acruet-db-app` present |
| `https://acruet.home.bradandmarsha.com/health` | HTTP 200 `{"status":"UP"}` |
| `https://acruet-admin.home.bradandmarsha.com/health` | HTTP 200 `{"status":"UP"}` |
| Landing pages `/` | HTTP 200 (user + admin) |
| wise-home-index annotations | Both tiles enabled with `acruet.png` image |

---

## Phase 4 — OIDC sign-in (user + admin) ✅ complete (2026-07-12)

**Goal:** Authenticated sessions via Keycloak on both hostnames.

**Status:** Deployed and verified on both hostnames (`acruet` **1.0.0**). Keycloak bootstrap (realm role, client scopes, post-logout URIs) done via console — see `wise-k8s` README todo for GitOps follow-up. **Deferred:** non-admin 403 on admin host (no test user yet).

**Pairs with:** [`KEYCLOAK.md` Phase 5](../wise-k8s/KEYCLOAK.md#phase-5--oidc-client--a-cruet-integration).

### wise-k8s

| Resource | Path |
|----------|------|
| `KeycloakOIDCClient` `acruet` | `iac/kustomize/keycloak/base/oidc-client-acruet.yaml` |
| `KeycloakOIDCClient` `acruet-admin` | `iac/kustomize/keycloak/base/oidc-client-acruet-admin.yaml` |
| Client secrets (SOPS) | `keycloak/base/secrets/acruet-oidc-client.yaml`, `acruet-admin-oidc-client.yaml` |
| App OIDC env | `acruet/base/deployment-{user,admin}.yaml` |
| Keycloak Flux decryption | `fluxcd/kustomizations/keycloak.yaml` → `decryption.provider: sops` |
| Image tag | `acruet/overlays` → `1.0.0` |
| Ingress session affinity | `acruet/base/ingress-{user,admin}.yaml` — cookie affinity for Tomcat sessions |

### a-cruet

| Component | Notes |
|-----------|--------|
| `OidcSettings`, `OidcService`, `OidcAuthFilter` | `acruet-core` — authorization code flow, Tomcat session |
| `AuthResource` | `/auth/callback`, `/auth/logout`, `/auth/me` |
| User WAR | OIDC filter; landing shows signed-in user |
| Admin WAR | OIDC filter + `a-cruet-admin` role gate (403) |

### Bootstrap (manual)

1. **Generate client secret** (once): `openssl rand -base64 32`
2. **SOPS — Keycloak client** (must match operator `auth.secretRef`):
   ```bash
   cd wise-k8s/iac/kustomize/keycloak/base
   sops secrets/acruet-oidc-client.yaml   # client-secret: <value>
   ```
3. **SOPS — app secret** (same `client-secret` value):
   ```bash
   cd wise-k8s/iac/kustomize/acruet/base
   sops secrets/acruet-oidc.yaml   # client-id: acruet, client-secret: <same>
   ```
4. **Realm role** — Keycloak console → realm `wise-k8s` → create role `a-cruet-admin`; assign to first admin user
5. **Release** — tag `a-cruet` **0.1.3**, push images; reconcile `keycloak` + `acruet` Flux Kustomizations

### Tasks

1. Apply `KeycloakOIDCClient` manifests (`acruet`, `acruet-admin`)
2. Store `acruet` client secret in SOPS; mount into both deployments
3. Implement OIDC authorization code flow → `/auth/callback` → Tomcat session
4. Admin WAR: after OIDC, require realm role `a-cruet-admin` (403 otherwise)
5. Bootstrap first admin: assign `a-cruet-admin` in Keycloak console manually

### Verify

| Check | Expected | Result |
|-------|----------|--------|
| Unauthenticated `/` on user host | Redirect to Keycloak login | ✅ |
| Successful login | Session cookie; landing page | ✅ |
| Admin with role | Admin dashboard shell loads | ✅ |
| Logout | Session cleared; Keycloak SSO logout | ✅ |
| Admin host without role | 403 after OIDC | ⏳ Deferred — no non-admin test user |

**Verified 2026-07-12:** User + admin OIDC sign-in, admin role gate (with `a-cruet-admin` in client dedicated scope), logout on user host. Manual Keycloak client settings documented in `wise-k8s` README todo.

---

## Phase 5 — Signup + SMTP + verification + image automation

**Goal:** Public applicant flow without Keycloak account, plus GitOps-driven deploys when `a-cruet` releases new images.

**Status:** ✅ Verified on cluster (2026-07-15). Signup form → Proton verification email (`acruet@bradandmarsha.com`) → verify link → `pending_approval` page. Image automation manifests deployed; explicit `flux` smoke test deferred.

### Tasks

1. Public signup form: name, email, reason, phone, mailing address — `/signup`
2. Jakarta Mail + Proton SMTP from SOPS secret (`smtp.protonmail.ch:587`, STARTTLS) — `ACRUET_SMTP_*` env from `acruet-smtp`
3. Email verification token + link — `/signup/verify?token=...`
4. Pending application queue in Postgres (plaintext metadata) — Flyway `V2__signup_applications.sql`
5. Rate limits: **5 attempts/hour per IP**, **3 attempts/day per email**
6. Re-apply rules: 7-day cooldown; block after two rejections
7. **CI (done):** `a-cruet` GitHub Actions build both WARs, push `sbwise/acruet-user` and `sbwise/acruet-admin`, tag with semver on merge to `main`
8. **CD:** Flux `ImageRepository` + `ImagePolicy` + `ImageUpdateAutomation` bump overlay tags via kustomize setters

### Deploy

| Item | Location |
|------|----------|
| Image tags (initial) | `acruet/overlays/kustomization.yaml` — set to first released tag; Flux updates thereafter |
| Image automation | `acruet/overlays/image-automation.yaml` |
| SMTP env | `deployment-user.yaml` → `acruet-smtp` secret |
| Public routes | `OidcAuthFilter` allows `/`, `/signup`, `/auth/*` without OIDC on user WAR; `/auth/login` starts sign-in |
| Migrations | `DatabaseLifecycleListener` on user + admin WAR startup |

**Pattern:** `iac/kustomize/wise-home-index/overlays/image-automation.yaml`

### Verify

**Signup**

- [x] Submit application → verification email received (`acruet@bradandmarsha.com`)
- [x] Click verify link → `pending_approval` page ("pending admin approval")
- [x] No Keycloak user created yet (by design at this phase)
- [x] Duplicate signup throttling (IP + email) behaves as configured — verified 2026-07-15 via curl (see below)

**Throttling check (curl — bypasses HTML5 `required`)**

Limits: **5 attempts/hour per IP**, **3 attempts/day per email**. Rate limit is evaluated **only after server-side validation passes**; invalid submits still **record** attempts. **IP limit is checked before email limit.**

Run each test in a **separate window** (or delete test rows from `signup_attempt`) so counters do not interfere.

Per-email (3 invalid + 1 valid → 4th blocked):

```bash
EMAIL="throttle-email-only@example.com"
for i in 1 2 3; do
  curl -s https://acruet.home.bradandmarsha.com/signup -X POST \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "email=$EMAIL" --data-urlencode "fullName=" \
    --data-urlencode "phone=x" --data-urlencode "mailingAddress=x" --data-urlencode "reason=x" \
    | grep -oE 'Name is required|Too many signup attempts for this email[^<]*'
done
curl -s https://acruet.home.bradandmarsha.com/signup -X POST \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "email=$EMAIL" --data-urlencode "fullName=Test User" \
  --data-urlencode "phone=555-0100" --data-urlencode "mailingAddress=123 St" \
  --data-urlencode "reason=throttle test" \
  | grep -oE 'Too many signup attempts for this email[^<]*|Check your email'
```

Per-IP (5 invalid + 1 valid → 6th blocked). Use a **fresh IP** not used in the email test; `X-Forwarded-For` from outside may not apply (ingress records the real client IP).

```bash
for i in 1 2 3 4 5; do
  curl -s https://acruet.home.bradandmarsha.com/signup -X POST \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "email=ip-build-$i@example.com" --data-urlencode "fullName=" \
    --data-urlencode "phone=x" --data-urlencode "mailingAddress=x" --data-urlencode "reason=x" \
    | grep -oE 'Name is required|Too many signup attempts from your network[^<]*'
done
curl -s https://acruet.home.bradandmarsha.com/signup -X POST \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "email=ip-final@example.com" --data-urlencode "fullName=Test User" \
  --data-urlencode "phone=555-0101" --data-urlencode "mailingAddress=456 Ave" \
  --data-urlencode "reason=throttle test" \
  | grep -oE 'Too many signup attempts from your network[^<]*|Check your email'
```

Cleanup (optional): `DELETE FROM signup_attempt WHERE email LIKE '%example.com';` — for email-only test, also clear same **client IP** rows if IP limit was hit earlier (`WHERE ip_address = '…'`).

- [ ] Re-apply after rejection respects 7-day cooldown / two-strike block — **deferred → Phase 12 E2E**

**Image automation**

```bash
flux get image repository,policy,update -n flux-system | grep acruet
# After a new release tag is pushed → fluxcdbot commits tag bump to wise-k8s main
```

- [ ] `flux get image …` shows `acruet-user` / `acruet-admin` resources healthy
- [ ] New release tag triggers `fluxcdbot` overlay bump (validate on next `a-cruet` merge)

---

## Phase 6 — Admin approval + Keycloak provisioning ✅ complete (2026-07-14)

**Goal:** Admin queue → approve creates Keycloak user + initial a-cruet records.

**Status:** ✅ Complete on cluster (2026-07-14). Approve → Keycloak user → approval email → first OIDC login (temp password, change password, logout, re-login) verified (`sbwise@gmail.com`). Reject flow verification deferred to **Phase 12 E2E**. Keycloak **manual console** bootstrap (non-GitOps): `realm-management` service-account roles **and** matching roles on `acruet-admin-dedicated` scope (Keycloak 26; **Full scope allowed OFF** verified).

### Tasks

1. Admin UI: pending applications list — `/approvals`
2. Approve: `acruet-admin` client credentials → Keycloak Admin API
   - Create user in realm `wise-k8s`
   - Set temporary password
   - Create a-cruet user row + empty ledger scaffold (`acruet_user`, counts at zero)
   - Email sign-in link (Proton SMTP)
3. Reject: mark rejected + rejection email (7-day cooldown / two-strike block)
4. Audit log: admin approve/reject actions in `admin_action_audit`

### a-cruet

| Component | Notes |
|-----------|--------|
| Flyway `V3__users_and_admin_audit.sql` | `acruet_user`, `admin_action_audit` |
| `KeycloakAdminSettings`, `KeycloakAdminClient` | Client credentials + Admin REST API |
| `ApprovalService` | Approve/reject orchestration + SMTP |
| `AdminApprovalResource` | HTML queue at `/approvals` |
| Env | `ACRUET_KEYCLOAK_ADMIN_CLIENT_*`, `ACRUET_USER_BASE_URL`, SMTP on admin deployment |

### wise-k8s

| Resource | Notes |
|----------|--------|
| `secrets/acruet-admin-api.yaml` | SOPS — same `client-secret` as Keycloak `acruet-admin` |
| `deployment-admin.yaml` | KC admin API secret, SMTP, `ACRUET_USER_BASE_URL` |

### Keycloak bootstrap (manual console — required before approve)

The `acruet-admin` client authenticates via client credentials, but **cannot call the Admin API until both** (a) its **service account** has `realm-management` roles and (b) the **dedicated client scope** allows those roles into the token. The `KeycloakOIDCClient` CR cannot assign `realm-management` client roles or dedicated-scope mappings yet (see `oidc-client-acruet-admin.yaml` comment; tracked in `wise-k8s` README todo).

**Symptom if incomplete:** token endpoint returns **200**, but `GET /admin/realms/wise-k8s/users` returns **403**.

**Console (one-time, realm `wise-k8s`):**

1. Keycloak admin → **Clients** → **`acruet-admin`**
2. **Service account roles** tab → **Assign role** → **Filter by clients** → **`realm-management`**
3. Assign **`manage-users`**, **`view-users`**, **`query-users`**
4. **Clients** → **`acruet-admin`** → **Client scopes** tab → open the dedicated scope (link in the row, **`acruet-admin-dedicated`**) → **Scope** tab
5. **Recommended (verified):** leave **Full scope allowed** **OFF** → **Assign role** → **Filter by clients** → **`realm-management`** → assign **manage-users**, **view-users**, **query-users** (same three as step 3)
6. **Homelab shortcut (also works):** turn **Full scope allowed** **ON** on that dedicated scope instead of step 5
7. Confirm `acruet` namespace secret `acruet-admin-api` `client-secret` matches Keycloak `acruet-admin` client secret

If approve still returns 403 after steps 3–5, assign **`realm-admin`** from `realm-management` to the service account (broader fallback).

**Pre-flight (optional curl):** after roles are assigned, client credentials should list users (HTTP 200, empty array if none):

```bash
# From a host that can reach auth; substitute client secret from SOPS/decrypted secret
TOKEN=$(curl -s -X POST "https://auth.home.bradandmarsha.com/realms/wise-k8s/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=acruet-admin -d client_secret='…' \
  | jq -r .access_token)
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" \
  "https://auth.home.bradandmarsha.com/admin/realms/wise-k8s/users?email=test@example.com&exact=true"
# expect 200 (not 403)
```

### Verify

- [x] `acruet-admin` Keycloak bootstrap: service-account roles + dedicated scope role mappings (**Full scope allowed OFF**; pre-flight curl returns 200)
- [x] Admin dashboard links to pending queue
- [x] Approve application → Keycloak user exists in `wise-k8s`
- [x] Applicant email with sign-in instructions + temporary password
- [x] First OIDC login succeeds (password change prompt from Keycloak; logout + re-login)
- [x] `admin_action_audit` row for approve (implicit in successful approve flow)
- [x] `acruet_user` row created with `key_setup_complete = false` (Phase 7 gate)

---

## Phase 7 — Client encryption + key lifecycle ✅ complete (2026-07-15)

**Goal:** Mandatory passphrase-derived KEK, DEK wrap, recovery file before ledger use.

**Status:** ✅ Complete on cluster (2026-07-15). Key setup, unlock (`sessionStorage` + 30-min idle timeout), key rotation, and `/ledger` gate verified (`sbwise@gmail.com`).

### Tasks

1. Browser: Web Crypto — AES-256-GCM DEK, PBKDF2 → AES-KW KEK (passphrase never sent to server)
2. One DEK per user; store wrapped DEK server-side (`user_encryption_key`)
3. Recovery file export (`acruet-recovery.json`) + confirmation gate (`key_setup_complete`)
4. Session unlock with 30-minute idle timeout (`sessionStorage`, tab-scoped)
5. Key rotation: re-wrap same DEK with new passphrase (no ledger ciphertext changes)

### a-cruet

| Component | Notes |
|-----------|--------|
| Flyway `V4__user_encryption_key.sql` | `user_encryption_key` table (wrapped DEK + KDF metadata) |
| `WrappedDekPayload`, `KeyService` | Validate + persist wrapped DEK; confirm recovery; rotate |
| `UserRepository` / `UserEncryptionRepository` | Lookup by Keycloak subject; wrapped DEK CRUD |
| `KeySetupFilter` | User WAR — redirect authenticated users without `key_setup_complete` → `/keys/setup` |
| `KeyResource` | `/keys/setup`, `/keys/unlock`, `/keys/rotate` HTML + JSON API |
| `static/js/acruet-crypto.js` | Web Crypto primitives + `sessionStorage` session unlock |
| `LandingResource` | Key-aware home; `/ledger` stub gated until setup + unlock |

### API (authenticated)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/keys/status` | `{ keySetupComplete, hasWrappedDek }` |
| GET | `/keys/wrapped-dek` | KDF params + wrapped DEK (base64) for unlock/rotate |
| PUT | `/keys/wrapped-dek` | Initial setup — store wrapped DEK |
| POST | `/keys/confirm-recovery` | Set `key_setup_complete = true` after backup confirmed |
| POST | `/keys/rotate` | Replace wrapped DEK (same DEK, new KDF params) |

### Verify

**Key setup (new approved user)**

- [x] Sign in → redirect to `/keys/setup`
- [x] Create passphrase → download recovery file → confirm backup → finish
- [x] `user_encryption_key` row exists; `acruet_user.key_setup_complete = true` (`sbwise@gmail.com`)
- [x] Server DB has wrapped DEK + KDF metadata only — no passphrase column

**Unlock + rotation**

- [x] Home shows unlock status; `/keys/unlock` unwraps DEK for session
- [x] Session persists across page refresh/navigation within idle window (`sessionStorage`)
- [x] 30-minute idle timeout without activity → unlock required again
- [x] `/keys/rotate` with current passphrase → new wrapped DEK on server; recovery file downloads
- [x] Rotation does not change ledger ciphertext (no ledger rows yet — wrapped DEK blob updated only)

**Ledger gate (Phase 8 prep)**

- [x] `/ledger` accessible after key setup complete (server gate)
- [x] `/ledger` shows unlock hint when key not unlocked in browser session

---

## Phase 8 — Ledger core

**Goal:** Envelope budgeting MVP.

### Tasks

1. CRUD ledger accounts (encrypted names); 100 default limit
2. Deposit — 100% allocation across envelopes in one step
3. Withdraw / transfer — warn on overspend; allow negative with warning
4. Archive account at zero balance
5. Append-only transactions; user date + system `created-at`
6. Per-user API write rate limits

### Verify

- Full deposit → withdraw → transfer cycle in browser
- Negative balance shows warning
- Server stores ciphertext; plaintext counts update for admin metadata

---

## Phase 9 — Client-side reports

**Goal:** CSV export and stacked area chart — 100% browser-side decryption.

### Tasks

1. API returns ciphertext blobs filtered by date range + account scope
2. Browser decrypts, aggregates, renders chart (e.g. Chart.js)
3. CSV download from decrypted data

### Verify

- Report matches manual ledger operations
- No plaintext amounts in server logs or API responses

---

## Phase 10 — Admin ops (suspend, offboard, cron)

**Goal:** Remaining admin & abuse workflows from `PRODUCT.md` Section 6.

### Tasks

1. User list with operational counts (accounts, transactions, last active)
2. Grant/revoke `a-cruet-admin` via admin UI → Keycloak Admin API
3. Suspend: disable Keycloak user + email with admin-set duration
4. CronJob: auto-unsuspend when suspension ends
5. Offboard: email 7-day export window
6. Client-side decrypted export (CSV/JSON) during window
7. CronJob: on export complete or 7-day expiry → disable Keycloak + purge a-cruet data
8. Admin unblock for twice-rejected emails

### Verify

- Suspend → user cannot log in; auto-restore after N days
- Offboard → export works; data purged after trigger
- Admin action audit entries present

---

## Phase 11 — CI/CD + Flux image automation

**Merged into Phase 5** (2026-07-14). Continuous integration lives in `a-cruet/.github/workflows/`; Flux image automation manifests live in `wise-k8s/iac/kustomize/acruet/overlays/image-automation.yaml`. See Phase 5 deploy and verify steps.

---

## Phase 12 — Index tiles + E2E verification

**Goal:** Production-ready homelab service.

### Tasks

1. Ingress annotations for wise-home-index (public user tile, private admin tile)
2. End-to-end scripted checklist (below)
3. Document first-admin bootstrap and Proton SMTP token rotation

### E2E checklist

| # | Flow | Expected |
|---|------|----------|
| 1 | Public signup + verify + approve | User can sign in |
| 2 | First login key + recovery | Ledger unlocked |
| 3 | Deposit / withdraw / transfer | Balances correct client-side |
| 4 | CSV + chart report | Matches ledger |
| 5 | Admin suspend + auto-unsuspend | Access restored |
| 6 | Admin offboard + export + purge | Data removed |
| 7 | Flux + CNPG healthy | All Kustomizations Ready |
| 8 | Keycloak Phase 5 clients | `keycloakoidcclient` Ready |
| 9 | Admin reject application | Rejection email sent; 7-day re-apply cooldown / two-strike block enforced |

**Deferred from earlier phases:** reject flow (#9); signup re-apply throttling after rejection (Phase 5); Phase 5 throttling/image-automation smoke tests. Flow #1 (signup → verify → approve → sign in) verified during Phases 5–6.

---

## Phase 13 — Non-technical README summary ✅ complete (2026-07-12)

**Goal:** Give visitors and future contributors a plain-language picture of a-cruet without reading `PRODUCT.md`.

**Deliverable:** [`README.md`](README.md) — non-technical product summary.

### Content checklist

- [x] Name origin (cruet, Proverbs 21:20, “accrue it”)
- [x] What envelope budgeting is and what users can do
- [x] Privacy model in accessible terms (client-side encryption, passphrase, recovery file, admin limits)
- [x] Signup and approval flow (no jargon: OIDC, CNPG, etc.)
- [x] Links to `PRODUCT.md` and `ROLLOUT.md` for technical readers

### Tone

- Write for a **curious non-developer** — a family member or future user, not a homelab operator
- Avoid implementation terms (Tomcat, Jersey, SOPS, Flux, Keycloak client IDs)
- Keep it concise; one screenful of prose plus short sections

### Verify

- README stands alone as an introduction to the product
- No contradictions with locked decisions in `PRODUCT.md`
- Technical depth remains in `PRODUCT.md` / `ROLLOUT.md` only

---

## Rollout order (safe sequence)

1. Phase 0 decisions ✅
2. Phase 1 scaffold (can start immediately)
3. Phase 2 `acruet-cnpg` (parallel with Phase 1)
4. Phase 3 platform deploy
5. Phase 4 OIDC + **KEYCLOAK.md Phase 5**
6. Phase 5 signup + SMTP + **Flux image automation**
7. Phase 6 admin approval ✅
8. Phase 7 encryption ✅
9. Phase 8 ledger
10. Phase 9 reports
11. Phase 10 admin ops
12. Phase 12 E2E
13. Phase 13 README summary (can be drafted anytime; finalize after product stabilizes)

**Parallel work:** Keycloak Phase 6–7 (HA + observability) does not block a-cruet Phases 1–3.

---

## Suggested repo changes

| Repo | Path | Change |
|------|------|--------|
| `a-cruet` | `README.md` | Non-technical product summary (Phase 13) |
| `a-cruet` | `pom.xml`, modules | Maven multi-module |
| `a-cruet` | `.github/workflows/` | Build + push images |
| `wise-k8s` | `iac/kustomize/acruet-cnpg/` | CNPG cluster |
| `wise-k8s` | `iac/kustomize/acruet/` | Deployments, ingresses, certs, SOPS |
| `wise-k8s` | `iac/kustomize/keycloak/base/oidc-client-acruet*.yaml` | Keycloak clients |
| `wise-k8s` | `iac/kustomize/acruet/overlays/image-automation.yaml` | Flux image automation (Phase 5) |
| `wise-k8s` | `iac/kustomize/fluxcd/kustomizations/acruet*.yaml` | Flux wiring |

---

## Risks and gotchas

| Risk | Mitigation |
|------|------------|
| **Key loss** | Mandatory recovery file; clear UX warnings |
| **Admin cannot decrypt** | By design — support is metadata-only |
| **OIDC redirect mismatch** | Lock URIs in GitOps; test both hostnames |
| **Internal admin DNS** | `acruet-admin` only on LAN; Keycloak redirect still works from browser on trusted network |
| **Proton SMTP egress** | Confirm pods reach `smtp.protonmail.ch:587` |
| **SOPS decryption** | Verify Flux kustomize-controller has SOPS keys before deploying secrets |
| **Offboard purge** | Irreversible — test export path before enabling auto-purge in prod |
| **Encrypted export window** | User must sign in + unlock key within 7 days |

---

## References

- [`PRODUCT.md`](PRODUCT.md) — locked decisions
- [`wise-k8s/KEYCLOAK.md`](../wise-k8s/KEYCLOAK.md) — IdP rollout, Phase 5 clients
- [`wise-home-index`](../wise-home-index/) — Java 17 / Tomcat / Jersey reference
- `wise-k8s/iac/kustomize/keycloak-cnpg/`, `plex-cnpg/` — CNPG patterns
- `wise-k8s/iac/kustomize/wise-home-index/overlays/image-automation.yaml` — Flux images
- [Proton SMTP submission](https://proton.me/support/smtp-submission)
- [KeycloakOIDCClient CRD](https://www.keycloak.org/operator/basic-deployment#_creating_an_oidc_client)
