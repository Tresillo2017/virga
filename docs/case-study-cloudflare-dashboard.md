# The Cloudflare dashboard today: what a native Android client has to cover

Status date: 2026-11-11. Audience: the app's designer and builder.

Method note: this study was assembled from the research notes gathered today (workers, pages-dns, storage, auth-analytics, android, prior-art-policy) and the verifier verdicts on them. Where a verifier marked a claim wrong, partly wrong, or could not verify it, the correction is used and the uncertainty is stated in the text. The screenshots were described in the brief; the image files were not re-read for this document. Figures attributed to the screenshots (for example the 125.56k total requests, 14.10% cache hit rate, 3.67 build minutes, and 439 ms CPU P90 tile) come from the research notes, not from a fresh look at the images.

---

## 1. Bottom line

**Fully possible with a user token and no backend (read and write):**

- Workers: settings and bindings reads, version upload, gradual split and rollback (both one POST to the deployments endpoint), custom domain and workers.dev/preview toggles, delete.
- Workers Builds: list builds, get one build, read logs (cursor-polled), cancel, trigger a build by branch or commit hash, read and update build settings, list previews.
- Pages: list projects, list deployments with stage status, retry a deployment, custom domains (add, retry validation, remove), production branch and build config changes.
- DNS: record list, create, edit (PATCH), delete, batch, BIND import and export, proxy, TTL, comments, tags. Zone development mode, pause, DNSSEC, zone DNS settings, and the account-level enforce-DNS-only switch.
- R2: bucket list, create, delete, CORS, lifecycle, public access toggle, and object list, get, put and delete over REST. This works only with an Admin-scoped R2 token (see section 3).
- D1: database list, create, rename, delete, undelete (within 7 days), read-replication setting, SQL query, export, time-travel bookmark and restore.
- KV: namespace CRUD, key list, get, put, metadata, delete, bulk put, bulk get, bulk delete.
- Analytics for Workers, D1, R2 and KV through the GraphQL Analytics API.

**Partly possible (works with a stated limit or an unverified step):**

- Live Workers build progress: polling only, with coarse states and no step progress.
- Live tail logs: the tail session endpoint exists, but the transport (WebSocket or otherwise) is not documented.
- Git-connected Pages project creation: the schema accepts a GitHub or GitLab source, but the first GitHub authorization is a browser step.
- Large R2 uploads: multipart is S3-only, so the app needs SigV4 signing or temporary credentials.
- D1 table browsing, row editing and schema changes: these must be emulated with SQL against the query endpoint.
- Workers bindings and secrets writes: these ride the version upload path. Secret endpoints are not verified.
- Workers Builds retry: no retry endpoint is documented (see section 3).
- Access on a Worker, cron triggers, and Pages build logs: endpoints not confirmed, or confirmed only as names.

**Not possible without a server, or without an internal API:**

- Pages rollback and promotion of a preview to production: no API found; the rollback guide documents only the dashboard flow.
- Workers Issues (grouped errors) and Issues automations: no REST endpoint confirmed. The only push path is an MCP event, which is not a phone-reachable API.
- Build status push to the phone: Workers Builds events go through Queues event subscriptions, which need a relay that the no-backend design does not have.
- Registrar renewals, contact edits, and auto-renew: not in the API.
- Recents, Cmd+K search, Ask AI, the usage panel, and tile customisation: no public endpoint. The billable-usage endpoint is daily, self-serve only, and not confirmed to match the dashboard panel.
- Samsung Now Bar placement of a Live Update: unconfirmed, so the design must not depend on it.

---

## 2. How the web dashboard is organised

The dashboard has four navigation layers, based on the screenshots as described in the brief:

1. **Account home.** Domains and Workers quick lists, a recents list, analytics tiles (total requests, Worker invocations, Workers errors, cache hit rate, CPU time P90, build minutes), a time range picker, and a global search.
2. **Account-level product sidebar.** Compute and Storage submenus, with separate pages for Workers & Pages, Containers, Durable Objects, Queues, Workflows, R2, D1, KV and others.
3. **Zone-level sidebar.** Once a domain is open: DNS, Email, SSL/TLS, Security, Caching, Rules, Speed, Traffic, Network. The zone overview shows unique visitors, total requests, percent cached and data served, plus quick actions (development mode, Under Attack mode, Markdown for Agents) and a right-hand panel with registrar details and connected Workers.
4. **Per-resource tabs.** A Worker has Overview, Metrics, Deployments, Observability, Issues, Domains, Access and Settings. An R2 bucket or D1 database has its own tabs (objects, metrics, settings; tables, console, metrics, settings).

**Implications for phone navigation:**

- Use an account switcher on first launch and in the top bar. Multi-account users need a picker, and the account list should be cached per token.
- Use a bottom navigation or drawer that mirrors layer 2: Workers and Pages, DNS (through domains), R2, D1, KV, Analytics, Settings.
- Treat a domain as a zone-level detail screen with sections for DNS, Overview, and the handful of zone toggles the app supports. Deep-link the rest to the browser.
- Use segmented tabs inside a Worker, bucket or database, matching the dashboard. Keep the tab set smaller where the API does not cover a tab (Issues and Access in v1 should be read-only links).
- Put destructive actions behind typed-name or explicit confirmation: delete Worker, delete database, time-travel restore, rollback, cancel build, enforce DNS-only, pause zone, public preview URL.

---

## 3. Product sections

Notation: **R** read, **W** write, **RW** both. "Public" means a documented REST or GraphQL endpoint in the sources read. "Unconfirmed" means the endpoint was not verified against a primary source today.

### 3.1 Workers (including Workers Builds)

| Capability | Dashboard | Access | API support | Endpoint | Notes |
|---|---|---|---|---|---|
| Worker list (name, routes, repo, last commit, requests, CPU) | Workers and Pages list | R | Partial | `GET /accounts/{id}/workers/scripts`; repo and last commit from `GET /accounts/{id}/builds/workers/{worker_tag}`; requests and CPU from GraphQL | Builds endpoints need the worker tag, not the name. One extra call per Worker. |
| Bindings graph | Overview | R (W via version) | Partial | `GET /accounts/{id}/workers/scripts/{name}/settings` | Show as a list in v1. Writes need a new version and deployment. |
| Domains, routes, workers.dev | Domains tab | RW | Public for custom domains and subdomain; routes public | Custom domains `/accounts/{id}/workers/domains`; routes `/zones/{zone_id}/workers/routes`; subdomain `/accounts/{id}/workers/scripts/{name}/subdomain` | Needs Workers Routes Write scoped per zone. Registrar purchase is out of scope. |
| Code editor | Overview | RW | No editor API | Code change is a version upload (below) | Out of scope for v1. |
| Metrics (invocations, errors, CPU, wall time, subrequests) | Metrics | R | Partial | GraphQL `workersInvocationsAdaptive` (beta) | Per-version split and wall time not confirmed in the dataset docs. |
| Asset requests, asset cache hit rate, top hostnames | Metrics | R | Unconfirmed | None confirmed | Mark unavailable in v1. |
| Deployments list (active, history) | Deployments | R | Partial | Deployments list endpoint not verified. Versions list is `GET /accounts/{id}/workers/scripts/{name}/versions` | Verify the list path against the OpenAPI schema before building the screen. |
| Gradual split | Deployments | RW | Public | `POST /accounts/{id}/workers/scripts/{name}/deployments` with `strategy: percentage` and `versions` | Create-only. Percentages must sum to 100. Re-read the active deployment before each change. |
| Rollback | Deployments | RW | Public | Same POST with one version at 100% | Does not change bindings or data. Warn in the UI. Up to 100 recent versions. |
| Upload version without deploying | Deployments | W | Public, beta | `POST /accounts/{id}/workers/workers/{id}/versions` | Beta. Response shape may change. |
| Durable Object code update strategy | Deployments | W | Public | `code_update_strategy` on the deployments POST | Only for Workers that use Durable Objects. |
| Preview URLs and workers.dev toggles | Deployments and Domains | RW | Public | Subdomain API (get, enable, disable) | Exposing previews is security-sensitive. Confirm before changing. |
| Logs search (Workers Logs) | Observability | R | Public | `POST /accounts/{id}/workers/observability/telemetry/keys`, `/values`, `/query` | All three are POST. Query returns STARTED before COMPLETED, so poll. Max 100 results per call. |
| Query Builder saved queries and share links | Observability | RW | Partial | Save and share endpoints not confirmed | Ad hoc queries only in v1. |
| Live tail | Observability (Live) | R | Partial | `POST /accounts/{id}/workers/scripts/{name}/tails` returns `id`, `expires_at`, `url`; also `GET` and `DELETE` on tails | Verifier confirmed the response fields. The docs do not say `url` is a WebSocket or name a subprotocol. Limit is 10 concurrent viewers per Worker, shared with the dashboard and Wrangler. Prepare and heartbeat paths exist (`telemetry/live-tail`, `live-tail/heartbeat`) with bodies not checked. |
| Traces | Observability | R | Partial | Trace view named in the query response; trace detail endpoint not verified | Could be a web view in v1. |
| Observability settings (logs on or off, sampling) | Observability | RW | Partial | Set via Wrangler config and deployed; script settings GET returns it; PATCH not verified | No standalone toggle endpoint confirmed. |
| Issues | Issues | RW | Unconfirmed | No REST endpoint confirmed | Only an MCP event (`cloudflare.alert.workers_observability_real_time_issue`) and a dataset name were found. Keep out of v1 or use a link. |
| Access (Cloudflare Access on a Worker) | Access | RW | Unconfirmed | Zero Trust Access API exists; Worker-specific link not confirmed | Link out. |
| Variables and secrets | Settings | RW | Partial | Bulk secrets API (changelog 2026-06-03); per-Worker secrets path not fetched | Write-only. Never display a value after save. Plain-text vars ride version bindings. |
| Bindings (KV, D1, R2, DO, service, queue, AI) | Settings | RW | Partial | Read via script settings; write via version upload with bindings array plus deployment | Per-resource APIs are public. |
| Cron triggers | Settings | RW | Unconfirmed | Cron trigger API not verified; triggers are part of the deploy config | Defer. |
| Compatibility date, flags, placement, limits | Settings | R (W via version) | Partial | Script settings GET (full field list not read) | Read-only in v1. |
| Tail Worker consumers | Settings | RW | Partial | `tail_consumers` in script metadata | Not the same as the tails subresource. |
| Delete Worker | Settings | W | Public, beta | `DELETE /accounts/{id}/workers/workers/{id}` | Require typed-name confirmation. |
| Create Worker, rename, tags | Settings | RW | Public, beta | `POST /accounts/{id}/workers/workers`; `PUT .../workers/workers/{id}` | Use the UUID form of the id. |

