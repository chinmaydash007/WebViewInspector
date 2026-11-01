# Standalone validation

## 0.2.0 namespace migration — local evidence, 1 November 2026

Current sources use `io.github.chinmaydash007.webviewinspector`; both artifacts retain
API 24 and the five facade methods, with no legacy alias. These are fresh local results:

- Clean module/sample outputs rebuilt successfully. All 26 JVM tests, lint (zero errors),
  full/no-op API parity and 30 publishing/content guard tests passed.
- All three sample variants and all three independent Java-only consumer variants built,
  including both R8 configurations. Every APK declares API 24; production has zero
  inspector resources and internal mapping entries. Neither consumer requires Compose
  or a Kotlin Gradle plugin for the inspector.
- Both staged AAR/POM/Gradle-metadata/source/documentation publications passed nested
  archive, namespace and dependency-inventory checks. An independent recursive scan of
  all ten primary files found no prior host-company identities or workspace paths.
- Source comparison confirmed that all 54 Kotlin/Java source files changed only by
  namespace replacement; UI resources, algorithms, timing and facade signatures remain
  unchanged. The host-specific notice prose was replaced with a neutral publication
  notice while retaining the Apache license and third-party notices.
- On explicitly selected `emulator-5554`, API 36 / WebView 156.0.8078.47, the navigation
  prerequisite, all 21 normal cases, 13 narrow/large-font light cases and six dark cases
  passed under the new test namespace. Font, density, night mode and animation settings
  were restored; competing automation instrumentation was stopped for native testing.

Evidence is retained in ignored `build/reports/company-independent-0.2.0/` and
`build/reports/devices/api-36/`. The first archive-check run rejected a legitimate
publisher group and a Java NIO reference; the guard was corrected, regression fixtures
were added, and full verification then passed. That failed run is not counted as a pass.

At this source checkpoint, signed CI/API 24 gates, new minified clipboard probes,
Central publication/public-download verification and downstream adoption are still
pending. Do not infer their completion from local staging. The historical records
below retain their original dates and refer to 0.1.0 unless explicitly stated otherwise.

This record covers the independent project, not the original consuming app. Raw logs and synthetic screenshots live in ignored `build/reports/` outputs and private GitHub Actions artifacts. Historical preparation evidence and the completed 31 October publication are separated below. A signed local bundle alone is not evidence of Maven Central availability.

## Local evidence — 25 October 2026

- All **26 JVM tests passed**, covering weak target identity, history grouping/budgets, observation/subscription cleanup and local date presentation. Library `lintDebug` passed with **0 errors**; existing Kotlin/deprecation/style warnings remain.
- All **21 existing instrumentation cases passed on the explicitly selected API 36 emulator**, with WebView 156.0.8078.47: eight inspector, five navigation-history, two gesture and six Focused cases. A separate release prerequisite confirmed NAVIGATION_LISTENER_V1 support.
- The owner subsequently selected **minimum SDK 24** for both artifacts and all consumers/samples. The earlier eight API 23 passes are historical extraction evidence only; API 23 is no longer supported or required in CI. API 24 verification replaces that minimum-version run.
- All **eight API 24 cases passed** on the explicitly selected task-owned ARM64 Google emulator with the new minimum-SDK declaration. These cover both native hosts, saved/returned targets, menus/cleanup, scroll restoration, presentation and sampled capture.
- On the updated minimum-SDK build, the full **21 API 36 cases**, navigation prerequisite, **13 narrow/large-font light cases** and **six dark cases** were rerun successfully.
- All **13 narrow/large-font light cases** and **six dark cases** passed at 320 dp / 160% font. These include all seven earlier narrow-screen regressions plus the six Focused cases. Settings are restored by the device script. Only screenshots newly produced by each test class should be treated as evidence of that presentation mode.
- Full/no-op release facades expose the same five Java static methods. Runtime production source bytes match the extracted implementation; standalone changes are build/publishing/sample/test/documentation support.
- Both unsigned local Maven publications validated with sources, real Dokka HTML documentation, usage guides, license, POMs and Gradle metadata. Both signed publications also staged successfully and all signatures verified against the permanent public signing key. Public-key server lookup returned the expected fingerprint.
- The independent **Java-only** consumer built debug, R8-minified diagnostic and R8-minified production variants from Maven coordinates in the local validation repository. It has no Kotlin/Compose Gradle plugin or direct Compose dependency. Its production APK has **0 inspector resources / 0 internal mapping entries**; its diagnostic APK has **151 inspector resources / 73 internal mapping entries**.
- The Java-only minified consumer also opened the inspector and passed an independent exact clipboard probe, confirming no runtime Compose requirement. The sample built debug, minified diagnostic and minified no-op release. On the minified diagnostic sample, independent foreground clipboard probes passed for the exact initial URL, exact redirected destination, historical initial URL after changing the clipboard to the destination, and full timeline export. Real loopback HTTP navigation showed two redirects grouped under one record.
- Publishing guard tests cover private-repository enforcement, tag/commit agreement, immutable release assets, signed bundles, failed deployments, downloaded hash mismatches, cross-origin authorization stripping, resuming an existing deployment, and refusing a duplicate upload after an ambiguous attempt.
- Workflow YAML parsed, source inputs were reviewed for build/cache leakage, private key blocks and local absolute paths, and documentation links resolved. Private signing material is outside Git; GitHub receives encrypted secrets. Repository visibility is checked independently during creation and publishing.

