# Security policy

Virga holds a Cloudflare API token that can change real infrastructure, so security reports are taken seriously.

## Reporting a vulnerability

Report privately through [GitHub's private vulnerability reporting](https://github.com/Tresillo2017/virga/security/advisories/new). Please do not open a public issue, pull request or discussion for a security problem.

Include what an attacker can do, the steps to reproduce, and the Virga version. Never include a real API token; if you think one has leaked, revoke it in the Cloudflare dashboard first.

You can expect an acknowledgement within 7 days and an update on the fix within 30 days. Once a fix is released, the advisory is published and you are credited, unless you prefer not to be.

## Supported versions

Only the latest release, stable or pre-release, receives security fixes.

## In scope

- Exposure of the API token: storage, logging, backups, screenshots, the recents screen, or sending it anywhere other than `https://api.cloudflare.com`.
- App lock bypass.
- Actions performed on the account without the user's intent, for example a destructive action without its confirmation.
- Release integrity: the signing certificate of an official release is listed in its release notes.

Problems in Cloudflare's own services or API belong in [Cloudflare's disclosure programme](https://www.cloudflare.com/disclosure/).
