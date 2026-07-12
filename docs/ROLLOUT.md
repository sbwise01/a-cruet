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
ingress-nginx-internal  ──►  admin.acruet.home.bradandmarsha.com  ──►  acruet-admin (Tomcat, 1 replica)
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
| Admin hostname | `admin.acruet.home.bradandmarsha.com` |
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
| 2 — `acruet-cnpg` database | Pending |
| 3 — Platform deploy (shells + ingress + secrets) | Pending |
| 4 — OIDC sign-in (user + admin) | Pending — pairs with `KEYCLOAK.md` Phase 5 |
| 5 — Signup + SMTP + verification | Pending |
| 6 — Admin approval + Keycloak provisioning | Pending |
| 7 — Client encryption + key lifecycle | Pending |
| 8 — Ledger core | Pending |
| 9 — Client-side reports | Pending |
| 10 — Admin ops (suspend, offboard, cron) | Pending |
| 11 — CI/CD + Flux image automation | Pending |
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

### Manifests (`wise-k8s/iac/kustomize/acruet/`)

| Resource | Notes |
|----------|--------|
| Namespace | `acruet` (or `default` per homelab convention) |
| Deployments | `acruet-user` (2 replicas), `acruet-admin` (1 replica) |
| Services | ClusterIP :8080 |
| Certificates | user + admin hostnames |
| Ingress (user) | `ingressClassName: nginx`, public |
| Ingress (admin) | `ingressClassName: nginx-internal` |
| Secrets (SOPS) | DB creds, OIDC placeholders, SMTP placeholder |
| DB credential sync | Optional CronJob if secret cross-namespace (mirror keycloak pattern) |

### wise-home-index annotations

- User tile: public scope
- Admin tile: private scope (`index.home.bradandmarsha.com/*` annotations)

### Verify

```bash
flux get kustomizations acruet
curl -sI https://acruet.home.bradandmarsha.com/health
curl -sI https://admin.acruet.home.bradandmarsha.com/health   # from LAN
```

---

## Phase 4 — OIDC sign-in (user + admin)

**Goal:** Authenticated sessions via Keycloak on both hostnames.

**Pairs with:** [`KEYCLOAK.md` Phase 5](../wise-k8s/KEYCLOAK.md#phase-5--oidc-client--a-cruet-integration).

### Tasks

1. Apply `KeycloakOIDCClient` manifests (`acruet`, `acruet-admin`)
2. Store `acruet` client secret in SOPS; mount into both deployments
3. Implement OIDC authorization code flow → `/auth/callback` → Tomcat session
4. Admin WAR: after OIDC, require realm role `a-cruet-admin` (403 otherwise)
5. Bootstrap first admin: assign `a-cruet-admin` in Keycloak console manually

### Verify

| Check | Expected |
|-------|----------|
| Unauthenticated `/` on user host | Redirect to Keycloak login |
| Successful login | Session cookie; landing page |
| Admin host without role | 403 after OIDC |
| Admin with role | Admin dashboard shell loads |
| Logout | Session cleared |

---

## Phase 5 — Signup + SMTP + verification

**Goal:** Public applicant flow without Keycloak account.

### Tasks

1. Public signup form: name, email, reason, phone, mailing address
2. Jakarta Mail + Proton SMTP from SOPS secret (`smtp.protonmail.ch:587`, STARTTLS)
3. Email verification token + link
4. Pending application queue in Postgres (plaintext metadata)
5. Rate limits: per-IP and per-email signup throttling (homelab defaults)
6. Re-apply rules: 7-day cooldown; block after two rejections

### Verify

- Submit application → verification email received
- Click verify link → status `pending_approval`
- No Keycloak user created yet
- Duplicate signup throttling behaves as configured

---

## Phase 6 — Admin approval + Keycloak provisioning

**Goal:** Admin queue → approve creates Keycloak user + initial a-cruet records.

### Tasks

1. Admin UI: pending applications list
2. Approve: `acruet-admin` client credentials → Keycloak Admin API
   - Create user in realm `wise-k8s`
   - Set temporary password
   - Create a-cruet user row + empty ledger scaffold
   - Email sign-in link (Proton SMTP)
3. Reject: mark rejected + rejection email
4. Audit log: admin approve/reject actions in Postgres

### Verify

- Approve application → Keycloak user exists
- Applicant email with sign-in instructions
- First OIDC login succeeds; prompted for key setup (Phase 7 gate)

---

## Phase 7 — Client encryption + key lifecycle

**Goal:** Mandatory passphrase-derived KEK, DEK wrap, recovery file before ledger use.

### Tasks

1. Browser: Web Crypto — AES-256-GCM, KEK from passphrase (never sent to server)
2. One DEK per user; store wrapped DEK server-side
3. Recovery file export + confirmation gate
4. Session unlock with idle timeout (default ~30 min)
5. Key rotation: re-wrap DEK with new KEK

### Verify

- New user blocked from ledger until key + recovery confirmed
- Server DB contains wrapped DEK + ciphertext only — no passphrase
- Key rotation succeeds without re-encrypting all records

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

**Goal:** Automated image build and GitOps tag bumps.

### a-cruet repo (GitHub Actions)

- Build both WARs
- Build and push `sbwise/acruet-user`, `sbwise/acruet-admin`
- Tag with semver on release

### wise-k8s

| Resource | Path |
|----------|------|
| `ImageRepository` | `acruet/overlays/image-automation.yaml` (×2 images) |
| `ImagePolicy` | Semver range per image |
| `ImageUpdateAutomation` | Bump overlay image tags via setters |

**Pattern:** `iac/kustomize/wise-home-index/overlays/image-automation.yaml`

### Verify

```bash
flux get image repository,policy,update automation | grep acruet
# Push new tag → fluxcdbot commits tag bump to wise-k8s
```

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
6. Phase 5 signup + SMTP
7. Phase 6 admin approval
8. Phase 7 encryption
9. Phase 8 ledger
10. Phase 9 reports
11. Phase 10 admin ops
12. Phase 11 CI/CD (can start after Phase 3; fully validate by Phase 12)
13. Phase 12 E2E
14. Phase 13 README summary (can be drafted anytime; finalize after product stabilizes)

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
| `wise-k8s` | `iac/kustomize/fluxcd/kustomizations/acruet*.yaml` | Flux wiring |

---

## Risks and gotchas

| Risk | Mitigation |
|------|------------|
| **Key loss** | Mandatory recovery file; clear UX warnings |
| **Admin cannot decrypt** | By design — support is metadata-only |
| **OIDC redirect mismatch** | Lock URIs in GitOps; test both hostnames |
| **Internal admin DNS** | `admin.acruet` only on LAN; Keycloak redirect still works from browser on trusted network |
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
