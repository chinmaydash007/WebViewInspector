# Private GitHub → public Maven Central

Repository visibility is permanently **private** for this workflow. GitHub tags, Releases, attached bundles, Actions logs and CI artifacts stay within that private repository. Maven Central AARs, complete sources and documentation are intentionally public after publication. No public GitHub Pages site is configured.

## One-time setup

1. Sign in to https://central.sonatype.com using the `chinmaydash007` GitHub identity. Verify namespace `io.github.chinmaydash007`; GitHub identity verification must not make this repository public.
2. Generate a **publishing user token** in the Central Portal. Its token username/password differ from your sign-in credentials. Do not put them into Git, command arguments, logs or chat.
3. Store the generated credentials securely. If using the local configuration helper, save the two fields as `username` and `password` in an owner-only JSON file outside the checkout, for example `~/.config/webview-inspector-publishing/central-credentials.json`, with mode 600. Adding the secrets directly in GitHub does not require this local file.
4. Add the token username/password as repository Actions secrets `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` at https://github.com/chinmaydash007/WebViewInspector/settings/secrets/actions. Alternatively, run `scripts/configure_central_secrets.py` in an isolated Python environment containing PyNaCl, passing only the protected credential file path. It verifies that GitHub remains private and encrypts both values without printing them.
5. Signing secrets `SIGNING_KEY` and `SIGNING_PASSWORD` are configured separately. The permanent public signing key is [signing-public.asc](../signing-public.asc). Fingerprint: **B9C2F3CF6BDEC9054EA83F3FCD516702F4195002**, expiring in three years. It is distributed through keyserver.ubuntu.com, independently of private GitHub access. Protected local signing material is outside Git under `~/.config/webview-inspector-publishing/`; back it up securely. Never publish its private key or passphrase.

Local tooling dependencies are not consumer dependencies. Use `python3 -m venv .tools/publishing-venv` and install `PyNaCl==1.6.2` if needed. Local signature verification uses GnuPG, or PGPy 0.6.0 in an isolated environment when GnuPG is unavailable. CI uses GnuPG. The publisher reads the current repository identity from GitHub Actions, with `chinmaydash007/WebViewInspector` as its local default, so renamed-repository redirects are not required for writes.

## Before a release

- Keep `GROUP` and `VERSION_NAME` in gradle.properties as the single coordinate source. Both modules always share a version. Do not use SNAPSHOT versions for releases.
- Update CHANGELOG and compatibility notes. Run `bash scripts/verify.sh`, then native API 36/API 24 gates. The API 36 provider must support the navigation listener for the resolved WebKit version. An unsupported provider fails the prerequisite.
- Check the independent Java consumer, including its minified diagnostic variant, with no Compose/Kotlin Gradle plugin. Check no-op resource/dependency isolation and exact clipboard contents on an emulator.
- Push the tested source to private main. Run **Signed Maven Central release → Run workflow** for a signed dry run. Manual dispatch only signs/stages/verifies; it never uploads to Central.
- Native workflow evidence is private. If a CI system image has an older WebView, update that emulator's official provider before releasing and rerun; never disable or weaken the prerequisite to obtain green CI.

## Create the release

After all gates and account setup pass, create an annotated `vVERSION` tag at the tested commit and push it. Create a GitHub Release in the private repository using that existing tag. Publishing that Release triggers the workflow; a draft does not. The GitHub repository stays private throughout.

The workflow checks the private-repository invariant and exact tag/commit/version agreement, reruns validation/native gates, imports the public key, stages both signed Maven publications, and assembles a reproducible combined Central ZIP. It attaches the signed bundle, SHA256SUMS and publication-manifest.json to the private Release. It then uploads **one deployment containing both artifacts** with AUTOMATIC publishing, waits for PUBLISHED, and verifies public downloads against every primary file's SHA-256. An independent clean consumer build then resolves coordinates from Central without local repositories/substitution.

The publisher uses Vanniktech 0.35.0 for Android publication/source/POM/metadata/signing configuration. The release script handles combined bundle upload, deployment status and retry safety through the Central Portal API. No legacy OSSRH endpoint is used.

Do not call global Gradle publishing tasks as part of ordinary validation. `stageRelease` publishes only to `build/validation-repository`; `scripts/verify.sh` never uploads. No consuming Android app is distributed by this workflow.

## Publish an existing prepared draft

When release automation needs a repair after a version was tagged, preserve that tag and its signed assets. From private `main`, run **Publish prepared Maven Central release** with `release_tag` set to the existing version, such as `v0.1.0`. This is a publishing operation, unlike the original **Signed Maven Central release** manual dry run.