**Workers Builds**

| Capability | Dashboard | Access | API support | Endpoint | Notes |
|---|---|---|---|---|---|
| Connected repo and build config | Builds settings | RW | Public | `POST /accounts/{id}/builds/workers`; `GET` and `PATCH /accounts/{id}/builds/workers/{worker_tag}` | Create needs a GitHub repo id and a `build_token_uuid`. PATCH sends only changed fields. |
| Build token management | Builds settings | R (create not verified) | Public for list | `GET /accounts/{id}/builds/tokens` | A stale token makes builds fail with a specific error. |
| Build history for a Worker | Deployments | R | Public | `GET /accounts/{id}/builds/workers/{worker_tag}/builds` | Returns builds from the last 90 days. Pagination `page` and `per_page` (max 200). The list item schema is loosely typed, so parse defensively. |
| Single build detail | Build details | R | Public | `GET /accounts/{id}/builds/builds/{build_uuid}` | Verifier confirmed this endpoint. The earlier claim that no single-build GET exists is wrong. This is the poll target for Watch build. |
| Build logs | Build details | R | Public | `GET /accounts/{id}/builds/builds/{build_uuid}/logs` with optional `cursor` | Verifier confirmed the path (with the `builds` segment). Cursor-paged, not streamed. Logs kept 90 days. Whether a missing cursor means end of log is unconfirmed. |
| Trigger a build | Manual run | W | Public | `POST /accounts/{id}/builds/triggers/{trigger_uuid}/builds` with `branch` or `commit_hash` (one required) | The trigger UUID list path was not fetched. Verifier confirmed the path. |
| Cancel | Build details | W | Public | `PUT /accounts/{id}/builds/builds/{build_uuid}/cancel` | Confirm before cancelling in the UI. |
| Retry a build | Build details and Deployments | W | No retry endpoint | None | The dashboard has Retry, and GitHub has Rerun (changelog 2025-03-17). The claim that retry can be emulated by re-triggering the same `commit_hash` was not stated in any source read. Could not verify. Do not label the action "Retry" in the app. |
| Previews | Previews | RW | Public | `GET /accounts/{id}/builds/workers/{worker_tag}/previews`; `GET` and `PATCH .../previews/{preview_id}`; `POST .../previews/{preview_id}/builds`; `GET .../builds` | Good fit for a list screen. |
| Deploy hooks | Deploy hooks | W | Partial | Trigger is `POST https://api.cloudflare.com/client/v4/workers/builds/deploy_hooks/{hook_id}` with no auth header | The hook URL is a bearer credential. Do not store or display it. Use the authenticated trigger endpoint. |
| Build events | Not a dashboard feature | R | Push only, via Queues | `cf.workersBuilds.worker.build.started`, `.succeeded`, `.failed`, `.canceled` | Needs a relay. Not usable on a no-backend phone app. |
| Step or stage progress | None in dashboard | R | None | Only status, timestamps and log lines | Show state and elapsed time, not percentages. |
| Superseded queued builds | Builds | R | Behaviour documented in changelog only | The final `build_outcome` of a skipped build is not documented; `skipped` is the likely value | Treat `skipped` as terminal, not as an error. |

**Workers gaps.** Issues, Access, cron editing, secrets endpoints, deployments list path, Worker-level asset metrics, and release markers on charts. The account usage panel is also a gap beyond build minutes. Build minutes can be read from `GET /accounts/{id}/builds/account/limits` (response fields not verified; one source says it reports only on non-paid plans), and no per-minute counter was found.

**Workers Builds gaps.** Retry, step progress, streamed logs, push status, and a documented token-type rule that the method pages do not repeat (see section 4).

### 3.2 Pages

Cloudflare's own docs now call Workers the primary platform. The Pages page says it is the primary platform for building applications and tells users to start new projects on Workers. The Workers best-practices page says Pages continues to work but new features are focused on Workers. Treat Pages as a legacy surface: keep it working, show a migration hint, and do not invest in Pages-only features. The migrate-from-Pages guide and an MCP guide exist for that migration.

