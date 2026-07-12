# A Cruet

## Decisions

Product and technical decisions captured during requirements clarification. Each subsection is locked before moving to the next.

### 1. Identity & signup flow ✅ locked (2026-07-12)

#### Applicant journey

| Step | Decision |
|------|----------|
| 1 | **Public unauthenticated** signup page — no Keycloak account exists yet |
| 2 | Applicant submits profile + email |
| 3 | **a-cruet** sends **verification email** (app SMTP, not Keycloak) |
| 4 | After email verified, application is **queued for admin approval** |
| 5 | Pending applications stored in **Postgres as plaintext metadata** (name, email, status, etc.); ledger data is encrypted later |

#### Admin approval

| Step | Decision |
|------|----------|
| 6 | Admin reviews queue on the **admin hostname** |
| 7 | On approve, **a-cruet server**: calls **Keycloak Admin API** to create user in realm **`wise-k8s`**; sets **temporary password** via Admin API; creates initial **a-cruet data objects**; **emails** applicant a sign-in link |

#### Admin surface

| Item | Decision |
|------|----------|
| Hostname | **Separate ingress hostname** for admin vs users |
| OIDC | **Same** `wise-k8s` realm and **same OIDC client** on both hosts |
| Authorization | **Keycloak realm role** (e.g. `a-cruet-admin`) checked server-side on admin hostname; non-admins blocked |

#### Keycloak implications (Phase 5, when app is deployed)

- **Confidential** OIDC client (server-side Tomcat sessions)
- Redirect URIs for **both** user and admin hostnames
- **Service account** (client credentials) for a-cruet → Keycloak Admin API (user provisioning on approve)
- Realm role **`a-cruet-admin`** for admin authorization
- **a-cruet SMTP** for verification + approval emails (separate from Keycloak SMTP)
- **No** Keycloak self-registration for applicants

### 2. Hostnames & OIDC ✅ locked (2026-07-12)

| Item | Decision |
|------|----------|
| User hostname | **`acruet.home.bradandmarsha.com`** |
| Admin hostname | **`admin.acruet.home.bradandmarsha.com`** (nested subdomain) |
| OIDC client type | **Confidential** — client secret in k8s Secret; Tomcat server-side sessions |
| OIDC client ID | **`acruet`** |
| Redirect path | **`/auth/callback`** on both hostnames |
| Redirect URIs | `https://acruet.home.bradandmarsha.com/auth/callback`, `https://admin.acruet.home.bradandmarsha.com/auth/callback` |
| Admin API client | **Separate service account** — client ID **`acruet-admin`**, client credentials grant for user provisioning on approval |
| Deployment | **Two Tomcat deployments** — separate user and admin instances |
| User ingress | **Public** — `ingressClassName: nginx` (same LB as Plex, Keycloak, etc.) |
| Admin ingress | **Internal** — `ingressClassName: nginx-internal` (LAN / homelab network only) |
| wise-home-index | **Tiles for both** hostnames — user tile **public**; admin tile **private** (visible on index only from trusted/private network per wise-home-index rules) |
| Keycloak realm | **`wise-k8s`** @ `auth.home.bradandmarsha.com` |

### 3. Envelope encryption ✅ locked (2026-07-12)

#### Encryption scope

| Item | Decision |
|------|----------|
| Encrypted (ciphertext only on server) | **All sensitive user data** — ledger account names, transaction memos, amounts, balances, report payloads |
| Plaintext metadata (server may store) | **Keycloak user ID**, record **UUIDs**, **created/updated timestamps**, record **type enums**, plus **operational counts** (ledger account count, transaction count, etc.) for abuse monitoring |
| Pending signup applications | **Plaintext metadata** (Section 1) — separate from ledger encryption |
| Admin visibility | **Operational metadata only** — admins never see decrypted ledger content; counts OK for abuse monitoring |

#### Envelope model

| Item | Decision |
|------|----------|
| Hierarchy | **One user KEK** (master key) → **one DEK** for all user ledger data |
| Key rotation | Re-wrap the **same DEK** with a new KEK — no full re-encryption of all records |
| Algorithms | **AES-256-GCM** for data; **AES-256-KW** or **RSA-OAEP** for DEK wrapping (Web Crypto) |

#### Client key management

| Item | Decision |
|------|----------|
| KEK derivation | **Passphrase-derived** — passphrase never sent to server |
| Recovery | **Mandatory exportable recovery file** at key creation; app **blocks ledger use** until backup is confirmed |
| Key loss | **Unrecoverable** without passphrase or recovery file — user warned explicitly; admins cannot decrypt |
| Session unlock | Passphrase unlocks KEK for a **configurable idle timeout** (implementation picks default, e.g. 30 min) |
| First-login gate | **Mandatory key creation** on first login — key + recovery backup before any ledger feature |

#### Reports