Local evidence includes `build/reports/standalone/` and `build/reports/devices/`. The original consuming application's checkout remains unchanged; its local modules remain in use until Central publication succeeds. No consuming app release, payment, backend change, token demo or header capture was performed.

## External steps pending on 25 October 2026

This historical pending state is superseded by the completed publication record below.

- GitHub standalone validation passed on the SDK 24 source commit: https://github.com/chinmaydash007/webview-inspector/actions/runs/40181178238. This is separate from the cloud emulator jobs and the pending signed-publishing workflow.
- The owner deferred Central account/namespace/token setup. Maven Central has **not** been published or independently downloaded, and the downstream app has **not** migrated.
- The private GitHub Release must remain a draft until account setup and all release gates pass. The automatic publishing workflow itself cannot be end-to-end verified against Central without those credentials.
- Authenticated host-app flows and a full manual TalkBack audit remain unexercised. This extraction does not make claims about other consumer toolchains beyond the documented tested baseline.

The first cloud device run exhausted hosted-runner disk space before either emulator booted; no native tests from that run are counted as passes. Device CI now frees only unused toolchains on disposable GitHub-hosted runners before boot. Local SDK 24/36 evidence above remains independent.

Cloud API 24 passed after runner-space cleanup. Cloud API 36 correctly failed its explicit navigation prerequisite on the image's bundled WebView 133; those event tests were not silently skipped. The next CI revision pins an official, checksum-verified Chromium provider solely for that disposable API 36 emulator.

## Release preflight — 31 October 2026