| Capability | Dashboard | Access | API support | Endpoint | Notes |
|---|---|---|---|---|---|
| List projects | Workers and Pages list | R | Public | `GET /accounts/{id}/pages/projects` | Paginate. |
| Create project, Git-connected | Import an existing Git repository | W | Partial | `POST /accounts/{id}/pages/projects` with `source` (`github` or `gitlab`) and `build_config` | Verifier: the schema is confirmed. The 2026-11-10 changelog says Cloudflare sends the user to GitHub to authorise the app. Whether that step must be done in a browser was not confirmed. Authorisation is per user. The repo list is now per GitHub user. |
| Create project, Direct Upload | Drag-and-drop or Wrangler | W | Partial | `POST /accounts/{id}/pages/projects` without `source` | Upload path not verified. Direct Upload cannot later switch to Git. Mobile upload of build output is not realistic (limits: 20,000 files via Wrangler, 1,000 drag-and-drop, 25 MiB per file). |
| Production branch change | Build settings, Branch control | RW | Public | `PATCH /accounts/{id}/pages/projects/{name}` with `production_branch` | Required for Direct Upload projects. |
| Build config, env vars, bindings, compatibility | Settings, Build and deployments | RW | Partial | `PATCH` with `build_config` and `deployment_configs` | Secret env vars come back redacted. Sub-fields not verified. |
| Git management (install, repo access, disconnect, pause) | Settings, Builds | RW for branch controls | Partial | Branch controls via PATCH; GitHub App install and repo picker are browser-only per the research notes | First-time connection is a web-assisted flow. |
| Deployments list with stage status | Deployments | R | Public | `GET /accounts/{id}/pages/projects/{name}/deployments` with `env`, `page`, `per_page` | Each item has `latest_stage` and `stages` (queued, initialize, clone_repo, build, deploy) with status. This is the poll source for build progress. No push. |
| Deployment detail | Deployment details | R | Partial | `GET .../deployments/{deployment_id}` | Path not re-verified today. Use list fields to save calls. |
| Build log | Build log | R | Unconfirmed | None found (a guessed URL returned 404) | Show stage status and a browser link to the dashboard log. |
| Retry failed deployment | Retry button | W | Public | `POST .../deployments/{deployment_id}/retry` | Good one-tap action. |
| Rollback production | Deployments menu | W | None found | Rollback guide documents only the dashboard flow (verifier confirmed) | Deep-link to the dashboard. |
| Promote preview to production | Deployments | W | Unconfirmed | None | Dashboard only until verified. |
| Create deployment (upload) | Drag-and-drop or Wrangler | W | Unconfirmed | Not fetched | Out of v1. |
| Delete deployment | Deployment menu | W | Unconfirmed | Not confirmed; the rule about the active deployment is assumed | Verify before shipping. |
| Custom domains | Custom domains tab | RW | Public | `GET` and `POST .../pages/projects/{name}/domains`; `GET`, `PATCH` (retry validation), `DELETE .../domains/{domain_name}` | Show the validation CNAME. If the zone is on Cloudflare, offer to create the record through the DNS API. |
| Delete project | Settings | W | Partial | Expected `DELETE /accounts/{id}/pages/projects/{name}`; REST path not fetched; Wrangler equivalent confirmed | Typed-name confirmation. |
| Functions and `_worker.js` | Deployment config | R | Partial | Configured via deployment configs and bindings | Code is not editable through the API. Read-only in the app. |
| Project analytics | Overview | R | Partial | Requests via account GraphQL; Pages dataset not verified | Keep queries inside GraphQL budget. |

**Pages gaps.** Rollback, promote, deployment delete, build logs, and the create-deployment upload path. The Git connection step needs a browser.

### 3.3 DNS and zone overview

| Capability | Dashboard | Access | API support | Endpoint | Notes |
|---|---|---|---|---|---|
| List records (type, name, content, page, filters) | DNS records | R | Partial | `GET /zones/{zone_id}/dns_records` | Filter parameters not re-fetched. Paginate and cache per zone. |
| Create record (A, AAAA, CNAME, MX, TXT, NS, SRV, CAA, SVCB/HTTPS and others) | Add record | W | Public | `POST /zones/{zone_id}/dns_records` with `type`, `name`, `content`, `ttl` (1 is Auto), `proxied`, `comment`, `tags` | SRV, CAA and other structured types take a `data` object instead of `content`. |
| Edit record | Edit drawer | W | Public | `PATCH /zones/{zone_id}/dns_records/{id}` | Use PATCH for toggles. PUT is standard but not fetched. |
| Delete record | Delete | W | Partial | `DELETE /zones/{zone_id}/dns_records/{id}` | Confirm in the app. |
| Proxy toggle | Orange or grey cloud | RW | Public | `proxied` on create or PATCH | Only A, AAAA and CNAME are proxiable. Proxied records show TTL as Auto. |
| TTL | TTL dropdown | RW | Public | `ttl` | Hide custom TTL for proxied records. |
| Comments and tags | Record fields | RW | Public | `comment`, `tags` | Comment limit 100 characters on Free, 500 on paid. Tags are paid-only (20 per record). |
| Batch changes | Bulk edit and import | W | Public | `POST /zones/{zone_id}/dns_records/batch` with `deletes`, `patches`, `posts` | Verifier: the execution order (deletes, patches, puts, posts) is confirmed, but the body schema has no `puts` field. Do not send puts. KV propagation is not atomic across edge nodes. |
| BIND import | Import | W | Public | `POST /zones/{zone_id}/dns_records/import` (multipart, field `file`) | Reserved `cf-proxied` and `cf-flatten-cname` tags. |
| BIND export | Export | R | Public | `GET /zones/{zone_id}/dns_records/export` | Save with Android SAF. Whether DNSSEC or settings are included is not stated. |
| Private network routing | Record toggle | RW | Public | `private_routing` | Enterprise-only per docs. Hide unless the account has it. |
| Enforce DNS-only (account) | Not in dashboard | W | Public | `PATCH /accounts/{id}/dns_settings` with `enforce_dns_only` | Break-glass. Turns off proxying for every zone in the account, removing WAF and DDoS. Double-confirm. |
| Nameservers | DNS Records page | RW | Partial | `PATCH /zones/{zone_id}/dns_settings` with `nameservers.type` | Registrar change happens outside the API. |
| DNSSEC | DNS Settings | RW | Public | `PATCH /zones/{zone_id}/dnssec` with `status` (active or disabled), `dnssec_presigned`, `dnssec_multi_signer`, `dnssec_use_nsec3` | Verifier confirmed. Show the DS record after enabling. Disable before a registrar transfer. |
| Zone DNS settings | DNS Settings | RW | Public | `GET` and `PATCH /zones/{zone_id}/dns_settings` | Multi-provider field not verified. Secondary override is public. |
| Development mode | Quick action | RW | Public | `PATCH /zones/{zone_id}/settings/development_mode` with `on` or `off` | Auto-off after 3 hours. Show a countdown. |
| Pause Cloudflare | Fundamentals | RW | Public | `PATCH /zones/{zone_id}` with `paused` | Paused zones lose security and performance benefits. Confirm. |
| Under Attack mode | Quick action | RW | Partial | Configuration Rules action `set_config` with `security_level` set to `under_attack` | Rule-based, so read state from the ruleset, not a flat setting. Legacy settings endpoint not verified. |
| Purge cache | Caching | W | Partial | `POST /zones/{zone_id}/purge_cache` (variants: everything, files, tags, hosts, prefixes) | Body not fetched. Rate limited per zone. Debounce. |
| Zone list, add, remove | Account home | RW | Partial | `GET /zones`; `POST /zones` with account, name, type (confirmed); `DELETE /zones/{id}` (expected, not fetched) | Adding requires a nameserver change at the registrar. |
| Activation check | Check nameservers | W | Unconfirmed | Expected `PUT /zones/{id}/activation_check` | Verify. |
| Zone analytics tiles | Overview | R | Public | GraphQL `viewer.zones` with `httpRequests1hGroups` or adaptive datasets, `zoneTag` filter | Exact field names for uniques and cached bytes not re-verified (expected `uniques`, `sum.bytes`, `sum.cachedBytes`). Retention: 30 days on all plans since 2026-10-02. |
| Percent cached | Overview | R | Derived | From the cache status dimension | Classification changed in Dec 2025. Values before the fix are inflated. Reproduce the dashboard formula. |
| DNS analytics | DNS Analytics | R | Public | GraphQL DNS datasets (zone scope 30 to 62 days by plan; account scope 7 days) | Dataset names in secondary source only for one variant. |
| Connected Workers and routes | Right panel | R | Partial | `GET /zones/{id}/workers/routes` | Cross-link into the Workers area. |
| Registrar info | Domain registration card | R | Public | `GET /accounts/{id}/registrar/registrations/{domain}` | Status, expiry, auto-renew, privacy, lock. |
| Register a domain | Search and buy | W | Public, beta | Search and check, then `POST /accounts/{id}/registrar/registrations` | Billable and non-refundable. Exclude from v1. |
| Transfer in | Transfer page | W | Public, beta (added 2026-09-30) | Initiate and poll under registrar transfer_in | Prerequisites: DNSSEC off; EPP code for most extensions. Transfers take 1 to 10 days. |
| Renewals, contacts, auto-renew | Registration | W | None | Not in API (beta notes) | Link out. |

**DNS and zone gaps.** Activation check, zone delete path, zone settings beyond the ones above (SSL/TLS, Security, Caching, Rules, Email are not researched in depth), multi-provider field name, and the Under Attack ruleset read path. Quick actions for Markdown for Agents use Configuration Rules `content_converter` (documented, plan-gated).

### 3.4 R2

Important constraint: R2 object operations on the REST API need an Admin Read & Write or Admin Read R2 token. Object-level tokens fail on REST with 401 (code 10002, all-buckets scope) or 403 (code 10000, bucket scope). Object-level tokens work with the S3-compatible API using SigV4. The verifier confirmed this.