| Item | Decision |
|------|----------|
| CSV + stacked area chart | **100% client-side** — API returns ciphertext; browser decrypts, aggregates, renders/downloads |

### 4. Ledger model ✅ locked (2026-07-12)

#### Envelope semantics

| Item | Decision |
|------|----------|
| Model | **Pure envelope** — deposits split directly across 1+ envelopes; no separate "Ready to Assign" pool |
| Deposit rule | **100% allocation required** — sum of splits must equal deposit total in one step |
| Withdraw / transfer | **Warn on overspend** — allowed to drive negative envelope balance, with warning |
| Negative balances | **Allowed with warning** — not blocked |

#### Currency & amounts

| Item | Decision |
|------|----------|
| Currency | **USD only** (v1) |
| Storage | **Integer cents** in encrypted transaction payloads |

#### Ownership & scope

| Item | Decision |
|------|----------|
| v1 | **Single-user** — one Keycloak user, one isolated ledger |
| Future | **Design for shared household** — schema/API should not preclude multi-user ledger access later |

#### Accounts

| Item | Decision |
|------|----------|
| Limit | **100 accounts default** per user — admin can raise per user |
| Removal | **Archive only** — account must be **zero balance** to archive; hidden from active list |

#### Transactions

| Item | Decision |
|------|----------|
| Immutability | **Append-only** — no edits/deletes; corrections via **reversing/adjustment** entries |
| Dates | **Both** — user-specified transaction date (default today) + system **created-at** (UTC) for audit |

### 5. Engineering & deployment ✅ locked (2026-07-12)

#### Build stack

| # | Item | Decision |
|---|------|----------|
| 32 | Java / Tomcat | **Java 17** + **Tomcat 10.1** — match `wise-home-index` (`maven.compiler.release=17`, `tomcat:10.1-jre17-temurin`) |
| 33 | Build system | **Maven** — multi-module project |
| 34 | Code structure | **Two WARs** (user + admin) + **shared library module(s)** for domain, API, and crypto client stubs |
| 35 | REST stack | **JAX-RS Jersey 3.x** on Tomcat — match `wise-home-index` (`jersey.version` 3.1.5) |
| 36 | Postgres | **Dedicated `acruet-cnpg`** cluster on wise-k8s — mirror `keycloak-cnpg` / `plex-cnpg` pattern |
| 37 | GitOps location | **Kustomize manifests in wise-k8s** — `iac/kustomize/acruet/`, `iac/kustomize/acruet-cnpg/`; app code in **a-cruet** repo |

#### Operations & deployment

| # | Item | Decision |
|---|------|----------|
| 38 | Outbound email | **Proton Mail SMTP submission** — `smtp.protonmail.ch:587`, **STARTTLS**, dedicated **SMTP token** (not mailbox password); sender **`noreply@bradandmarsha.com`**; SPF/DKIM/DMARC via existing Proton + Route53 setup |
| 39 | `acruet-cnpg` sizing (v1) | **3 instances**, **20Gi** per instance, **`csi-rbd-sc`** |
| 40 | Tomcat replicas (v1) | **2** user replicas, **1** admin replica |
| 41 | CI/CD | **GitHub Actions** in a-cruet repo (build WARs + container images, push to registry) + **Flux image automation** in wise-k8s (same pattern as `wise-home-index`) |
| 42 | Secrets management | **SOPS-encrypted secrets** in wise-k8s — SMTP token, OIDC client secret, DB credentials |

#### SMTP implementation notes

| Item | Detail |
|------|--------|
| Provider | Proton Mail — paid plan with `bradandmarsha.com` custom domain |
| Auth | Username = full sender address; password = per-app SMTP token (e.g. `a-cruet-wise-k8s`) |
| Java client | **Jakarta Mail** with SMTP properties from k8s Secret |
| Scope | **a-cruet app mail only** — verification + approval notices (Section 1); Keycloak SMTP separate |
| Dev (optional) | **Mailpit** in cluster for local/dev overlays — not used in production |

#### wise-k8s implications

- **`acruet-cnpg/`** — CNPG `Cluster` `acruet-db`, Postgres 17 image, 3 instances, 20Gi, `csi-rbd-sc`
- **`acruet/`** — two Tomcat Deployments (user + admin), Ingresses (public user / internal admin per Section 2), cert-manager Certificates, SOPS secrets for SMTP + OIDC
- **Flux** — `ImageRepository` / `ImagePolicy` / `ImageUpdateAutomation` per image (user WAR + admin WAR)
- **Egress** — pods must reach `smtp.protonmail.ch:587` (STARTTLS)

### 6. Admin & abuse ✅ locked (2026-07-12)

#### Signup application

| # | Item | Decision |
|---|------|----------|
| 43 | Applicant form fields | **Name**, **email**, **short reason/message**, **phone number**, **mailing address** — stored as plaintext metadata (Section 1) |
| 44 | Rejection | Admin rejects → **rejection email** sent; application marked **rejected**; **no** Keycloak user created |
| 45 | Re-apply rules | **7-day cooldown** after first rejection; if **rejected twice**, email is **permanently blocked** from re-applying (admin must unblock) |