The earlier evidence above remains dated to its original run. Signed dry run [40184397749](https://github.com/chinmaydash007/WebViewInspector/actions/runs/40184397749) completed successfully on `43e05b8c86d43c9451735a59e4839d0917d69e88`, including build validation, eight API 24 cases, the API 36 navigation prerequisite, 21 normal cases, 13 narrow/large-font light cases, six dark cases, and verification of all ten primary publication signatures.

The owner has now verified the Central namespace and added both Central secrets. Preflight confirmed the GitHub repository remains private under its current name `chinmaydash007/WebViewInspector`; the existing `v0.1.0` tag and signed bundle remain unchanged. All 15 publisher guard tests pass locally, including lookup of authenticated drafts, resolving version/source from the immutable tag, explicit draft-publication opt-in and preserving duplicate-upload protection. A prepared-release workflow reruns the original tagged build/native gates before the corrected current publisher uploads those exact signed assets.

Central publication is still pending at this preflight snapshot. Successful public download and independent consumer evidence will be recorded only after the release workflow actually completes.

Prepared-release run `41182611643` passed build validation and all eight API 24 cases. Two API 36 attempts each passed the navigation prerequisite and 20 normal cases, but `keyboardInsetsAccessibilityAndActualRotation` timed out waiting for the emulator's IME to become visible, before reaching its inspector-inset assertion. Publication stayed blocked. CI now explicitly permits software-keyboard display when a hardware keyboard is detected and retains input-method diagnostics on failure; the tagged test and every assertion remain unchanged. A fresh full gate run is required after this environment adjustment.

Run `41184435245` exposed an emulator-action command-format error before native tests: the action invokes each script line separately, so a multiline shell conditional was invalid. The command format was corrected and checked line by line with `/bin/sh -n`; this setup failure is not counted as native test evidence.

Run `41184815265` passed build validation and API 24 but reproduced the same API 36 IME-visibility timeout despite explicitly allowing a software keyboard. End-of-suite input-method state confirmed Gboard is installed, but its bounded history no longer covered the earlier failed request. CI now runs the unchanged keyboard case first and collects an IME log throughout; the original full normal/light/dark suites remain required. The emulator action calls one Bash entry point so structured shell control flow is preserved.

The isolated check in run `41185823077` reproduced the timeout and retained its complete input-method history. The editor established a text input connection, but the trace contained no IME show request. The test harness now explicitly requests keyboard display through `WindowCompat.getInsetsController(...).show(ime())`, following Android's documented keyboard setup. It adds an editor-focus assertion and preserves the existing real IME-visibility, control-bounds, rotation and exact-copy assertions. CI first checks that runtime sources/resources and build/dependency configuration are identical to the immutable tag, then compiles that unchanged runtime with the updated keyboard test file. Publication and all full native gates remain pending until the updated harness actually passes.

In run `41186700063`, the explicit request showed the IME and the real keyboard-bounds assertion passed. The isolated case subsequently failed finding the sheet after landscape recreation; the input-method trace showed visibility restoration during that transition. The harness retains the Back action, then explicitly hides and awaits the IME both before and after recreation before opening the sheet. Its actual landscape, clipboard and keyboard-geometry assertions remain in place; preflight screenshots are retained for inspection. No runtime source or release asset changed.

## Published release — 31 October 2026

**Both `0.1.0` artifacts are published on Maven Central and publicly downloadable.** GitHub repository `chinmaydash007/WebViewInspector` remains private, including its published [v0.1.0 GitHub Release](https://github.com/chinmaydash007/WebViewInspector/releases/tag/v0.1.0).

[Release run 41187806536](https://github.com/chinmaydash007/WebViewInspector/actions/runs/41187806536) completed successfully on publisher/harness commit `77a50f7d783a2cb246a03b12b7145b76def03575`. It verified and published the original signed bundle from immutable artifact tag/source commit `43e05b8c86d43c9451735a59e4839d0917d69e88`.

| Fresh check | Result |
|---|---|
| Tagged library JVM tests, lint and facade parity | Passed; 26 JVM tests and no lint errors |
| Current publisher guard tests | All 15 passed |
| API 24 native cases | All eight passed |
| API 36 isolated keyboard/rotation case | Passed with real IME visibility, control bounds and exact clipboard assertions |
| API 36 navigation prerequisite / normal suite | One prerequisite and all 21 normal cases passed |
| API 36 320 dp / 160% font, light / dark | All 13 light and six dark cases passed |
| Public Maven downloads | All ten primary files downloaded anonymously and matched the signed bundle |
| Independent public-only Java consumer | Debug, R8-minified diagnostic and R8-minified no-op release all built successfully |

Central deployment `27392699-2908-41aa-816c-fe6cea363838` reached **PUBLISHED**. The workflow verified public file hashes before building the independent consumer with a fresh Gradle user home, no local validation repository, and no Kotlin/Compose Gradle plugin. An independent local download check also verified both AARs, POMs, Gradle metadata files, sources and documentation JARs on 31 October at 15:14 IST. Its report and downloaded files are in ignored `build/reports/publication-2026-10-31/` outputs.

The unchanged combined signed bundle has SHA-256 `b8d87702320c6bd1e5001f55224cd9ee54d4cdf3f0552b332492945f238bf6c2`. The tag and original three release assets were not replaced; deployment/intent receipts were added to the private Release. Both published AARs declare minimum SDK 24. Current library runtime sources/resources and build/dependency configuration still match the artifact tag; only the publisher, CI setup, test preconditions and documentation changed during release verification.

Earlier minified on-device copy evidence remains dated to 25 October; it is not presented as a new runtime smoke test. Authenticated original host-app flows and a full manual TalkBack audit were not rerun. This publication task did not change or migrate the original host-app checkout.
