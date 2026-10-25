# Standalone validation — 25 October 2026

This record covers the independent project, not the original consuming app. Raw logs and synthetic screenshots live in ignored `build/reports/` outputs and private GitHub Actions artifacts. A signed local bundle is not evidence of Maven Central availability.

## Fresh local evidence

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

## Pending external steps

- GitHub standalone validation passed on the SDK 24 source commit: https://github.com/chinmaydash007/webview-inspector/actions/runs/40181178238. This is separate from the cloud emulator jobs and the pending signed-publishing workflow.
- The owner deferred Central account/namespace/token setup. Maven Central has **not** been published or independently downloaded, and the downstream app has **not** migrated.
- The private GitHub Release must remain a draft until account setup and all release gates pass. The automatic publishing workflow itself cannot be end-to-end verified against Central without those credentials.
- Authenticated host-app flows and a full manual TalkBack audit remain unexercised. This extraction does not make claims about other consumer toolchains beyond the documented tested baseline.