| Capability | Dashboard | Access | API support | Endpoint | Notes |
|---|---|---|---|---|---|
| List, get, create buckets | Bucket list and create | RW | Public | `GET /accounts/{id}/r2/buckets`; `GET .../r2/buckets/{name}`; `POST /accounts/{id}/r2/buckets` | Bucket management is rate-limited per account across all R2 REST operations (1,200 per 5 minutes). |
| Bucket settings patch | Settings | RW | Public | `PATCH /accounts/{id}/r2/buckets/{name}` | Patchable fields not confirmed. |
| Delete bucket | Settings | W | Public | `DELETE /accounts/{id}/r2/buckets/{name}` | Bucket must be empty. Emptying is dashboard-only as far as confirmed. |
| Empty bucket, folder delete (background job) | Settings, object browser | W | Unconfirmed | Bucket jobs endpoints exist (`.../jobs`, `.../jobs/{job_id}`); request bodies not read | Do not build on it until the schema is checked. |
| List objects | Object browser | R | Partial | `GET /accounts/{id}/r2/buckets/{name}/objects`; S3 ListObjectsV2 via SigV4 | REST pagination and page size not confirmed. Each page costs one REST call. Large buckets need S3 listing. |
| Download | Download | R | Public | `GET /accounts/{id}/r2/buckets/{name}/objects/{key}`; S3 GetObject with range requests | Stream to disk. Do not buffer. |
| Upload, single PUT | Upload | W | Public | `PUT /accounts/{id}/r2/buckets/{name}/objects/{key}` | Single PUT max 5 GiB (verifier confirmed). Use the content URI as a streamed body with a known length. Single PUT recommended under about 100 MB. |
| Multipart upload | Dashboard internal | W | Partial | S3 only: CreateMultipartUpload, UploadPart, CompleteMultipartUpload, AbortMultipartUpload, ListParts. Temp credentials: `POST /accounts/{id}/r2/temp-access-credentials` | Part size 5 MiB to 5 GiB, max 10,000 parts, object max 5 TiB (verifier confirmed the object limits). Incomplete uploads abort after 7 days. Needs SigV4 in Kotlin. The verifier could not confirm the 5 GiB per-part maximum. |
| Delete object | Delete | W | Public | `DELETE /accounts/{id}/r2/buckets/{name}/objects/{key}`; S3 DeleteObjects (bulk, up to 1,000 keys) | Admin token for REST. |
| Copy, rename, move | Not clearly a dashboard feature | W | Partial | S3 CopyObject only | Rename is copy then delete. |
| Object metadata | Details panel | RW | Partial | Set on PUT only; no metadata-only update found | Custom metadata max 8,192 bytes. |
| Public access (r2.dev) | Settings | RW | Public | `GET` and `PUT .../buckets/{name}/domains/managed` | Testing only. Variable rate limits and 429s. |
| Custom domains | Settings | RW | Public | `GET`, `POST`, `PUT`, `DELETE` under `.../domains/custom` | Zone must be on the account. |
| CORS | Settings | RW | Public | `GET`, `PUT`, `DELETE .../buckets/{name}/cors` | Browser-only concern. Expose for parity. |
| Lifecycle rules | Settings | RW | Public | `GET` and `PUT .../buckets/{name}/lifecycle` | |
| Event notifications | Settings | RW | Public | Under `event_notifications/r2/{bucket}/configuration` | Targets Queues. Configuration parity only. |
| Bucket lock | Settings | RW | Public | `GET` and `PUT .../buckets/{name}/lock` | 409 on locked objects. |
| Sippy and Super Slurper | Migration | RW | Public | Sippy: `.../sippy`; Slurper: `/slurper/jobs` family | Desktop task. Low priority. |
| Account metrics | R2 overview | R | Public | `GET /accounts/{id}/r2/metrics`; GraphQL `r2OperationsAdaptiveGroups`, `r2StorageAdaptiveGroups` | Bandwidth in GraphQL since 2026-09-24. |
| R2 token management | R2 API tokens | RW | Partial | Temp credentials via REST; persistent token create and list not confirmed | Dashboard flow. |
| Presigned URLs | Not a dashboard feature | RW | Partial | S3 SigV4 presign (GET, HEAD, PUT, DELETE) | Not usable with custom domains. Phone-side signing exposes the secret. Prefer temp credentials. |
| Data Catalog (Iceberg) | Data Catalog | RW | Unconfirmed | GraphQL datasets named; management REST not read | Out of MVP scope. |

**R2 gaps.** Empty bucket and folder delete (job schema), REST list paging, bucket patch fields, persistent token management, and a safe on-device credential model. The 1,200 requests per 5 minutes R2 REST limit is shared with the dashboard for the same account.

### 3.5 D1

| Capability | Dashboard | Access | API support | Endpoint | Notes |
|---|---|---|---|---|---|
| List databases | D1 list | R | Public | `GET /accounts/{id}/d1/database` | |
| Create | Create dialog | W | Public | `POST /accounts/{id}/d1/database` with `name` and optional `jurisdiction` | Jurisdiction cannot be changed later. |
| Get, rename, read replication | Settings | RW | Public | `GET`, `PUT`, `PATCH /accounts/{id}/d1/database/{id}`; `read_replication.mode` (`auto` or `disabled`) | Disabling takes up to 24 hours. |
| Delete | Settings | W | Public | `DELETE /accounts/{id}/d1/database/{id}` | Typed-name confirmation. |
| Undelete | Not clearly exposed | W | Public | `POST .../database/{id}/undelete` | Only for databases deleted within the last 7 days (verifier confirmed; changelog 2026-10-23). |
| SQL console | Console | RW | Public | `POST .../database/{id}/query` (bound params); `POST .../raw` for row arrays | Writes need D1 Edit. Results over 50 MB fail from 2026-11-04 (verifier confirmed). REST requests must finish within 30 seconds, including batches. |
| Tables browser | Tables | R | Partial | No schema endpoint. Use `sqlite_master`, `PRAGMA table_info`, `index_list`, `foreign_key_list` through the query endpoint | Browse with `LIMIT` and `OFFSET` or keyset pagination. |
| Row insert, edit, delete | Grid | RW | Partial | Parameterised `INSERT`, `UPDATE`, `DELETE` through the query endpoint | Needs primary key detection. Mistakes in generated SQL can damage data. |
| Schema changes (DDL) | Create table | W | Partial | Through the query endpoint only | Use `PRAGMA defer_foreign_keys` inside a transaction. |
| Import SQL file | Import | W | Partial | `POST .../database/{id}/import` (async; returns an upload URL, then poll status) | Full sequence inferred, not confirmed. Import locks the database. Needs a live test. |
| Export SQL dump | Export | R | Public | `POST .../database/{id}/export` with `output_format: polling`, `dump_options`; poll until `complete` (signed URL valid 1 hour) | Needs D1 Write. D1 Read returns error 10000. Database is unavailable for queries during export. Signed URL is a bearer link. Exempt from the 50 MB cap. |
| Time Travel | Backups | RW | Public | `GET .../time_travel/bookmark`; `POST .../time_travel/restore` | Restores to any minute within 30 days (Paid) or 7 days (Free) (verifier confirmed). 10 restores per 10 minutes per database (verifier: this figure is on the limits page, not the Time Travel page). Restore overwrites the database and cancels in-flight queries. |
| Metrics and query insights | Metrics | R | Public | GraphQL `d1AnalyticsAdaptiveGroups`, `d1StorageAdaptiveGroups`, `d1QueriesAdaptiveGroups`; SQL dataset `events.d1Queries` | Needs account analytics read. Query text is captured, bound parameters are not. |
| Size and quota | Metrics | R | Partial | Size may appear on the database GET (not confirmed) | Max 10 GB (Paid) and 500 MB (Free). 1 TB account storage on Paid. |

**D1 gaps.** Table and row browsing and editing are emulated with SQL, so safety depends on the app. Import flow is unverified. Free-plan enforcement of daily row limits began 2026-09-01.

### 3.6 KV

