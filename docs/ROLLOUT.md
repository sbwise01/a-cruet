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
| 6 — Admin approval + Keycloak provisioning | ✅ Complete (2026-07-14) — approve path + first OIDC login; reject E2E deferred → Phase 13 |
| 7 — Client encryption + key lifecycle | ✅ Complete (2026-07-15) — setup, unlock, idle timeout, rotation |
| 8 — Ledger core | ✅ Complete (2026-07-16) — deposits, withdraws, transfers, archive, ciphertext verified |
| 9 — Ledger UI polish | Pending |
| 10 — Client-side reports | Pending |
| 11 — Admin ops (suspend, offboard, cron) | Pending |
| 12 — CI/CD + Flux image automation | ✅ Merged into Phase 5 — CI in `a-cruet`; CD manifests in `wise-k8s` |
| 13 — Index tiles + E2E verification | Pending |
| 14 — Non-technical README summary | ✅ Complete (2026-07-12) — `README.md` |

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
| wise-home-index annotations | Both tiles enabled with `acruet-bw.jpg` image |

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

- [ ] Re-apply after rejection respects 7-day cooldown / two-strike block — **deferred → Phase 13 E2E**

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

**Status:** ✅ Complete on cluster (2026-07-14). Approve → Keycloak user → approval email → first OIDC login (temp password, change password, logout, re-login) verified (`sbwise@gmail.com`). Reject flow verification deferred to **Phase 13 E2E**. Keycloak **manual console** bootstrap (non-GitOps): `realm-management` service-account roles **and** matching roles on `acruet-admin-dedicated` scope (Keycloak 26; **Full scope allowed OFF** verified).

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

### Bootstrap admin backfill (link existing Keycloak user to `acruet_user`)

