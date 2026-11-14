# Releasing Virga

Releases are built and published by GitHub Actions. Nothing is built on a laptop, and every release is reproducible
from its tag.

## Channels and versions

| Tag | Channel | GitHub release | Google Play (if configured) |
| --- | --- | --- | --- |
| `v1.3.0-alpha.1` | alpha | Pre-release | Internal testing |
| `v1.3.0-beta.2` | beta | Pre-release | Internal testing |
| `v1.3.0-rc.1` | release candidate | Pre-release | Internal testing |
| `v1.3.0` | stable | Release, marked Latest | Production, as a draft |

`versionName` is the tag without the `v`. `versionCode` comes from the version, so it always increases in release
order and never depends on a CI counter:

```
versionCode = MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + STAGE
STAGE       = N for alpha.N, 30 + N for beta.N, 60 + N for rc.N, 99 for stable
```

So `1.3.0-alpha.1 < 1.3.0-beta.1 < 1.3.0-rc.1 < 1.3.0 < 1.3.1-alpha.1`. Limits: minor and patch up to 99, up to 29
pre-releases per channel and version. The logic lives in `.github/scripts/release-version.sh`:

```sh
.github/scripts/release-version.sh parse v1.3.0-rc.1      # name, channel, versionCode
.github/scripts/release-version.sh next minor beta        # the next tag, from the local tags
```

## Cutting a release

**One click (recommended).** Actions → *Prepare release* → *Run workflow* on `main`:

- `bump`: `patch`, `minor` or `major` raises the version from the latest stable release; `none` continues the
  version in progress (the next beta, the first rc, or the stable release of it).
- `channel`: `alpha`, `beta`, `rc` or `stable`.
- `dry_run`: prints the version and stops.

It refuses to run unless CI passed on the head of `main`, tags that commit with an annotated tag, and runs the Release
workflow. A typical cycle from `v1.2.0`: `minor + beta` → `v1.3.0-beta.1`, `none + beta` → `v1.3.0-beta.2`,
`none + rc` → `v1.3.0-rc.1`, `none + stable` → `v1.3.0`.

**By hand.** Push an annotated tag; the Release workflow starts on its own:

```sh
git tag -a v1.3.0-beta.1 -m "Virga 1.3.0 beta 1"
git push origin v1.3.0-beta.1
```

**Re-run.** Actions → *Release* → *Run workflow* with an existing tag. It rebuilds and replaces the assets and notes of
that release, for example after fixing a secret.

## What the Release workflow does

1. **Validate**: the tag must be a release tag; a stable tag must point at a commit on `main` and needs the signing
   secrets. Pre-releases without a key are published unsigned with a warning in the notes.
2. **Verify** (in parallel with the build): unit and UI flow tests, and Android Lint.
3. **Build**: the minified release APK and app bundle for the phone and the minified release APK for the watch
   (`:wear`), all with the tag's version, checked with `aapt2`; when signed, both signatures are verified with
   `apksigner`, the certificate fingerprint is recorded, and the build fails if the watch APK is signed with a different
   key than the phone APK (the Wear Data Layer only delivers items between apps with the same package name and key).
4. **Publish**: a GitHub release with the phone APK (`virga-<version>.apk`), the AAB, the watch APK
   (`virga-wear-<version>.apk`), the R8 mapping files for both apps and `SHA256SUMS.txt`. The notes give the install
   steps for both apps, the channel, version code and certificate fingerprint, then the changes since the previous
   release, grouped by pull request label (`.github/release.yml`).
5. **Play** (only with `PLAY_SERVICE_ACCOUNT_JSON`): pre-releases go to internal testing; stable releases go to
   production as a draft, and a person starts the rollout in the Play Console. Only the phone bundle is uploaded. The
   watch app is a GitHub release APK for now: shipping it on Play needs its own `versionCode` and a form-factor release
   (see `docs/release-checklist.md`, section 9).

## Secrets

| Secret | Needed for |
| --- | --- |
| `VIRGA_KEYSTORE_BASE64` | Signing. `base64 -i upload.jks` of the upload keystore |
| `VIRGA_KEYSTORE_PASSWORD` | Signing |
| `VIRGA_KEY_ALIAS` | Signing |
| `VIRGA_KEY_PASSWORD` | Signing |
| `PLAY_SERVICE_ACCOUNT_JSON` | Optional Play upload. A service account with release access to the app |

Keep the upload keystore and its passwords in a password manager as well: a lost upload key needs a reset through
Play support, and releases signed with a different key cannot update an installed app.

## Labels for good release notes

Label pull requests with `feature`, `bug`, `security`, `design`, `accessibility`, `performance`, `ci`, `build`,
`dependencies` or `breaking`. Unlabelled pull requests land under *Other changes*; `skip-changelog` leaves one out.