#### Administrator management

| # | Item | Decision |
|---|------|----------|
| 46 | First admin bootstrap | **Manual** — assign **`a-cruet-admin`** realm role in Keycloak console for initial admin(s) |
| 47 | Grant/revoke admins | **Admin UI** — existing admin grants/revokes **`a-cruet-admin`** via app → **Keycloak Admin API** |

#### User suspension

| # | Item | Decision |
|---|------|----------|
| 49 | Suspension mechanism | **Disable Keycloak login**; data untouched; **email** to user explaining suspension and **length in days** |
| 49b | Suspension duration | **Admin sets** number of days when suspending |
| 49c | End of suspension | **Auto-unsuspend** — app/CronJob re-enables Keycloak user when period ends |

#### User offboarding

| # | Item | Decision |
|---|------|----------|
| 48 | Offboarding flow | Admin initiates offboard → **email** to user with **7-day** data-export window |
| 48a | Data export | User signs in during window → **client-side decrypted export** (CSV/JSON bundle) while key is unlocked |
| 48b | Purge trigger | After **export complete** or **7-day expiration** (whichever first) → **disable Keycloak user** + **purge all a-cruet data** automatically — **no** further admin confirmation required |

#### Abuse prevention & monitoring

| # | Item | Decision |
|---|------|----------|
| 50 | Rate limiting | **Signup limits** (per IP/email) **+ per-user API rate limits** on write endpoints — exact thresholds at implementation (homelab-sensible defaults) |
| 51 | Audit logging | **Admin actions** logged in Postgres **+ per-user activity timestamps** (last login, last transaction activity) — no per-API-call log |
| 52 | Admin dashboard (v1) | **Approval queue** + **user list** with operational counts (accounts, transactions, last active) |
| 53 | Spam controls | **Email verification** + **manual admin approval** only — no CAPTCHA (homelab pragmatism) |

#### Cross-references (already locked elsewhere)

| Topic | Section |
|-------|---------|
| 100 account default limit; admin can raise | Section 4 |
| Plaintext operational counts for abuse monitoring | Section 3 |
| Admins never see decrypted ledger content | Section 3 |

## Product requirements

- Application running in Apache Tomcat written in the Java programming language
- Application operates on both server side and client side
- Client side consists of HTML and JavaScript delivered to a web browser which serves as the front end
- Server side consists of REST based API resources running on the Tomcat server
- The application's primary purpose will be a web based management system for a general ledger for accounting.  The ledger accounts will represent categorizations of savings (i.e. christmas fund, vacation fund, health fund, etc..)
- The application will be integrated with auth.home.bradandmarsha.com for authentication of users of the application
- The application will store data in a cloud native postgres database instance
- The data stored by the application will be encrypted using client side encryption.  The web server will only receive data in encrypted form and store it in the database encrypted.
- The application will contain features to assist the user in creating their encryption key and storing it safely on their client device
- The encryption process will make use of envelope encryption, such that a user can easily rotate their encryption key and still access the data stored in the database without having to re-encrypt all of the data on key rotation.
- The application may be implemented as multiple web service to make code organization simpler
- The application will support different personas
  - The main persona will be users who manage their ledger accounts
  - An administrative persona will manage users
    - sign up approvals
    - usage and abuse monitoring
    - user offboarding
    - assignment of administrative users
- The application may implement different ingress sub-domains for users vs administrators
- The application will support the following user workflows:
  - sign up
    - apply to use a-cruet
    - User to supply basic identifying information, and an email account for verification
    - Queues up a request to a-cruet product administrators to approve the application and create a user account for the users
  - sign in
    - authenticate with auth.home.bradandmarsha.com to use the a-cruet application
    - on successful authentication, user is directed to an a-cruet landing page
  - create or rotate encryption key
  - create ledger account
    - a user may have 1 to N ledger accounts
    - we may limit to 100 total ledger accounts to prevent abuse
  - deposit funds
    - page will allow user to credit portions of the deposit to 1 or more ledger accounts
  - withdraw funds
    - page will allow user to debit portions of the withdraw to 1 or more ledger accounts
  - transfer funds
    - page will allow user to transfer funds from 1 ledger account to 1 or more other ledger accounts
  - create transaction report
    - user will specify date range
    - user will specify 1 or more or all ledger accounts for scope of report
    - report will be CSV like
  - create balance over time report
    - user will specify date range
    - user will specify 1 or more or all ledger accounts for scope of report
    - report will be a stacked area graph
  - rotate encryption key
  - update user credentials
- The application will support the following administrative workflows:
  - approve new user applications
  - assign administrators
  - monitor usage by user

Ask me questions to clarify the product requirements, the technical requirements, engineering principals, and hard constraints.
