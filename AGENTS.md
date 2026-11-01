# WebView Inspector maintenance

Read README.md, docs/RELEASING.md, VALIDATION.md and the implementation architecture before changes. User instructions take precedence.

- This repository MUST remain private. Never change visibility, create a public GitHub Pages site, or publish GitHub releases/assets outside this private repository. Central publication is explicitly public, including sources and documentation.
- Keep the five Java/Kotlin facade methods and full/no-op parity. Runtime implementation belongs to internal responsibility packages; preserve API 24, instance identity, exact raw copying, lifecycle cleanup, focus and scroll-before-dismiss.
- No host-screen changes, client/settings/bridge replacement, JavaScript injection, header capture, URL logging/persistence/upload, or analytics.
- Use the self-contained version catalog. No dependency on another checkout, private Maven repository, app/helper module, or local absolute path. Preserve the strictly constrained WebKit 1.15.0 dependency.
- `INSPECTOR_PACKAGE` in gradle.properties is the canonical publisher-owned namespace. Keep facade source packages and directories, Android namespaces, resource imports, test runners and CI paths aligned with it. The no-op Android namespace adds `.noop`; its facade uses the same package as the full artifact. Never add legacy aliases or host-application identities to public contents.
- Build using JDK 17 and the committed wrapper. `bash scripts/verify.sh` is the default non-publishing gate. Run device tests only against explicitly selected emulators and restore settings.
- Sample and test code never ships in AARs. The independent consumer uses Maven coordinates without Compose or a Kotlin Gradle plugin.
- Publishing requires the tested version tag and exact signed bundle. Never replace released assets, retag a release, overwrite Central coordinates, or automatically retry an ambiguous upload. Keep source/version/deployment hashes in private release evidence.
- Credentials and private signing keys stay outside Git and outside logs. Keep the public signing key and fingerprint only. Fork/PR validation never receives publishing secrets.
- Preserve pending publication status until Central download verification passes. Never substitute local-Maven success for Central availability. Do not migrate a consuming app before both public coordinates work.
- Prefer clear named variables and readable Kotlin/Python. Add tests for lifecycle or publication invariants rather than mirroring simple implementation statements. Update diagrams and docs when ownership changes.
- Publication content checks inspect nested runtime classes, source/documentation archives, manifests and exact dependency inventories. Update reviewed identities deliberately when dependencies or the facade change; never relax those checks to admit host code or private workspace data.
