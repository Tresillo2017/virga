# Contributing to Virga

Thanks for your interest. Virga is a small project maintained in spare time, so a little preparation goes a long way.

## Before you start

- **Bugs**: open an issue with the bug report form. Check first whether it happens in the demo account (on the sign-in screen, choose **Explore the demo**).
- **Features**: open a feature request before writing code, so we can agree on the approach. Virga only uses the public Cloudflare API: if the API cannot do something, neither can the app.
- **Security problems**: do not open an issue. See [SECURITY.md](SECURITY.md).
- Small fixes (typos, obvious one-line bugs) can go straight to a pull request.

## Setting up

You need Android Studio with JDK 17 or newer, Android SDK platform 37, and a device or emulator on Android 9 (API 28) or newer.

```sh
./gradlew :app:installDebug      # install the debug build
./gradlew testDebugUnitTest      # all unit and UI flow tests
./gradlew lintDebug              # Android Lint
```

You do not need a Cloudflare account. The demo account serves canned data for every screen. If you do test with your own account, use an API token with the fewest permissions that cover what you are testing.

## How the code is organised

Read [docs/architecture.md](docs/architecture.md) and [docs/feature-module-guide.md](docs/feature-module-guide.md) first. In short:

- Feature modules (`:feature:*`) depend only on `:core:*`, never on each other. Cross-feature links go through route keys in `:core:navigation`.
- UI is Jetpack Compose with Material 3 Expressive. Use the shared components in `:core:ui` (`ScreenScaffold`, `SectionCard`, `ListRow`, the state scaffolds) instead of building new ones, and theme colors and shapes instead of hard-coded values.
- API responses are parsed defensively: unknown fields ignored, optional fields nullable.
- Every new screen gets demo routes so it works in the demo account.

## Tests

Every change that alters behaviour needs a test:

- Repositories and view models: unit tests with `mockCloudflareApi` (Ktor `MockEngine`), `kotlinx-coroutines-test` and Turbine.
- Screens: Robolectric Compose flow tests with `DemoFlow` (see "UI flow tests" in the feature module guide).
- `app/src/test/.../md3/Md3SourceRulesTest` checks a set of Material 3 rules across the source. If it fails, fix the code rather than the rule.

## Pull requests

1. Fork the repository and branch from `main`.
2. Keep each pull request to one change. Refactors go in their own pull request.
3. Use [Conventional Commits](https://www.conventionalcommits.org/) for commit messages and the pull request title: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `ci:`, `build:`, `chore:`.
4. Make sure `./gradlew testDebugUnitTest lintDebug` passes. CI runs the same checks plus a minified release build.
5. For UI changes, add before and after screenshots, and check light and dark theme and a large font size.
6. Never include API tokens, account IDs or real hostnames in code, tests, screenshots or logs. Use `example.com` and the demo data.

The maintainer labels merged pull requests so they land in the right section of the release notes (see [docs/releasing.md](docs/releasing.md)).

## License

Virga is licensed under the [GNU General Public License v3.0](LICENSE). By contributing, you agree that your contributions are licensed under the same terms.

## Code of conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md). By taking part, you agree to follow it.