The prepared-release workflow checks out the immutable tag for build and API 24/36 native gates. Before compiling the native tests, CI verifies that runtime sources, resources, dependency versions and build configuration match the current harness commit, then applies only its `WebViewInspectorTest` file. That harness explicitly requests the IME before asserting actual keyboard visibility/insets, rotation and exact copying; no assertions are removed. The other tagged tests remain unchanged. A runtime/configuration difference blocks this harness transfer rather than testing different library code.

It runs the current publisher tests on `main`, restores the draft's exact original assets, and verifies their signatures, hashes and source commit against the tag's own `gradle.properties`. Publisher HEAD may differ from artifact source HEAD; it never substitutes a newly built bundle for the existing release assets.

Only after those gates pass, `--publish-draft` publishes the existing GitHub draft using the workflow's `GITHUB_TOKEN`, then uploads or resumes the one Central deployment. GitHub does not trigger a second release-event workflow for this token, avoiding a second publisher. The same public-download verification and clean Java consumer build follow. Repository privacy is checked before publication and after release updates.

Draft lookup uses the authenticated release list when GitHub's published-release-by-tag endpoint returns 404. Normal `publish` calls still reject drafts unless explicitly opted in. The standard immutable-assets and ambiguous-upload protections below apply to both workflows.

## Retry and failure handling

Release tags and assets are immutable by convention. A rerun restores the exact original signed ZIP and manifest before doing any publishing work; it rejects replacement bytes. `deployment.json` stores the Central deployment ID and bundle hash, so a normal retry resumes that deployment instead of uploading again.

An `upload-intent.json` asset is saved **before** POST. If the upload response is lost, a retry stops rather than risking a duplicate deployment. Inspect Central for `webview-inspector-VERSION`; recover its deployment ID and the corresponding hash into deployment.json in the private Release, then rerun. Never guess a deployment ID. If no deployment exists, a maintainer must explicitly resolve the ambiguous intent before another upload.

A FAILED deployment, invalid signature, version mismatch or wrong downloaded hash blocks success. Inspect the retained private logs and Portal errors. Fix source in a new version/tag. Already published Central coordinates cannot be replaced or removed as a rollback strategy.

Missing account/token credentials block publication, not extraction or local validation. Do not claim Central availability from a signed dry run. Keep the GitHub Release as a draft until prerequisites pass; this also prevents a knowingly doomed automatic publishing run.

## After Central publication

Confirm both AARs/POMs/sources/docs are downloadable without GitHub authentication. Update README/CHANGELOG status only after that verification passes. Consumers need `google()` and `mavenCentral()` and select exactly one full/no-op artifact per variant.

Migrate a consuming application only after both coordinates work. Both artifacts require API 24. Pin the full/no-op artifacts to the same version and update the facade import when moving from 0.1.0 to 0.2.0. Preserve central Application installation, developer controls and individual WebView screens. Remove any former source-module dependencies and update integration docs. Build each consumer profile and repeat minified copying and no-op isolation checks. Keep consumer changes separate from library release commits; do not distribute an app release as part of library publishing.

## Cloud test provider

The API 36 Google emulator image currently bundles WebView 133, which lacks NAVIGATION_LISTENER_V1. Device CI installs an official Chromium x64 SystemWebView snapshot (158.0.8124.0, revision 1726604) from Google's Chromium snapshot archive, checks the reviewed SHA-256, and selects it through the emulator's WebView update service. The helper rejects physical devices, non-debuggable images, other API levels and other ABIs. It does not modify API 24's old provider, preserving real fallback coverage. The provider APK stays in ignored test build outputs and never ships in Maven artifacts or samples. The navigation prerequisite remains mandatory.

For local compatibility validation, start an API 24 emulator with its original provider and an API 36 emulator whose provider supports both listeners. Select JDK 17 and pass the two emulator serials in API order:

```sh
python3 scripts/verify_webkit_compatibility.py --api24-serial emulator-5558 --api36-serial emulator-5554
```

The command runs both dependency lanes, including the standalone build checks and native suites. Results go under `build/reports/webkit-compat/`. The release AAR is compiled against 1.15.0 in both lanes. The overrides apply only to local test and consumer configurations.

The snapshot URL, member and APK hash are pinned in scripts/install_ci_webview.py. Updating them requires inspection of the official APK's package, minimum SDK, ABI and checksum, followed by native validation. This is disposable CI test infrastructure, not a recommendation to install development WebViews on user devices.