| Capability | Dashboard | Access | API support | Endpoint | Notes |
|---|---|---|---|---|---|
| List namespaces | Namespace list | R | Public | `GET /accounts/{id}/storage/kv/namespaces` | Legacy `/workers/namespaces` routes stopped working 2026-10-15 (verifier confirmed). Do not use them. |
| Create, rename, delete namespace | Namespace settings | RW | Public | `POST`, `GET`, `PUT` (rename), `DELETE` under `/storage/kv/namespaces` | Jurisdiction (eu, us, fedramp) and mode fixed at creation. |
| List keys | Key list | R | Partial | `GET .../namespaces/{id}/keys` | Prefix, cursor and limit parameter names not confirmed. Key names max 512 bytes. Search is prefix only, not full text. |
| Get value | Value view | R | Public | `GET .../namespaces/{id}/values/{key}` | Treat values as bytes, since values can be binary. |
| Get metadata | Metadata | R | Public | `GET .../namespaces/{id}/metadata/{key}` | |
| Put value with metadata and expiration | Add key | W | Partial | `PUT .../values/{key}` | Expiration parameter names not confirmed for REST (absolute epoch or TTL of at least 60 seconds). Value max 25 MiB. Metadata max 1,024 bytes. |
| Delete value | Delete | W | Public | `DELETE .../values/{key}` | Deleting a missing key succeeds. |
| Bulk write | Import (inferred) | W | Public | `PUT .../namespaces/{id}/bulk` (older path under `/workers/namespaces` is deprecated) | Max 10,000 pairs and 100 MB per request (verifier confirmed). Failed keys return in `unsuccessful_keys`. |
| Bulk get | Not a dashboard feature | R | Public | `POST .../bulk/get` | Max keys per request not confirmed for REST. |
| Bulk delete | Multi-select | W | Public | `POST .../bulk/delete` | Max 10,000 keys per request. |
| Expiration | Key details | RW | Partial | Set on PUT; read-back field not confirmed | |
| Analytics | Analytics tab | R | Partial | GraphQL `kvOperationsAdaptiveGroups`, `kvStorageAdaptiveGroups` | Retained 31 days. Needs account analytics read. |

**KV gaps.** Expiration parameter names, list-keys parameters, and bulk-get key limits. Per-key write limit is 1 write per second (verifier confirmed), so rapid edits to one key return 429.

---

## 4. Sign-in and tokens

**Preferred path: self-managed OAuth (Authorization Code with PKCE).**

- Cloudflare launched self-managed OAuth clients on 2026-06-03. Clients are created in the dashboard (Manage account, OAuth clients) or by API: `POST /accounts/{id}/oauth_clients`. Creating clients by API needs an API token with OAuth Clients Write.
- Verifier correction: the changelog says public apps are available to any user after domain verification. It does not state that any account can create a client, and no plan restriction was confirmed either way. Treat account eligibility as unconfirmed.
- Native public clients use `token_endpoint_auth_method: none`, so no secret ships in the APK. Use PKCE with S256.
- Endpoints (verifier confirmed): discovery `https://dash.cloudflare.com/.well-known/openid-configuration`; authorize `https://dash.cloudflare.com/oauth2/auth`; token `https://dash.cloudflare.com/oauth2/token`; revoke `https://dash.cloudflare.com/oauth2/revoke`; JWKS and userinfo also listed. Read endpoints from the discovery document at build time.
- Unconfirmed: authorize and token parameter details, refresh-token behaviour, and token lifetimes. Do not design around refresh until the discovery document and a test login confirm it. If refresh is not issued, users re-consent when access expires.
- Only Authorization Code is offered for self-managed clients. Client credentials, implicit, ROPC and device flows are not.
- Scope list: `GET https://api.cloudflare.com/client/v4/oauth/scopes`. Request the minimum set.
- Public visibility needs a client URL on a domain you control, a logo, a name, and a TXT record `cloudflare_oauth_client_publisher=...`. Verification polling times out after two days. Moving to public is permanent.
- Verifier correction: the claim that a private client can be authorised only by members of the owning account was not in the fetched text. Treat as unverified.
- Revocation by the user is in the dashboard profile (Access management, authorization). Whether the app can list or revoke grants through the API is not confirmed.
- Discrepancy: the April 2026 post called registration "coming soon". The June changelog supersedes it. The "coming soon" wording is stale.

**Fallback path: user API token pasted by the user.**

- Create under My Profile, API Tokens (user token), or under Manage account (account-owned token, for service-style access). Tokens carry a `cfut_` prefix and are shown once.
- Verify with `GET https://api.cloudflare.com/client/v4/user/tokens/verify` (Bearer). Verify on onboarding.
- Pre-filled creation links exist (`permissionGroupKeys`, `name`), but the key names are inconsistent across docs (for example `dns` against `zone_dns`). Test each key against a real account before shipping a "create token" button.
- Workers Builds token type: the Builds API reference says the API requires a user-scoped token and that account-owned tokens return "Invalid token". The verifier confirmed that page text. The individual method pages do not repeat the rule, and the Builds token permission is listed as Workers CI Read or Write. Treat the user-scoped requirement as reported, and check it with a live call before onboarding copy depends on it.
- Scope blast radius: a token can act on a whole account. Tell users which permissions the app needs. Prefer read-only scopes until the user enables writes.

**On-device storage.**

- The sources disagree. One research note recommends Keystore-backed EncryptedSharedPreferences. The Android note says the security-crypto library is deprecated at API level (1.1.0, 2025-07-30) and recommends using the Android Keystore directly (AES-GCM, non-exportable key) with the ciphertext in Jetpack DataStore.
- Recommendation: use the Keystore directly with DataStore, as the newer guidance says. Confirm the current security-crypto status against the release notes before the design locks.
- Never log the token. Never store it in plain SharedPreferences.

---

## 5. Analytics

**Source.** All analytics come from the GraphQL Analytics API at `POST https://api.cloudflare.com/client/v4/graphql`. Dashboard charts are powered by the same API, but the dashboard can use datasets the public docs do not name.

**Workers.**

- `workersInvocationsAdaptive` (documented as beta) exposes `sum` of `requests`, `errors` and `subrequests`, and `quantiles` of `cpuTimeP50` and `cpuTimeP99`. Filter by `scriptName` and `datetime_geq` and `datetime_leq`. Verifier confirmed the fields and beta status.
- No `cpuTimeP90` was found. The account-home "CPU time P90" tile therefore has no documented source. Either show P50 and P99 from the API, or say clearly that the P90 tile is not reproduced.
- The per-version split and wall-time fields are unconfirmed. Introspect before shipping.
- Build minutes have no analytics dataset. The Builds limits endpoint is the only documented source. Verifier: the build-minutes claim (3,000 free, 6,000 paid, then $0.005 per minute; 20-minute timeout; 1 concurrent build free, 6 paid) is confirmed on the limits page.

**Zone and account traffic.**

- Total requests and cache hit rate come from zone datasets (`httpRequests1hGroups`, `httpRequests1mGroups`, adaptive groups). Which dataset the dashboard uses for account-wide totals is unconfirmed.
- Cache ratio classification was corrected in December 2025. Older values are inflated.
- Retention is 30 days on all plans (changelog 2026-10-02). Adaptive datasets keep at least 31 days. Aggregate retention per plan was not published.

**Limits.**

- Global REST: 1,200 requests per 5 minutes per user, shared across dashboard, keys and tokens (verifier confirmed). Over the limit returns 429 with Retry-After and blocks calls for five minutes.
- GraphQL: verifier flagged a conflict. The GraphQL limits page says 300 queries per 5 minutes. The API limits reference says the maximum is 320 per 5 minutes, varying with query cost. Plan for the lower number.
- Account-based mode: send header `X-Rate-Limit-Type: account-based`. Budget is 1 request per second per account and 1 per second per zone (Enterprise defaults 15 and 10). Verifier confirmed. This suits a phone that polls several accounts or zones.
- The Settings node on accounts and zones exposes per-dataset availability, maximum page size, maximum fields and maximum duration. Query it at startup to hide tiles the plan cannot show.
- Adaptive sampling means numbers can be estimates. Show that caveat.
- Cloudflare says analytics data should not be used for billing decisions.

**Per-product datasets.** D1 (`d1AnalyticsAdaptiveGroups`, `d1StorageAdaptiveGroups`, `d1QueriesAdaptiveGroups`), R2 (`r2OperationsAdaptiveGroups`, `r2StorageAdaptiveGroups`), KV (`kvOperationsAdaptiveGroups`, `kvStorageAdaptiveGroups`). All require an account tag filter and Account Analytics Read.