**When:** First admin(s) are created **manually in Keycloak** (`PRODUCT.md` #46): they have the **`a-cruet-admin`** realm role and can use the **admin hostname**, but **no `acruet_user` row** exists yet. The normal approve workflow cannot link them — `KeycloakAdminClient.provisionUser` rejects emails that already exist in Keycloak.

**Symptom on user hostname:** Phase 9 item 10 unlinked-login UX (avatar + “administrators have been alerted”); **`/keys/setup` must not run** until this backfill is done. Admin hostname continues to work on Keycloak role alone.

**Not the same as approve:** Signup → verify → approve creates **both** Keycloak user and `acruet_user`. Bootstrap is the inverse — Keycloak user exists first; backfill adds the app row.

**Database:** CNPG cluster `acruet-db`, database **`acruet`** (not `app`). Example shell:

```bash
kubectl -n acruet-cnpg exec -it acruet-db-1 -- psql -U app -d acruet
```

#### Steps

1. **Resolve Keycloak user ID** (`keycloak_user_id` — must match OIDC token `sub`):
   - From a prior unlinked visit: `SELECT keycloak_user_id, email, created_at FROM login_anomaly WHERE email = '…' ORDER BY created_at DESC LIMIT 1;`
   - Keycloak console → **Users** → user → copy **ID**
   - Admin API: `GET /admin/realms/wise-k8s/users?email=…&exact=true` → `id`

2. **Confirm no existing row:**
   ```sql
   SELECT id, keycloak_user_id, email, key_setup_complete
   FROM acruet_user
   WHERE LOWER(email) = LOWER('<email>')
      OR keycloak_user_id = '<keycloak-user-id>';
   ```
   Expect zero rows.

3. **Insert `acruet_user`** (bootstrap: `signup_application_id` is `NULL`):
   ```sql
   INSERT INTO acruet_user (
       id, keycloak_user_id, email, display_name, signup_application_id
   ) VALUES (
       gen_random_uuid(),
       '<keycloak-user-id>',
       '<email>',
       '<display-name>',
       NULL
   );
   ```
   Defaults: `key_setup_complete = false`, ledger counts zero, 100-account limit.

4. **Optional audit** (recommended for traceability):
   ```sql
   INSERT INTO admin_action_audit (
       admin_keycloak_user_id, admin_email, action, target_type, target_id, detail
   ) VALUES (
       '<keycloak-user-id>',
       '<email>',
       'bootstrap_link_user',
       'acruet_user',
       (SELECT id FROM acruet_user WHERE keycloak_user_id = '<keycloak-user-id>'),
       'Manual bootstrap backfill: linked existing Keycloak admin to acruet_user'
   );
   ```

5. **Sign in on user hostname** — sign out first if already signed in. Expect **Create encryption key** (or redirect toward `/keys/setup`), **not** the unlinked-login message.

6. **Complete Phase 7 key setup** — `/keys/setup`: passphrase, recovery file, confirm backup. Verify:
   ```sql
   SELECT key_setup_complete FROM acruet_user WHERE keycloak_user_id = '<keycloak-user-id>';
   SELECT user_id FROM user_encryption_key WHERE user_id = (
     SELECT id FROM acruet_user WHERE keycloak_user_id = '<keycloak-user-id>'
   );
   ```

After step 6, the bootstrap admin can use **both** hostnames without routine unlinked-login anomalies. Phase 11 grant-admin (existing `acruet_user` only) applies to this row.

**Re-testing Phase 9 item 10:** deleting the `acruet_user` row (or using a separate Keycloak-only test user) is required — a linked bootstrap admin cannot simulate unlinked UX again.

### Verify

- [x] `acruet-admin` Keycloak bootstrap: service-account roles + dedicated scope role mappings (**Full scope allowed OFF**; pre-flight curl returns 200)
- [x] Admin dashboard links to pending queue
- [x] Approve application → Keycloak user exists in `wise-k8s`
- [x] Applicant email with sign-in instructions + temporary password
- [x] First OIDC login succeeds (password change prompt from Keycloak; logout + re-login)
- [x] `admin_action_audit` row for approve (implicit in successful approve flow)
- [x] `acruet_user` row created with `key_setup_complete = false` (Phase 7 gate)
- [x] **Bootstrap admin backfill** — Keycloak user ID resolved; manual `INSERT` links bootstrap admin; user hostname shows key-setup prompt (not unlinked message) *(2026-07-18; `brad@bradandmarsha.com`)*
- [ ] **Bootstrap admin backfill** — Phase 7 key setup completes (`user_encryption_key` row; `key_setup_complete = true`)

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

**Status:** ✅ Complete (2026-07-16). V5 schema, JSON API, browser ledger UI with client-side encrypt/decrypt. Cluster verify complete.

### Tasks

1. CRUD ledger accounts (encrypted names); 100 default limit
2. Deposit — 100% allocation across envelopes in one step
3. Withdraw / transfer — warn on overspend; allow negative with warning
4. Archive account at zero balance
5. Append-only transactions; user date + system `created-at`
6. Per-user API write rate limits

### a-cruet

| Component | Notes |
|-----------|--------|
| Flyway `V5__ledger_core.sql` | `ledger_account`, `ledger_transaction`, `ledger_transaction_account`, `ledger_write_attempt`; `acruet_user.ledger_account_limit` |
| `EncryptedBlob` | Validates AES-GCM ciphertext blobs (IV + payload) |
| `LedgerService` | Account CRUD, archive, append transactions, count bumps, write rate limits |
| `LedgerResource` | `GET /ledger` dashboard + `/ledger/accounts`, `/ledger/transactions` JSON API |
| `acruet-crypto.js` | `encryptJson` / `decryptJson` for ledger payloads |
| `acruet-ledger.js` | Envelope list, balances, deposit/withdraw/transfer, archive, overspend warnings |

### API (authenticated, ciphertext in/out)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/ledger/accounts` | List accounts (`encryptedName` base64); `accountCount` / `accountLimit` |
| POST | `/ledger/accounts` | Create account `{ encryptedName }` |
| PUT | `/ledger/accounts/{id}` | Update encrypted name |
| POST | `/ledger/accounts/{id}/archive` | Archive active account |
| GET | `/ledger/transactions` | List by `?from=&to=&accountId=` |
| POST | `/ledger/transactions` | Append `{ transactionType, transactionDate, encryptedPayload, accountIds[] }` |

**Encrypted transaction payload (client JSON before AES-GCM):** `{ memo, totalCents, lines[{ accountId, amountCents }] }` — deposit lines positive; withdraw/transfer use signed cents.

**Write rate limits:** 30/minute, 200/hour per user (`ledger_write_attempt`).

### Verify

- [x] Unlock key → open `/ledger`
- [x] Create 2+ envelopes
- [x] Deposit — single envelope and multi-envelope allocation (100% of total)
- [x] Withdraw — single envelope and multi-envelope; withdraw past balance → warning shown; negative balance displayed
- [x] Transfer — single destination and multi-envelope split
- [x] Archive zero-balance envelope
- [x] DB: `ledger_account.encrypted_name` and `ledger_transaction.encrypted_payload` are ciphertext; `acruet_user.ledger_account_count` / `transaction_count` updated

---

## Phase 9 — Ledger UI polish

**Goal:** Non-functional ledger UX improvements — gathered item-by-item via Q&A before client-side reports.

**Status:** ✅ Complete on cluster (2026-07-17). Items 1–9 verified. Item 10 implemented; cluster verify deferred (may occur naturally when inviting family — see item 10). Post-verify fix: mobile public-nav no longer overlaps hero.

### Items

#### 1. Unauthenticated landing — upper-right Sign up / Sign in buttons

**Goal:** Replace text hyperlinks on the public landing page with ledger-style primary buttons in the upper-right corner, while keeping the centered logo/title hero.

**Decisions (Q&A):**

| # | Topic | Decision |
|---|--------|----------|
| 1 | Header layout | **B** — keep centered logo/title hero; float **Sign up** / **Sign in** in the upper-right of the viewport |
| 2 | Button labels | **B** — **Sign up** → `/signup`, **Sign in** → `/auth/login` |
| 3 | Button styling | **C** — both use primary (accent) button style; placement distinguishes them (match ledger `button` styles from `PageStyles.formCss()`) |
| 4 | Page scope | **A** — **Sign up** / **Sign in** upper-right on anonymous **`/`** only; not on `/signup`, `/signup/verify`, or auth redirect routes |
| 5 | Authenticated home | **Resolved via item 4** — authenticated pages use initials avatar, not Sign up / Sign in |

**Behavior:**

- Remove the in-body `<p class="actions">` text links from the unauthenticated landing content.
- Add a fixed or absolutely positioned control cluster (upper-right) with two `<button>`-styled links (or `<a class="button">` equivalent) using existing ledger/accent styles.
- Centered header (tile image, app name, subtitle) unchanged.
- Public nav appears **only** when `LandingResource` renders the anonymous marketing page at **`/`** — not on signup/verify flows (user is already applying or awaiting approval).

**Out of scope:** Sign up / Sign in on `/signup` or `/signup/verify`; authenticated surfaces (avatar menu per item 4).

**Verify:**

- [x] Anonymous visit to `/` shows centered hero plus **Sign up** / **Sign in** as accent buttons in the upper-right
- [x] Buttons navigate to `/signup` and `/auth/login`
- [x] No duplicate sign-up/sign-in links in the main content area

#### 2. Sticky footer — bottom of viewport on short pages

**Goal:** Footer (Proverbs verse) always appears below main content; on short pages it sits at the bottom of the viewport instead of floating mid-page.

**Decisions (Q&A):**

| # | Topic | Decision |
|---|--------|----------|
| 1 | Scope | **A** — all HTML pages in user and admin apps via shared `PageLayout` |
| 2 | Short-page layout | **A** — header and main stay at the top; flexible space between main and footer; footer pinned to bottom of viewport |

**Behavior:**

- Implement sticky-footer layout in `PageLayout` / `PageStyles` (e.g. `body` min-height `100vh` + flex column; `main` grows to fill remaining space).
- When content exceeds viewport height, footer follows content normally (user scrolls to reach it).
- When content is shorter than the viewport, footer remains at the bottom edge of the display area.

**Out of scope:** Footer copy or styling changes.

**Verify:**

- [x] Unauthenticated landing (`/`) — footer at bottom of viewport, not ~mid-page
- [x] Short page (e.g. home, key setup) — same behavior
- [x] Long page (e.g. `/` with many envelopes) — footer below all content after scroll
- [x] Admin pages (`/`, `/approvals`) — same behavior

#### 3. Unauthenticated landing — marketing content from README

**Goal:** Turn the public landing page into a product marketing page for visitors, using README content adapted for the web.

**Decisions (Q&A):**

| # | Topic | Decision |
|---|--------|----------|
| 1 | Content scope | **B** — include **What it does**, **How your privacy works**, and **Getting access**; lightly edited for web (shorter paragraphs, same meaning) |
| 2 | Reports bullet | **A** — keep **View reports** in the feature list (intended capability, README-aligned) |
| 3 | README intro | Include README opening **except the Proverbs quote** (footer already shows it): envelope-budgeting description, **cruet** metaphor, **“accrue it”** wordplay |
| 4 | Intro placement | **B** — own **About a-cruet** `<h2>` section above the three main sections |

**Include (from README):**

- **About a-cruet** — intro paragraphs (no proverb blockquote)
- **What it does** — envelope explanation, feature bullets, closing “not full accounting / bank connection” line
- **How your privacy works** — client-side encryption, passphrase, recovery file, admin metadata limits
- **Getting access** — invite-by-approval steps and re-apply / rejection notes

**Exclude:**

- Proverbs 21:20 quote (header/footer cover branding)
- **For builders** (`PRODUCT.md` / `ROLLOUT.md` links)
- **To Do list**
- Current minimal body copy (`Allocate money…`, hint about Keycloak, in-body action links — superseded by items 1 and 3)

**Behavior:**

- Replace `LandingResource.publicPage()` body with structured marketing sections (`<h2>`, paragraphs, `<ul>` where appropriate).
- Tone: non-technical, same audience as README.
- **Sign up** / **Sign in** actions remain in upper-right per item 1; feature bullets may still mention **Apply for access** and signing in descriptively (button labels stay **Sign up** / **Sign in** per consistency review).

**Out of scope:** Authenticated home page; builder docs links.

**Verify:**

- [x] Anonymous `/` shows **About a-cruet** plus three marketing sections with README-aligned content
- [x] No proverb duplicate in main body; no For builders / To Do sections
- [x] Page reads well on mobile (single column, existing `max-width` layout); public nav overlap fixed post-verify
- [x] Works with item 1 nav buttons and item 2 sticky footer

#### 4. Authenticated user menu — initials avatar (upper-right)

**Goal:** Replace “Signed in as …” with a circular initials button in the upper-right (matching item 1 placement). Click opens a dropdown with account and key controls. Shared across all authenticated user-app pages.

**Decisions (Q&A):**

| # | Topic | Decision |
|---|--------|----------|
| 1 | Click behavior | **C** — dropdown menu |
| 2 | Dropdown contents | **Sign out**; read-only identity (name/email); **key status**; **Lock key** or **Unlock key** depending on browser session state; **Rotate key** → `/keys/rotate` when key setup complete |
| 3 | Initials source | **A** — `AcruetUser.displayName` when linked; else Keycloak `given_name` / `family_name` from OIDC token (extend `OidcUser` / token parsing as needed) |
| 4 | Initials fallback | **A** — first two letters of email, capitalized (e.g. **SB**) |
| 5 | Page scope | **B** — all authenticated user-app pages (`/`, `/keys/*`, …); JSON API remains under `/ledger/*` (no HTML dashboard route per item 5) |
| 6 | Key setup incomplete | **A** — key status: “Encryption key not set up”; action link to **`/keys/setup`**; no Lock/Unlock or Rotate until setup complete |
| 7 | Authenticated home body | **Custom** — ~~main area keeps **Ledger** only~~ **Superseded by item 5** — home embeds full ledger UI |

**Behavior:**

- **Avatar:** Circular primary-styled control (ledger/accent button family), upper-right; label = two-letter initials (first + last, uppercase).
- **Dropdown:**
  - Read-only: full name and email (as available)
  - Key status text (live — uses `AcruetCrypto.session` on client where applicable)
  - **Unlock key** — on **`/`** when key locked: same as lock tile, open **inline unlock** (item 5); on other pages navigate to **`/keys/unlock`**. When DEK not in session elsewhere, link to `/keys/unlock`.
  - **Lock key** → `AcruetCrypto.session.lock()` when unlocked
  - **Rotate key** → `/keys/rotate` when `key_setup_complete` (omit until setup complete)
  - **Sign out** → `/auth/logout` (clear crypto session on sign-out where applicable)
- **Key setup gate:** Before `key_setup_complete`, show setup status + link to `/keys/setup` instead of Lock/Unlock/Rotate.
- **No linked a-cruet account:** Avatar + read-only identity + **Sign out** only (no key menu items). Main area shows explicit **account not linked** message including **“Administrators have been alerted.”** (see item 10). Do not show ledger or redirect to key setup.
- **Authenticated home (`/`):** **Superseded by item 5** — embed full ledger UI (not a link).
- Implement as shared layout partial (e.g. extend `UserPageLayout` / `PageLayout`) so `/ledger`, `/keys/*`, etc. render the same control.

**Out of scope:** Admin app pages.

**Verify:**

- [x] Signed-in user sees initials circle upper-right on `/`, `/keys/unlock`, and other authenticated pages
- [x] Initials correct for full name (e.g. Stephen Wise → **SW**); email fallback when names unavailable
- [x] Dropdown shows identity, key status, Lock/Unlock (or setup link when incomplete), Rotate key (when setup complete), Sign out
- [x] Lock key clears session DEK; Unlock navigates to unlock flow; Rotate navigates to `/keys/rotate`
- [x] No duplicate “Signed in as …” or standalone Sign out on home

#### 5. Authenticated home — embed ledger on `/`

**Goal:** For signed-in users with key setup complete, **`/`** is the ledger dashboard. Remove the separate `/ledger` HTML page; keep JSON API routes unchanged.

**Decisions (Q&A):**

| # | Topic | Decision |
|---|--------|----------|
| 1 | `/ledger` HTML route | **C** — remove `GET /ledger` dashboard (returns **404**); keep **`/ledger/accounts`**, **`/ledger/transactions`**, and other JSON API paths |
| 2 | Key locked in browser | **Custom** — show ledger **shell** on `/` with prominent unlock prompt: **lock tile** styled like **wise-home-index** app tiles — lock image at **`/media/lock.png`** on the homelab media host + **“Unlock”** label (no redirect to `/keys/unlock`) |
| 3 | Unlock prompt click | **B** — opens **inline unlock form** on `/` (passphrase field in main area) |
| 4 | Locked layout | **A** — **unlock prompt only**; hide envelopes, actions, and transaction forms until DEK is in session |
| 5 | Dropdown Unlock on `/` | **A** — avatar **Unlock key** opens **inline unlock** on `/` (same as lock tile); navigate to `/keys/unlock` on other pages |

**Behavior:**

- **Unauthenticated `/`:** unchanged marketing page (items 1 and 3).
- **Authenticated, key setup incomplete:** existing `KeySetupFilter` redirect to `/keys/setup`.
- **Authenticated, setup complete, key locked:** render ledger page structure but show only the unlock tile; clicking reveals inline unlock (reuse unlock crypto flow from `/keys/unlock`); on success, reveal full ledger and load data.
- **Authenticated, setup complete, key unlocked:** full ledger UI on `/` (same as current `/ledger` — envelopes, actions, forms).
- Extract shared ledger HTML/JS wiring from `LedgerResource` into a reusable fragment used by `LandingResource` (or equivalent); delete duplicate `GET /ledger` HTML handler.
- Remove ledger nav chrome superseded by item 4 (`Home · Unlock key` links, etc.).
- Update `acruet-ledger.js` unlock gate: no hard redirect to `/keys/unlock?next=/ledger`; use inline unlock on `/` instead. Update any `next=` deep links to `/` where applicable.
- Unlock tile lock image: **`MediaSettings.fromEnvironment().tileImageUrl("/media/lock.png")`** — same as header tile image; **do not hardcode** the media hostname. Host comes from **`ACRUET_MEDIA_HOST`** (default `media.home.bradandmarsha.com`).

**Supersedes:** Item 4 decision #7 (Ledger-only link on home).

**Out of scope:** JSON API path changes; admin app.

**Verify:**

- [x] Signed-in user with key setup complete and DEK unlocked sees full ledger on `/`
- [x] `GET /ledger` HTML returns **404** (API paths under `/ledger/*` still work)
- [x] Key locked: unlock tile shows lock image from media host via `MediaSettings` / `ACRUET_MEDIA_HOST`; ledger panels hidden; inline unlock works; ledger appears after unlock
- [x] Unauthenticated `/` still shows marketing content
- [x] `/keys/unlock` remains available for direct navigation / dropdown link

#### 6. Ledger heading — combine envelope count

**Goal:** On the ledger UI (embedded at `/` per item 5), merge the **Envelopes** section title and the envelope limit hint into one heading line.

**Decisions (Q&A):**

| # | Topic | Decision |
|---|--------|----------|
| 1 | Styling | **B** — `<h2>` with muted parenthetical: **Envelopes** at normal heading weight; **`(x of y)`** in hint/muted color (`var(--muted)`) |

**Behavior:**

- Replace separate `<h2>Envelopes</h2>` and `#accountLimitHint` paragraph with a single heading, e.g. **`Envelopes`** **`(3 of 100)`**.
- **`x`** = `accountCount`, **`y`** = `accountLimit` from `/ledger/accounts` API (not hardcoded).
- Update `acruet-ledger.js` `refresh()` to set the combined heading text instead of a separate hint line.
- Remove the standalone `#accountLimitHint` element (or repurpose as part of the heading).

**Format:** `Envelopes (x of y)` — drop the words “envelopes in use”.

**Out of scope:** Changing the account limit default or API.

**Verify:**

- [x] Ledger on `/` shows **Envelopes (x of y)** with muted parenthetical
- [x] Count updates after creating an envelope
- [x] No separate “x of y envelopes in use” line below the heading

#### 7. Ledger — remove “Actions” heading

**Goal:** On the authenticated ledger at `/`, remove the **Actions** section title; keep the action buttons.

**Decisions (Q&A):**

| # | Topic | Decision |
|---|--------|----------|
| 1 | Replacement | **A** — no section heading; **New envelope**, **Deposit**, **Withdraw**, and **Transfer** buttons remain (top and bottom rows per item 8) |

**Behavior:**

- Remove `<h2>Actions</h2>` from the ledger HTML fragment (`#opsPanel` or equivalent).
- Preserve button row and `#formPanel` behavior unchanged.
- Adjust spacing only if needed so the button row still reads clearly without the heading.

**Verify:**

- [x] Ledger on `/` has no **Actions** heading
- [x] All four action buttons still visible and functional below envelopes

#### 8. Ledger — duplicate action buttons above envelope list

**Goal:** When the envelope list is long, users should reach **New envelope**, **Deposit**, **Withdraw**, and **Transfer** without scrolling past every envelope.

**Decisions (Q&A):**

| # | Topic | Decision |
|---|--------|----------|
| 1 | Placement | **A** — duplicate button row **between Envelopes heading and list**; **keep** existing row **below** the list |
| 2 | Scroll behavior | **A** — top row **scrolls away** with heading/list; bottom row for use after scrolling |

**Behavior:**

- Two identical button rows wired to the same actions / `#formPanel` (shared IDs or delegated click handlers — implement without double-submit quirks).
- Order: **Envelopes (x of y)** heading → **top buttons** → envelope list (+ total row) → **bottom buttons** (form replaces browse UI per item 9 when open).
- Same styling as current ledger action buttons.

**Verify:**

- [x] Top and bottom button rows both open deposit/withdraw/transfer/create flows correctly
- [x] With 7+ envelopes, top buttons reachable without scrolling; bottom buttons reachable after scrolling to end of list
- [x] No duplicate form submissions from either row

#### 9. Ledger forms — replace main content instead of expand below

**Goal:** When an action button opens a form, the form should **replace** the ledger main content instead of appearing below the envelope list at the bottom of the page.

**Decisions (Q&A):**

| # | Topic | Decision |
|---|--------|----------|
| 1 | Hidden while form open | **A** — hide envelope heading, both button rows, and envelope list; show **only** the form (title, fields, Save/Cancel) |
| 2 | Return to ledger | **A** — **Cancel** and **successful Save** both close the form and restore the full ledger view |

**Behavior:**

- Wrap ledger “browse” UI (`#accountsPanel`, both `#opsPanel` rows per item 8) in a container toggled hidden when `#formPanel` is open.
- `#formPanel` occupies the main content area (not appended below a long list).
- Opening a form from **either** top or bottom button row uses the same replace behavior.
- Preserve existing validation, form error banner, and submit logic.
- Page header (site tile/title), avatar menu (item 4), and footer unchanged.

**Verify:**

- [x] Click **Deposit** (or any action) — envelope list and buttons disappear; form shown in main area without scrolling
- [x] **Cancel** restores envelope list + buttons
- [x] **Save** (success) restores envelope list + buttons with updated data
- [x] Form error (e.g. allocation mismatch) keeps form visible; browse UI stays hidden

#### 10. Unlinked Keycloak session — user message + anomaly record

**Goal:** Handle signed-in users with a valid OIDC session but **no `acruet_user` row** (partial approve failure, manual Keycloak user, bootstrap admin before user row exists, recreated Keycloak subject, etc.). Phase 11 task 2 (grant admin only for existing `acruet_user`) removes the routine “admin on user host without a user row” path.

**Decisions (consistency review Q4):**

| # | Topic | Decision |
|---|--------|----------|
| 1 | UX | **B** — initials **avatar** (identity + Sign out); main area: short **account not linked** message |
| 2 | User copy | Message must state that **administrators have been alerted** |
| 3 | Admin notification | **Phase 11 task 9** — full admin alert workflow; Phase 9 records the anomaly server-side on detection |

**Behavior:**

- **`/`** (and other authenticated pages as needed): no ledger, no key-setup redirect for this state (`KeySetupFilter` / landing logic must not send unlinked users to `/keys/setup`).
- On first detection per sign-in (or session), persist an anomaly record (e.g. `admin_action_audit` or dedicated table) with Keycloak subject, email, timestamp.
- User-visible text explains the account is not linked and **administrators have been alerted**.
- Avatar dropdown: identity + Sign out only.

**Depends on:** Phase 11 task — admin alert delivery for this anomaly.

**Cluster verify:** Deferred — no synthetic test run. Implementation shipped; confirm naturally if someone signs in via Keycloak before admin approval creates an `acruet_user` row (e.g. family invite timing). Expected UX: avatar + “administrators have been alerted”, no ledger, no `/keys/setup` redirect; one row per session in `login_anomaly`.

**Verify:**

- [ ] Simulated unlinked login shows avatar + message with “administrators have been alerted” *(deferred — see above)*
- [ ] No ledger, no `/keys/setup` redirect *(deferred)*
- [ ] Anomaly recorded server-side once per event *(deferred; query `login_anomaly` when it occurs)*

### Consistency review (resolved)

| # | Topic | Decision |
|---|--------|----------|
| 1 | Public Sign up / Sign in scope | **A** — anonymous **`/`** only (item 1) |
| 2 | Unlock on `/` from avatar | **A** — inline unlock, same as lock tile (items 4–5) |
| 3 | `GET /ledger` HTML | **404** (item 5) |
| 4 | Unlinked Keycloak session | **B** + admin alert in Phase 11 (item 10) |
| 5 | Marketing “Apply for access” vs **Sign up** button | **A** — keep both (descriptive bullet vs action label) |

### Verify

- [x] Items 1–9 verified on cluster (2026-07-17)
- [ ] Item 10 — deferred (documented above; Phase 11 adds admin alert delivery)

---

## Phase 10 — Client-side reports

**Goal:** CSV export and stacked area chart — 100% browser-side decryption.

**Status:** ✅ Complete and verified on cluster (2026-07-18).

### Deliverables

1. **Reports hub** — **Reports** button in both ledger action rows opens a hub with two wise-home-index-style tiles (**Transactions**, **Balance chart**); images from `MediaSettings.tileImageUrl` (`/media/transactions.png`, `/media/report-graphs.png`); **Back to reports** / **Back to envelopes** navigation.
2. **Transaction report** — date range + envelope checkboxes; **Show report** decrypts ledger lines in the browser and renders an on-page **table** (Date, Type, Memo, Envelope, Amount; negative amounts styled like the ledger). **Download CSV** is always available (secondary button); uses the same filters and row data as the table, with or without showing the table first.
3. **Balance chart** — date range + envelope checkboxes; **Show chart** renders a stacked area chart (Chart.js 4.4.1 vendored under `/static/js/`); all aggregation client-side after decrypt.
4. **UX enhancements (post-ship):**
   - **Chart size** — chart canvas **640px** tall (2× original); main content **max-width 80rem** while the chart panel is open (`page--report-chart`).
   - **Tile icon contrast** — report tile images use a **white background + padding** so dark artwork reads on the dark tile card.
   - **Transaction table + CSV** — primary action is **Show report** (table); **Download CSV** is independent (same filters, no need to show the table first).
   - **Static asset cache bust** — ledger report scripts load with a `?v=` query param so JS updates are not stuck behind browser cache after deploy.

### UX

- **Reports** button in both ledger action rows (with New envelope / Deposit / Withdraw / Transfer).
- Clicking **Reports** replaces the browse UI with a **Reports** hub: two wise-home-index-style tiles:
  - **Transactions** — tile image `MediaSettings.tileImageUrl("/media/transactions.png")` (white-backed icon on tile)
  - **Balance chart** — tile image `MediaSettings.tileImageUrl("/media/report-graphs.png")` (white-backed icon on tile)
- Each tile opens a report runner (date range + envelope checkboxes); **Back to reports** / **Back to envelopes** navigation.
- **Transactions:** **Show report** → on-page table; **Download CSV** anytime (same filters, independent of table).
- **Balance chart:** wider main column when chart is open; tall stacked area chart.
- All decryption, table rendering, CSV generation, and chart aggregation happen in the browser only.

### Tasks

1. API returns ciphertext blobs filtered by date range + account scope *(existing `/ledger/transactions?from=&to=`)*
2. Browser decrypts, aggregates, renders stacked area chart (Chart.js)
3. Browser decrypts and renders transaction report **table**; CSV download from the same decrypted rows (independent of table)

### Verify

- [x] **Reports** button appears in top and bottom action rows
- [x] Reports hub shows two tiles with media-host images (readable on dark tiles)
- [x] **Show report** displays transaction table for a date range + envelope selection
- [x] **Download CSV** matches the on-screen table when both are run with the same filters; works without **Show report**
- [x] Balance chart matches envelope balances over time (640px chart, wider page while open)
- [x] No plaintext amounts in server logs or API responses

---

## Phase 11 — Admin ops (suspend, offboard, cron)

**Goal:** Remaining admin & abuse workflows from `PRODUCT.md` Section 6.

### Decisions (pre-implementation)

| # | Topic | Decision |
|---|--------|----------|
| 1 | Grant `a-cruet-admin` | **Only for existing user-app users** — admin UI grant/revoke applies to identities that already have an `acruet_user` row (approved + provisioned). No Keycloak-only admin identities via the app. |
| 2 | Bootstrap | **Manual Keycloak console** remains the exception for the first admin(s) (`PRODUCT.md` #46). Link them to the user app via **Phase 6 bootstrap admin backfill** (manual `acruet_user` insert — signup/approve cannot run when Keycloak user already exists). Until linked, rare unlinked user-app visits are expected (Phase 9 item 10). |
| 3 | Login anomaly scope | Phase 9 item 10 + task 9 below remain for pending approval, partial provision failures, and bootstrap — not routine admin workflow after task 2. |

### Tasks

1. User list with operational counts (accounts, transactions, last active)
2. Grant/revoke `a-cruet-admin` via admin UI → Keycloak Admin API — **eligible users only:** must have an existing `acruet_user` row; UI selects from provisioned users (e.g. user list / email search), not arbitrary Keycloak accounts
3. Suspend: disable Keycloak user + email with admin-set duration
4. CronJob: auto-unsuspend when suspension ends
5. Offboard: email 7-day export window
6. Client-side decrypted export (CSV/JSON) during window
7. CronJob: on export complete or 7-day expiry → disable Keycloak + purge a-cruet data
8. Admin unblock for twice-rejected emails
9. **Unlinked login anomaly** — when a Keycloak session has no matching `acruet_user` (see Phase 9 item 10): alert administrators (email and/or admin UI queue); include Keycloak subject, email, and time; dedupe repeated hits in the same session where practical

### Verify

- Suspend → user cannot log in; auto-restore after N days
- Offboard → export works; data purged after trigger
- Admin action audit entries present
- Grant admin → only succeeds for users with `acruet_user`; granted admin can use **both** user and admin hostnames without unlinked user-app state
- Unlinked Keycloak login (non-routine paths) → admin alerted; user app message matches Phase 9 item 10

---

## Phase 12 — CI/CD + Flux image automation

**Merged into Phase 5** (2026-07-14). Continuous integration lives in `a-cruet/.github/workflows/`; Flux image automation manifests live in `wise-k8s/iac/kustomize/acruet/overlays/image-automation.yaml`. See Phase 5 deploy and verify steps.

---

## Phase 13 — Index tiles + E2E verification

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

## Phase 14 — Non-technical README summary ✅ complete (2026-07-12)

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
9. Phase 8 ledger ✅
10. Phase 9 ledger UI polish ✅
11. Phase 10 reports ✅
12. Phase 11 admin ops
13. Phase 13 E2E
14. Phase 14 README summary (can be drafted anytime; finalize after product stabilizes)

**Parallel work:** Keycloak Phase 6–7 (HA + observability) does not block a-cruet Phases 1–3.

---

## Suggested repo changes

| Repo | Path | Change |
|------|------|--------|
| `a-cruet` | `README.md` | Non-technical product summary (Phase 14) |
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