**Billing.** `GET /accounts/{id}/billable-usage` (from and to optional; Billing Read, per third-party guidance; daily; self-serve accounts only; Enterprise contract accounts unsupported). Not confirmed to match the dashboard usage panel. Show it as daily and not real time, or hide it.

---

## 6. Live build progress on Android with no server

**What is achievable.**

- Poll `GET /accounts/{id}/builds/builds/{build_uuid}` (verifier confirmed). Each response carries `status` (queued, initializing, running, stopped), `build_outcome` (success, fail, skipped, cancelled, terminated), and timestamps `created_on`, `initializing_on`, `running_on`, `stopped_on`. Elapsed time per phase is therefore available.
- Build outcome enum from the API: `fail`, `cancelled`. The event schemas use different words: `failure` and `failed`, `canceled`. Map both sets. Verifier: the disagreement is wider than first stated, since event statuses also use `failed` and `canceled`. Event schemas are examples, not formal enums.
- Logs can be read by cursor during a run. Poll the cursor while status is queued, initializing or running. Stop when `stopped_on` is set.
- Discovery of the build UUID: the list endpoint by worker tag, or a stored value from a trigger response.

**What is not achievable.**

- Step or stage progress for Workers builds. The API has none. Use state and elapsed time, not percentages. (Pages exposes stage status, which is the only stage view in the sources.)
- Streamed logs. Polling only.
- Push status to the phone without a relay. Queues event subscriptions need a backend or relay, which conflicts with the no-backend design.

**Android constraints (verified against the primary pages).**

- Live Update eligibility: a promoted notification needs the `POST_PROMOTED_NOTIFICATIONS` manifest permission; a style of Standard, BigText, Call, Progress or Metric (ProgressStyle is the natural choice); the ongoing flag; a promotion request through `EXTRA_REQUEST_PROMOTED_ONGOING` or `setRequestPromotedOngoing(true)`; a `contentTitle`; no RemoteViews; not a group summary; not colorized; and channel importance not `IMPORTANCE_MIN`. Verifier confirmed all of these.
- Check `NotificationManager.canPostPromotedNotifications()` at runtime. The user can disable Live Updates per app. Send the user to `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` when that is the cause.
- Usage rules: updates must be ongoing, user-initiated and time-sensitive. Ambient information, ads and promotions are not allowed. The page allows a user-requested monitor of a background event. Verifier correction: including an Unpin action is recommended ("should"), not a stated condition. Still include one. Also use `setDeleteIntent` to detect dismissal, and do not repost a dismissed update.
- A build-completion alert should be a standard notification, not a Live Update. Google's text is guidance, not a Play ruling.
- Chip text: `setShortCriticalText` (96 dp max, shown only if it fits).
- Platform floor: the Live Update feature is Android 16 (API 36). The official page does not state a minimum, so treat API 36 as the floor. Verify the QPR1 permission placement against release notes, since a third-party source says the feature was disabled by default in stable Android 16.
- Android 15 and below: post an ordinary ongoing notification with `setProgress` and a Stop action. Do not rely on ProgressStyle degrading gracefully (not documented). Do not use RemoteViews.
- Samsung One UI Now Bar: press reports say One UI 8 shows third-party Live Updates, but the criteria are unconfirmed. Do not promise it.

**Foreground service.**

- Start the watch service with the `dataSync` type, using `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` permissions. Start it only from the user's Watch tap while the app is visible. That is an exempt start.
- Time budget: 6 hours per 24 hours per app for each type, shared across the app's dataSync services. Verifier correction: the restriction applies when the app targets Android 15 (API 35) or higher, not based on the device OS version. Bringing the app to the foreground resets the timer.
- When the budget runs out, the system calls `Service.onTimeout(int, int)`. Call `stopSelf()` within a few seconds, or the system throws. Exhausted budget throws `ForegroundServiceStartNotAllowedException`. Cap a watch session well under 6 hours (research suggests 2 hours, with a clear message).
- Do not restart the service from `BOOT_COMPLETED` or a background worker.
- WorkManager's periodic minimum is 15 minutes, so it cannot drive a seconds-level loop. Use it only for one-off checks or resuming after reboot.
- Doze: the docs do not say whether a foreground service keeps network access during Doze. Test with `adb shell dumpsys deviceidle force-idle`. Ask for a battery exemption only if testing shows polling stalls, and only within Play policy.
- Play review: dataSync needs a declaration in the App content section and a demo video that matches the declared use. Prepare a recording of install, token entry, opening a build, tapping Watch, the notification, and the service stopping at completion. The rejection-for-mismatch report is a forum report from February 2025, not a policy text.
- shortService is too short. specialUse adds review risk for a use that dataSync covers.

**Polling budget.**

- Poll about every 5 seconds while the build is non-terminal. That is 60 requests per 5 minutes per build. The shared budget is 1,200 per 5 minutes per user, so keep the number of watched builds small.
- Back off on 429 using Retry-After. Use an adaptive interval.
- Terminal condition: `stopped_on` is set, or `build_outcome` is set. Treat `skipped` (superseded) as terminal, not as an error.

**Summary of constraints.** Android 16 floor for Live Updates; Unpin and delete-intent handling; foreground service with 6-hour budget and onTimeout; polling at about 5 seconds with 429 backoff; no step progress; no push status; Doze behaviour unverified; token needs Workers CI Read.

---

## 7. Prior art and what to learn from it

- **No official Cloudflare dashboard app.** No official Cloudflare-published app for managing Workers, Pages, DNS, R2, D1 or KV was found on Android or iOS. The only official Cloudflare app found is 1.1.1.1 with WARP, a consumer DNS and VPN client. It does not manage accounts. Its reviews cite connection drops after updates, which is a warning for any network-heavy app.
- **Third-party clients.** These are the closest comparables; ratings and install counts could not be retrieved.
  - FlarePilot (Play): claims DNS, analytics, security, R2 and Workers after sign-in. Auth method and developer unconfirmed.
  - CloudMare (Play): reported as open source and as the only Play app supporting API tokens rather than the Global API Key. Repo and licence unverified.
  - Orange Cloud (iOS): DNS, analytics, live Workers logs, R2, D1, KV tabs. Reported to use OAuth with PKCE. Paywalled items include multiple accounts and live logs.
  - CF Dash (iOS): Workers, Pages, R2, D1, KV. Auth unconfirmed.
  - Clouder (iOS): monitors Workers, updates DNS, analytics. Pro tier paywalls real-time tail logs and deploy access. Requires iOS 18.
  - CloudFlare Mobile (dev.to write-up, MIT licence): covers Workers, KV, R2 and Pages, but the stack is likely not Kotlin. No repo named.
- **Kotlin libraries.** cloudflare-kmp (AndroidPoet) is a Kotlin Multiplatform client for D1, KV and R2 only. nathanfallet/cloudflare-api-client is a Ktor-based client with unconfirmed coverage. Either could be evaluated as the API layer. Check licence and maintenance first.
- **Lessons.** Competitors use API tokens or OAuth and cover DNS, analytics and storage. Live Worker logs are a paywalled differentiator. Differentiation for this app is likely the Watch build Live Update and an honest coverage map. Competitor reviews are thin in this research, so treat sentiment as unknown.

---

## 8. Brand and store policy constraints

**Trademark (cloudflare.com/trademark).**

- Refer to Cloudflare only in referential phrasing, such as "for Cloudflare" or "compatible with". Verifier confirmed the permitted forms (runs on, for use with, for, compatible with).
- Do not imply affiliation or endorsement. Do not use Cloudflare logos, including the orange cloud, without written permission. Verifier confirmed the logo rule.
- Do not put Cloudflare in the app name, package ID, domain, user agent, or code identifiers without approval. The research summary cites this from the trademark page; the exact clause was not re-read today.
- Attribution line (from the research notes): "Cloudflare, the Cloudflare logo, and Cloudflare Workers are trademarks and/or registered trademarks of Cloudflare, Inc." Place it where a mark is used. Verify the exact wording on the trademark page before release. The permission contact is not published.

**Terms and API use.**

- The website terms prohibit overloading servers or APIs and access through means not intentionally made available. No clause specifically about third-party dashboard clients was found.
- The API rate limit is the practical boundary. Design for 429 and backoff.
- Enterprise agreements restrict access to Authorized Users. Enterprise customers should be told that tokens are employee-scoped. Not verified against the current signed agreement.

**Google Play.**

- Impersonation policy (quoted, verifier confirmed): "We prohibit apps that mislead users by impersonating someone else." Use a neutral name such as "Unofficial Cloudflare Client" or a name that is not Cloudflare-branded, and say in the listing that the app is not affiliated with Cloudflare.
- Credential-theft policy: the app must never share the token and must store it securely. Not quoted from the fetched text.
- Data safety: if the app sends nothing to the developer, it can say so. The app does send the user's API requests to api.cloudflare.com, which the developer does not control. State that clearly. Declare any analytics, crash-reporting or ad SDK that transmits data.
- Target SDK: new apps and updates must target API 36 or higher from 2026-08-31 (verifier confirmed). The extension to 2026-11-01 has passed. Compose 1.12 raises compileSdk to 37 (blog confirmed). Verifier correction: AGP 9.5 support for API 37 was not confirmed by the post, which refers to AGP 9 only.
- Developer verification deadline of 2026-09-30 and a policy update block dated 2026-10-29 are listed on Play policy pages. Details for a new third-party app were not confirmed. Check Play Console before first release.

---

## 9. Risks and open questions

**Open questions (answer before design lock):**

1. Can any account create a self-managed OAuth client, and can a Play-published third-party app register one? The changelog implies yes for public apps after verification; eligibility is unconfirmed.
2. OAuth refresh tokens and lifetimes: confirm from discovery and a test login.
3. Does the Workers Builds API accept only user-scoped tokens? Reported, not confirmed on method pages.
4. Does `workersInvocationsAdaptive` expose per-version splits, and is the P90 tile reproducible?
5. Live tail `url`: is it a WebSocket, and what subprotocol? Until confirmed, do not promise live logs.
6. Workers Builds retry: is there any API path? Current answer: none documented.
7. Skipped builds: confirm the final `build_outcome` value.
8. Does a foreground dataSync service keep network access during Doze? Test on device.
9. Does Pages Git connection need a browser step, or can the GitHub authorisation be completed inside an app-owned flow?
10. R2 REST list paging, bucket jobs bodies, and whether an account token with R2 Storage Edit can use object endpoints.
11. D1 import upload sequence.
12. Expiration and prefix parameter names for KV REST list and put.
13. Exact Play policy text for trademark and credential handling; developer verification details.
14. Samsung Now Bar criteria.

**Risks:**

- **Credential blast radius.** Admin R2 tokens and account-wide DNS or Worker scopes let a leaked token delete buckets or reroute traffic. Use the smallest scope, short TTL where possible, and Keystore storage.
- **Destructive writes on a phone.** Enforce DNS-only (removes WAF and DDoS for every zone), zone pause, delete Worker, D1 time-travel restore (cancels in-flight queries and overwrites data), D1 export locking the database, rollback (does not roll back bindings or data), public preview URLs, and DNS deletes. Each needs strong confirmation and an undo path where one exists.
- **Beta and drifting APIs.** Workers versions and Workers resource endpoints are beta; `workersInvocationsAdaptive` is beta; the Registrar transfer API is beta. Pin the schema and expect breaking changes. Cloudflare changelog entries from September to November 2026 are recent; re-check them before shipping (D1 50 MB cap, D1 undelete window, KV legacy route shutdown).
- **Legacy routes already dead.** KV legacy `/workers/namespaces` routes stopped on 2026-10-15. Any code copied from older examples is broken.
- **Shared rate budget.** 1,200 requests per 5 minutes per user across dashboard and token. A watch loop plus analytics tiles can trigger 429s and a five-minute block.
- **Deploy hook URLs.** They are credentials with no header. Do not store or display them.
- **Live Update reliability.** OEMs may add criteria, and users can disable Live Updates. Keep the foreground notification fallback first-class.
- **Play review.** dataSync declaration and demo video must match. Verifier-confirmed rule only in the form given.
- **Doze and foreground time limit.** A long build can be stopped mid-watch. The UI must say so and recover.
- **Pages deprecation.** Investing in Pages-only features may be wasted. Show migration hints.
- **Analytics honesty.** Sampled, delayed and beta data. Label tiles, show caveats, and do not claim parity with the dashboard for tiles that cannot be reproduced (P90, build minutes, asset requests, cache hit rate for the account if the dataset differs).
- **Reliance on research that was not re-fetched.** This document is built from the research notes and verifier verdicts. Several endpoints are flagged as unconfirmed because they were not checked against the OpenAPI schema. Check them against the schema before implementation.

---

## 10. Sources

Primary pages cited in the research notes (as listed; all were the basis for the statements above, and each was checked by a verifier unless noted):

- Workers Builds API reference: https://developers.cloudflare.com/workers/ci-cd/builds/api-reference/
- Workers Builds method pages: https://developers.cloudflare.com/api/resources/workers_builds/ (get, list, logs, create_build, triggers)
- Workers Builds limits: https://developers.cloudflare.com/workers/ci-cd/builds/limits-and-pricing/
- Workers Builds troubleshooting and deploy hooks: https://developers.cloudflare.com/workers/ci-cd/builds/troubleshoot/ ; https://developers.cloudflare.com/workers/ci-cd/builds/deploy-hooks/
- Builds 90-day retention changelog: https://developers.cloudflare.com/changelog/post/2026-10-28-workers-builds-90-day-retention/
- Builds event subscriptions: https://developers.cloudflare.com/queues/event-subscriptions/events-schemas/ ; https://developers.cloudflare.com/changelog/post/2025-12-11-builds-event-subscriptions/
- Superseded builds changelog: https://developers.cloudflare.com/changelog/post/2026-07-24-skip-superseded-builds/
- Rerun build changelog: https://developers.cloudflare.com/changelog/post/2025-03-17-rerun-build/
- Workers scripts list and tail: https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/tail/ ; https://developers.cloudflare.com/api/resources/workers/subresources/scripts/methods/list/
- Workers observability API: https://developers.cloudflare.com/api/resources/workers/subresources/observability/
- Workers new API and versions changelog: https://developers.cloudflare.com/changelog/post/2025-09-03-new-workers-api/
- Workers infrastructure as code: https://developers.cloudflare.com/workers/platform/infrastructure-as-code/
- Gradual deployments and rollbacks: https://developers.cloudflare.com/workers/versions-and-deployments/gradual-deployments/ ; https://developers.cloudflare.com/workers/versions-and-deployments/rollbacks/ ; https://developers.cloudflare.com/changelog/post/2025-09-11-increased-version-rollback-limit/
- Custom domains and preview URLs: https://developers.cloudflare.com/workers/configuration/routing/custom-domains/ ; https://developers.cloudflare.com/changelog/post/2025-10-23-preview-url-default-behavior/ ; https://developers.cloudflare.com/changelog/post/2026-05-14-domains-tab/
- Workers metrics and analytics: https://developers.cloudflare.com/workers/observability/metrics-and-analytics/ ; https://developers.cloudflare.com/analytics/graphql-api/tutorials/querying-workers-metrics/ ; https://developers.cloudflare.com/analytics/graphql-api/features/data-sets/
- Workers observability and real-time logs: https://developers.cloudflare.com/workers/observability/logs/real-time-logs/ ; https://developers.cloudflare.com/workers/observability/issues/ ; https://developers.cloudflare.com/changelog/post/2026-10-13-cloudflare-mcp-events/
- Pages: https://developers.cloudflare.com/pages/ ; https://developers.cloudflare.com/pages/configuration/rollbacks/ ; https://developers.cloudflare.com/pages/configuration/git-integration/ ; https://developers.cloudflare.com/pages/configuration/git-integration/github-integration/ ; https://developers.cloudflare.com/pages/get-started/direct-upload/ ; https://developers.cloudflare.com/pages/platform/known-issues/
- Pages API: https://developers.cloudflare.com/api/resources/pages/subresources/projects/methods/create/ ; https://developers.cloudflare.com/api/resources/pages/subresources/projects/subresources/deployments/methods/list/ ; https://developers.cloudflare.com/api/resources/pages/subresources/projects/subresources/deployments/methods/retry/ ; https://developers.cloudflare.com/api/resources/pages/subresources/projects/subresources/domains/
- Pages changelog (GitHub user-scoped repositories): https://developers.cloudflare.com/changelog/post/2026-11-10-user-scoped-github-repositories/
- Workers best practices: https://developers.cloudflare.com/workers/best-practices/workers-best-practices/ ; migration guide: https://developers.cloudflare.com/workers/static-assets/migration-guides/migrate-from-pages/
- DNS: https://developers.cloudflare.com/dns/manage-dns-records/reference/dns-record-types/ ; https://developers.cloudflare.com/dns/manage-dns-records/reference/record-attributes/ ; https://developers.cloudflare.com/api/resources/dns/subresources/records/methods/batch/ ; https://developers.cloudflare.com/dns/manage-dns-records/how-to/import-and-export/ ; https://developers.cloudflare.com/dns/proxy-status/enforce-dns-only/ ; https://developers.cloudflare.com/api/resources/dns/subresources/dnssec/methods/edit/ ; https://developers.cloudflare.com/dns/private-origins/private-network-routing/
- Zone settings and rules: https://developers.cloudflare.com/api/resources/zones/subresources/settings/methods/edit/ ; https://developers.cloudflare.com/cache/reference/development-mode/ ; https://developers.cloudflare.com/api/resources/zones/methods/edit/ ; https://developers.cloudflare.com/rules/configuration-rules/settings/ ; https://developers.cloudflare.com/rules/configuration-rules/create-api/
- Zone analytics retention: https://developers.cloudflare.com/changelog/post/2026-10-02-30-days-analytics-on-every-plan/ ; cache classification: https://developers.cloudflare.com/changelog/post/2025-12-18-cached-request-classification/ ; DNS analytics: https://developers.cloudflare.com/dns/additional-options/analytics/
- Registrar: https://developers.cloudflare.com/registrar/registrar-api/ ; transfer changelog: https://developers.cloudflare.com/changelog/post/2026-09-30-registrar-transfer-api-beta/
- R2: https://developers.cloudflare.com/api/resources/r2/ ; https://developers.cloudflare.com/r2/platform/limits/ ; https://developers.cloudflare.com/r2/platform/troubleshooting/ ; https://developers.cloudflare.com/r2/api/error-codes/ ; https://developers.cloudflare.com/r2/api/s3/api/ ; https://developers.cloudflare.com/r2/api/s3/presigned-urls/ ; https://developers.cloudflare.com/r2/objects/upload-objects/ ; https://developers.cloudflare.com/r2/platform/metrics-analytics/
- D1: https://developers.cloudflare.com/api/resources/d1/ ; https://developers.cloudflare.com/d1/platform/limits/ ; https://developers.cloudflare.com/d1/reference/time-travel/ ; https://developers.cloudflare.com/d1/best-practices/read-replication/ ; https://developers.cloudflare.com/d1/best-practices/import-export-data/ ; https://developers.cloudflare.com/d1/observability/metrics-analytics/ ; https://developers.cloudflare.com/d1/observability/find-expensive-queries/ ; https://developers.cloudflare.com/d1/sql-api/foreign-keys/
- D1 changelogs: https://developers.cloudflare.com/changelog/post/2026-10-30-query-result-size-limit/ ; https://developers.cloudflare.com/changelog/post/2026-10-23-undelete-seven-day-window/ ; https://developers.cloudflare.com/changelog/post/2026-09-01-d1-free-tier-limit-enforcement/ ; https://developers.cloudflare.com/changelog/post/2025-11-05-d1-jurisdiction/
- KV: https://developers.cloudflare.com/api/resources/kv/ ; https://developers.cloudflare.com/kv/api/write-key-value-pairs/ ; https://developers.cloudflare.com/kv/api/delete-key-value-pairs/ ; https://developers.cloudflare.com/kv/api/read-key-value-pairs/ ; https://developers.cloudflare.com/kv/api/list-keys/ ; https://developers.cloudflare.com/kv/observability/metrics-analytics/ ; legacy route shutdown: https://developers.cloudflare.com/changelog/post/2026-07-15-kv-legacy-namespace-routes-deprecation/
- Rate limits: https://developers.cloudflare.com/fundamentals/api/reference/limits/ ; https://developers.cloudflare.com/analytics/graphql-api/limits/ ; https://developers.cloudflare.com/analytics/graphql-api/account-based-rate-limiting/ ; https://developers.cloudflare.com/support/troubleshooting/http-status-codes/4xx-client-error/error-429/
- Auth and tokens: https://developers.cloudflare.com/changelog/post/2026-06-03-public-oauth-clients/ ; https://developers.cloudflare.com/fundamentals/oauth/create-an-oauth-client/ ; https://developers.cloudflare.com/fundamentals/oauth/integrate-with-cloudflare/ ; https://developers.cloudflare.com/fundamentals/oauth/authorizing-an-application/ ; https://developers.cloudflare.com/fundamentals/api/get-started/create-token/ ; https://developers.cloudflare.com/fundamentals/api/get-started/account-owned-tokens/ ; https://developers.cloudflare.com/fundamentals/api/how-to/account-owned-token-template/ ; https://developers.cloudflare.com/fundamentals/api/reference/permissions/ ; https://blog.cloudflare.com/improved-developer-security/ ; https://developers.cloudflare.com/changelog/post/2026-08-26-app-scoped-tokens/
- Billing: https://blog.cloudflare.com/billable-usage-api/ ; https://developers.cloudflare.com/changelog/product/fundamentals/
- Android: https://developer.android.com/develop/ui/compose/notifications/live-update ; https://developer.android.com/about/versions/16/features/progress-centric-notifications ; https://developer.android.com/develop/background-work/services/fgs/service-types ; https://developer.android.com/develop/background-work/services/fgs/timeout ; https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start ; https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work ; https://developer.android.com/training/monitoring-device-state/doze-standby ; https://developer.android.com/jetpack/androidx/releases/core ; https://developer.android.com/jetpack/androidx/releases/security ; https://developer.android.com/jetpack/androidx/releases/datastore ; https://developer.android.com/google/play/requirements/target-sdk ; https://developer.android.com/build/releases/gradle-plugin ; https://developer.android.com/about/versions/17/summary ; https://developer.android.com/about/versions/17/behavior-changes-all ; https://developer.android.com/jetpack/androidx/releases/compose-material3 ; https://developer.android.com/develop/ui/compose/bom/bom-mapping ; https://developer.android.com/jetpack/androidx/releases/navigation3 ; https://developer.android.com/develop/ui/views/launch/splash-screen ; https://support.google.com/googleplay/android-developer/answer/13392821
- Android blog and Maven metadata: https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html ; https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml ; https://kotlinlang.org/docs/whatsnew2420.html
- Play policy: https://play.google/developer-content-policy/ ; https://support.google.com/googleplay/android-developer/answer/13323374?hl=en ; https://developer.android.com/distribute/play-policies
- Trademark and terms: https://www.cloudflare.com/trademark/ ; https://www.cloudflare.com/website-terms/ ; https://www.cloudflare.com/en-in/enterpriseterms/
- Prior art: https://play.google.com/store/apps/details?id=cf.getsdata.com (FlarePilot); https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone (1.1.1.1 with WARP); https://android-stats.com/d/dev.jtsalva.cloudmare (CloudMare); https://apps.apple.com/us/app/orange-cloud-for-cloudflare/id6779323783 ; https://apps.apple.com/app/id6766168875 (CF Dash); https://apps.apple.com/app/id6755757771 (Clouder); https://dev.to/abdulm/i-manage-11-cloudflare-domains-from-my-phone-now-27en ; https://klibs.io/project/AndroidPoet/cloudflare-kmp ; https://awesome.ecosyste.ms/projects/github.com%2Fnathanfallet%2Fcloudflare-api-client

Sources the research notes flagged as not primary or not read in full: the 2026-06-30 memory metrics changelog, the 2026-09-25 release flows changelog, the 2026-08-20 Durable Objects deployments changelog, the bulk secrets changelog (2026-06-03), the Workers observability telemetry schema, the Pages build-log path, the OpenAPI schema at https://developers.cloudflare.com/api/ (not re-fetched), and the InfoQ summary on OAuth shield colours.
