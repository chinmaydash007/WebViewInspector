# Standalone validation

## Unreleased Live and Recent WebViews — 21 November 2026

- The JDK 17 non-publishing gate passed with WebKit 1.15.0 and 1.16.0: root
  formatting, 45 library and three sample JVM tests, Android Lint, seven-method
  full/no-op facade parity, 34 publication/content guard tests, and independent
  Java consumer debug, minified diagnostic and no-op release builds. Dependency
  resolution and release-APK isolation checks passed. Both dependency lanes
  produced identical full/no-op AAR hashes; publication still compiles against
  and declares WebKit 1.15.0 as its minimum.
- Each WebKit lane passed the explicitly selected API 24 emulator's 18 native and
  three clipboard cases. API 36 passed its navigation prerequisite, 31 normal
  cases, three clipboard cases, and the 23 light/16 dark cases at 320 dp and
  160% font scale. Exact URL copying passed in the minified Java consumer and
  the offline Afterframe showtime flow.
- The new lifecycle cases use synthetic URLs and real Activity transitions.
  They cover payment Activity destruction followed by a native confirmation
  screen, helper-only captures that never became visible, saved URL/header
  copying, separate instance identities, background/resume, clearing, disable
  and uninstall. An authenticated payment/backend flow was not exercised.
- Local API 36 validation uses official Chromium ARM64 158.0.8124.0, snapshot
  revision 1726605, with recorded APK SHA-256
  `e5f973811e59fdef875c258aa8798b112facec086174c0cd4474519229956168`.
  Software-renderer attempts stalled inside the emulator graphics driver; the
  completed lanes use the host GPU renderer. Original density, font, night mode,
  keyboard and animation settings were checked against the saved originals.
  API 36's original provider was reselected and both task-created emulators were
  stopped after validation.
- Evidence: `build/reports/webkit-compat/matrix-6qnstciy/` and
  `build/reports/live-recent/`. Both WebKit lanes completed successfully. This
  working-tree change has not been published.

## Published 0.4.2 — 15 November 2026

Both artifacts are available on Maven Central. Anonymous downloads of the AARs,
POMs, Gradle metadata, sources and documentation for both artifacts matched all
ten SHA-256 checksums in the signed release manifest.

The [signed dry run](https://github.com/chinmaydash007/WebViewInspector/actions/runs/43671143660)
and [release workflow](https://github.com/chinmaydash007/WebViewInspector/actions/runs/43687745410)
passed standalone validation and the API 24 and 36 native gates on `4cf0b2a`.
The release workflow reused the verified dry-run bundle in one Central deployment
and built the debug, minified diagnostic and no-op release variants of the
independent Java consumer from public Maven repositories. The signed bundle,
manifest, checksums and deployment receipt are attached to the [GitHub Release](https://github.com/chinmaydash007/WebViewInspector/releases/tag/v0.4.2).

## Inspector navigation bar insets — 15 November 2026

- The JDK 17 non-publishing gate passed, including formatting, lint, facade
  parity, publication checks, and the independent Java consumer builds.
- API 24 passed 14 native cases and three clipboard cases with software navigation
  buttons enabled. API 36 passed the navigation prerequisite, all 27 normal cases
  at 1080×1920, three clipboard cases, and the 19 light and 12 dark cases at
  320 dp and 160% font scale. The tests check that the sheet surface reaches the
  window bottom and that the pinned footer ends above the system inset in
  Current URL and History. Rotation, exact copying and scroll before swipe
  dismissal also passed. Emulator settings were restored.
- Afterframe screenshots on API 36 show the sheet surface behind the gesture
  handle and three-button navigation in light and dark mode. Developer Tools
  shares the same treatment. API 24 screenshots show the legacy tint keeping
  the white system buttons readable. Local evidence is in
  `build/reports/sheet-insets/`.

## Published 0.4.1 — 14 November 2026

Both artifacts are available on Maven Central. All ten primary files (AARs, POMs,
Gradle metadata, sources and documentation) matched the signed release bundle in
anonymous downloads. The full AAR contains the dark mode resources, and the full
source JAR contains the Close icon alignment fix.

The [signed dry run](https://github.com/chinmaydash007/WebViewInspector/actions/runs/43600824822) and the
[prepared release run](https://github.com/chinmaydash007/WebViewInspector/actions/runs/43602410283)
passed standalone checks and the API 24 and 36 native gates, including the 320 dp
light and dark suites at 160% font scale. The prepared release reused the exact signed
bundle and built the debug, minified diagnostic and no-op release variants of the
independent Java consumer from public Maven repositories.

Central reported **PUBLISHED**. The `v0.4.1` release assets record the signed bundle,
tagged source identity, checksums and deployment receipt.

Local checks also passed: 41 JVM tests, 33 publisher/content guard tests, lint with
zero errors, API 24 and 36 native suites, and exact URL copying in the minified Java
consumer. Both production APKs excluded inspector resources, implementation and
WebKit. The local emulators' settings were restored after testing.

## Inspector clipboard feedback — 14 November 2026

- The JDK 17 non-publishing gate passed, including lint, the independent Java consumer,
  and full/no-op facade and publication checks.
- API 24 passed all 14 existing native cases and the three clipboard feedback cases.
  API 36 with WebView 157.0.8103.50 passed the navigation prerequisite, all 27 native
  cases, 19 light and 12 dark narrow-screen cases, and all three clipboard feedback
  cases. Copies preserved exact input strings. The feedback cases checked the old
  Android checkmark, its timeout and cleanup, the accessibility announcement, and
  clipboard preservation on a rejected write.
- API 36 vibration service history recorded a single confirmation in Ring and Vibrate.
  Silent and all three DND filters produced no request. Android suppressed the pulse
  when touch feedback was disabled. A rejected write produced no confirmation request.
  The emulators' settings were restored. Physical vibration was not measured.

## Afterframe press feedback and showtime rendering — 14 November 2026

- Afterframe uses neutral gray press feedback masked to the native controls' shapes
  and the local movie links' corner radii. On API 36, light and dark screenshots
  showed bounded feedback on the navigation indicator, Settings, film tiles,
  watchlist rows and showtime links.
- On API 36 with WebView 157.0.8103.50, a 12-tap run per build measured Midnight
  Local's 6:45 PM link from ADB tap dispatch to the first contentful paint probe.
  The first open measured 179 ms before and 208 ms after. Repeat medians were
  144 ms before and 166 ms after, with observed maxima of 179 ms and 636 ms
  respectively across all taps. The updated showtime pages made zero separate CSS
  requests and painted at full opacity. The timing run did not establish a speedup.
- A follow-up WebView trace measured click dispatch to navigation start at 0.1–8 ms.
  In the slowest traced navigation, HTML arrived at 48 ms and WebView recorded a
  paint at 228 ms. It reported first contentful paint at 824 ms, with a gap before
  frame presentation. The trace and timing samples are in the local build reports.
- Midnight Local and The Last Signal showtimes opened and returned to their listings
  on API 24 and 36 in debug and minified release. API 24 ran offline. The API 36
  Inspector clipboard probe matched the exact showtime URL. The JDK 17 standalone
  gate and minified no-op isolation check passed.

## Afterframe Material 3 redesign — 14 November 2026

- The JDK 17 standalone gate passed. Sample Lint reported zero errors. The minified
  release APK contains no Inspector runtime resources, implementation, or WebKit.
- On API 36, Home exposed both embedded WebViews. Saving and removing films updated
  the watchlist, including its empty state. The exact showtime URL clipboard smoke and
  minified Java consumer smoke passed.
- Both Headers send modes matched the live endpoint and Inspector App loads on API 36.
  Browser accepted HTTPS, rejected HTTP, and preserved its open page, forward stack,
  and unfinished address through rotation. All four tabs were checked in light mode
  and in dark mode with 1.6× font scale and animations disabled.
- On API 24, the film detail and showtime rendered offline, and Headers showed its
  error and Retry state. The API 24 minified release rendered Movies. On API 36, the
  minified release exercised all four tabs, the live receipt, and HTTPS browsing.

## Afterframe feature packages — 14 November 2026

- The standalone verification gate passed, including the Browser URL tests and debug and
  minified no-op builds. Sample lint reported zero errors.
- On API 24 and 36, Home exposed two independent Inspector targets. Opening a poster,
  saving and removing a film, switching tabs, and restoring the Movies page and back stack
  after rotation worked. The exact showtime URL clipboard smoke passed on API 36.
- Browser retained its page and navigation across tab switches and rotation on both API
  levels. Inspector Current URL and History worked, and the copied URL preserved the query
  encoding and fragment. Home, Movies and Browser also passed on the no-op release.
- On API 36, the Headers receipt matched the three captured demo headers. The URL-only
  load produced a zero-header Inspector entry and the echo omitted all demo headers.
  The minified no-op build also displayed the live receipt. On API 24, the older provider
  showed Retry after the endpoint response failed to render.

## Afterframe Browser tab — 14 November 2026

- The standalone gate passed, including three URL validation tests and both sample build types.
- On API 36, following a link and using Back and Forward produced navigation history. On
  API 24 with the older provider, Browser showed observed URL changes. Both emulators
  retained the page across tab switches and copied an encoded URL with its fragment exactly.
- On API 36, keyboard Go loaded the page and hid the keyboard. Rotating the Activity
  retained the selected tab, the unfinished address and the browser’s forward stack.
- The minified no-op sample browsed on API 24. HTTP input and a mismatched HTTPS certificate
  showed inline errors. The separate native redirect fixture verified real redirect grouping
  and exact clipboard contents on API 36.

## Afterframe sample — 14 November 2026

- The standalone verification gate passed, including debug and minified no-op release
  builds. On API 36, the compatibility smoke copied the showtime URL exactly. Real
  redirect grouping and exact intermediate URL copying remain in the native fixture.
- On API 36, both embedded cards were inspectable. Saving a film in Movies updated Home.
  The HTTPS response matched the Inspector’s captured run, and a second request using
  the URL-only helper echoed no demo headers. The minified no-op build also received all
  three demo headers.
- The API 24 emulator passed all 14 native cases. Home and Movies rendered and watchlist
  updates worked with WebView 53. The live endpoint response did not render on that
  provider. Afterframe showed **Retry**.

## Published 0.4.0 — 10 November 2026

Both artifacts are available on Maven Central. All ten primary files, including
the AARs, POMs, Gradle metadata, sources and API documentation, matched the
original signed bundle by SHA-256 when downloaded without credentials.

The prepared release passed the standalone and API 24 and 36 gates, then
published the signed bundle attached to the private draft. The independent Java
consumer built its debug, diagnostic and release variants against the public
coordinates. [Prepared release run](https://github.com/chinmaydash007/WebViewInspector/actions/runs/42785929523)

Local compatibility checks passed on API 24 and 36 with WebKit 1.15.0 and 1.16.0.
They covered the full/no-op facade, production isolation and exact URL copying
in the minified sample.

## Lint and formatting setup — 8 November 2026

Added root Spotless 8.11.0 with ktlint 1.8.0 for Kotlin/Gradle Kotlin scripts and
google-java-format 1.28.0 AOSP for Java, on the existing JDK 17 / Gradle 8.13 toolchain.
All 69 existing covered source/build files now pass the full-tree style check. Root
`.editorconfig` supplies four-space indentation, LF/UTF-8, 120-column Kotlin and
formatter-defined 100-column Java. Formatter dependencies remain build tooling only.

Fresh local evidence:

| Check | Result |
|---|---|
| Deliberately malformed Kotlin, Java and Gradle probes | All three rejected by `spotlessCheck`; `spotlessApply` repaired them |
| Read-only checks / repeat apply | Source hashes unchanged by checks; second apply produced no changes |
| Exclusion probes | Build/generated sources, Gradle/Kotlin caches, `.tools` and `.idea` remained untouched |
| Literal audit | Kotlin lexer / Java parser preserved literal sequences across all 69 source/build files; existing root-build literals remain with additional tooling configuration |
| JVM tests | All 38 passed, zero failures/errors/skips |
| Android Lint | Zero errors in full, no-op, sample and Java consumer; existing warnings remain reported (39 / 0 / 7 / 1) |
| API parity / no-op forwarding | Seven matching static methods; helpers still delegate exactly once |
| Publishing/content guards | All 30 passed; both locally staged publications validated |
| Sample / independent Java consumer | All three variants of each built, including both minified profiles |
| Packaged isolation | All six APKs require API 24; both production builds contain zero inspector resources, implementation names or internal R8 mappings |
| Published dependency inventory | No Spotless, ktlint or google-java-format dependencies in either publication |

The first normalization passes reported wildcard imports and long-line diagnostics.
Imports were expanded explicitly and layout corrected without disabling rules or
weakening assertions. The pinned ktlint CLI helped materialize intermediate formatting
for the initial mixed-style files; the final supported workflow is the root Spotless
tasks. All formatting probes were removed before compiling or staging publications.
Temporary checks/tools and logs are ignored under `build/reports/formatting-2026-11-08/`
and `.tools/formatting/`.

`scripts/verify.sh` now checks formatting first and includes Android Lint for every
Android project, including the independent consumer. Existing main/PR and release
validation use that script. CI does not apply formatting. The consumer still uses
Maven coordinates and has no Kotlin or formatting Gradle plugin.

Existing local Gradle edits were preserved and normalized; unrelated IDE metadata and
the consuming app checkout were preserved. API 24, version 0.3.0, runtime behavior and
release safeguards are unchanged. No commits, pushes, repository visibility changes,
signing, publication or app distribution were performed. Generated local unsigned
publications are validation outputs, not replacement release artifacts.

Instrumentation hosts were compiled; device/emulator interaction suites were not rerun
for this formatting/build-tooling change. Runtime, clipboard and device evidence below
retains its original dates and is not presented as fresh execution.

## 0.3.0 Basic / Advanced preparation — 1 November 2026

**Original unreleased preparation checkpoint.** At that checkpoint no commits, pushes,
tags, GitHub Releases or Central publication had been performed for this feature, and
the latest published version was 0.2.0. The consuming application checkout was preserved
byte-for-byte, including its pending migration work. The completed 0.3.0 publication
record below supersedes this pending status; older release evidence remains separate.

Basic retains the existing discovery, sampling, navigation events, clients and settings.
Advanced adds two synchronous load helpers and a separate app-load store/panel. It
captures app-supplied inputs only, shows full values, and never attributes headers to
browser redirects, requests or outcomes by inference. No-op helpers still load normally.

Fresh local checks:

| Check | Result |
|---|---|
| JVM tests | 38 passed: original 26 plus 12 app-load cases |
| Lint / API parity | Zero errors; seven matching static methods, including both helpers |
| No-op forwarding bytecode | Only argument checks and exactly one corresponding WebView call per helper |
| Publisher/content guards | All 30 passed; approved identities, nested archives and dependency inventories preserved |
| Sample / Java-only consumer | All three variants of each built, including both minified profiles |
| API 24 fallback / Advanced | All 14 native cases passed on explicitly selected emulator-5558 |
| API 36 prerequisite / normal | Navigation support prerequisite and all 27 normal cases passed on emulator-5554 |
| API 36 320 dp / 160% font | All 19 light and 12 dark cases passed |
| Packaged production isolation | Both production APKs declare API 24 and have zero inspector resources, internal DEX names or implementation mappings |

The API 24 test-owned emulator uses Google's checksum-verified ARM64 revision-29 image
and WebView 53.0.2785.124, exercising real unsupported-provider fallback. API 36 uses
WebView 156.0.8078.47. The existing API 27 emulator was left untouched. Native suites
cover both Activity families, actual keyboard/rotation, gestures, saved targets, and
the new pre-attachment, mutable-map, header/value copying, clearing and focus/scroll
cases. Font, density, night mode and animations are restored by the runner. Competing
Maestro instrumentation is stopped during native tests and restored afterward.

Unit coverage includes equal recycled identifiers without instance merging, repeated
URLs, empty headers without inheritance, scope cleanup, pending collection, saved bound
records, exact exports, UTF-8/per-instance/process budgets and preserving Advanced after
Clear under global eviction pressure. Record budgets include pending captures and are
independent of navigation history. Retired empty metadata is bounded; live weak markers
are not evicted merely because other views generated records.

Independent foreground probes on minified APKs verified exact supplied headers, the
original stored URL after real HTTP redirects, and app-load export containing the
explicit URL/headers without manufacturing a redirect destination. The Java-only
minified consumer also copied exact headers with a plain Activity/platform theme and
no Compose/Kotlin Gradle plugin. Its minified production counterpart rendered the page
through the same helper while displaying no inspector UI.

Final-code smoke checks also exercised mixed Basic/Advanced Compose instances on API 24
and a two-redirect navigation grouped under one reported event on API 36. The app-load
export still contained only the explicit initial input. Five consecutive API 24 About
dismissal runs verified focus restoration after the parent window regained focus.

Initial failures are retained separately: a system clipboard preview intercepted a
footer tap, and input-focus assertions needed an established focus precondition. Copy
assertions still read the real clipboard; the affected footer action invokes the actual
button directly to avoid that external preview. App-load clips mark Android 13+ previews
sensitive without altering copied bytes. About dismissal now restores tab focus without
making initial taps focus-only, waiting for parent window focus to avoid an API 24 race.
The assertion still requires actual tab focus after that asynchronous transition.
Screenshot review also caught a split History label at
large font; responsive badges now stack rather than splitting that word. Required
assertions and existing behavioral cases were retained, and the affected suites rerun.

Evidence lives in ignored `build/reports/advanced-0.3.0/` and
`build/reports/devices/api-24/`, `api-36/`, with earlier evidence preserved separately.
Sources, guides and diagrams use the independent namespace. Both 0.3.0 publications
are staged only in the local validation repository; those files are not evidence of
Central availability or a signed release. Existing released tags/assets are unchanged.

Unexercised: full manual TalkBack/screen-reader review, physical devices, authenticated
host-app flows and real backend/payment traffic. These are not implied by emulator,
fixture, compilation or clipboard evidence. Network/response headers and POST bodies
remain intentionally outside this feature.

### Release hardening — shorter CI viewport

The first 0.3.0 signed dry run passed build validation, all 14 API 24 cases and API 36's
keyboard preflight, navigation prerequisite and 27 normal cases. Its 320×640-pixel API 36
image then exposed four App loads test failures at 160% font: the tests attempted to tap
partially visible rows below the capture notice without scrolling them into view.
Signing/publication was skipped; no Central upload was attempted.

The test interactions now scroll the actual nested content before tapping those rows,
retaining Espresso's default visibility constraints and every behavioral assertion.
All six App loads tests passed locally with the same 320×640 size, 160 dpi and 160% font.
Runtime sources are unchanged by this correction. The failed cloud logs are preserved
in ignored `build/reports/release-0.3.0/`; the corrected commit must pass a new complete
signed dry run before tagging or publishing.

## Published 0.3.0 — 1 November 2026

**Both 0.3.0 artifacts are published on Maven Central.** The repository and
[v0.3.0 GitHub Release](https://github.com/chinmaydash007/WebViewInspector/releases/tag/v0.3.0)
remain private. The annotated tag identifies source commit
`31ed1e0f52cbfd1bf678e0c559939c85c932bc1d`, including the test scrolling correction above.
Released 0.1.0/0.2.0 tags, artifacts and assets were preserved.

[Push CI 41298199478](https://github.com/chinmaydash007/WebViewInspector/actions/runs/41298199478)
and [signed dry run 41298218824](https://github.com/chinmaydash007/WebViewInspector/actions/runs/41298218824)
completed successfully. The
[release workflow 41299612633](https://github.com/chinmaydash007/WebViewInspector/actions/runs/41299612633)
independently repeated all build and native gates before uploading:

| Check | Fresh release result |
|---|---|
| JVM tests / lint | All 38 passed; zero lint errors |
| Facade parity / no-op forwarding | Seven static methods match; both no-op helpers delegate once |
| Publishing/content guards | All 30 passed |
| Sample / Java consumer builds | All three variants of each passed before publication |
| API 24 fallback | All 14 cases passed |
| API 36 preflight / prerequisite | Actual keyboard/rotation and navigation support checks passed |
| API 36 normal | All 27 cases passed |
| API 36 320 dp / 160% font | All 19 light and 12 dark cases passed on the shorter CI viewport |
| Signatures / public downloads | All ten primary files verified; anonymous Central bytes match the signed bundle |

CI retained the unsupported API 24 provider and used the pinned official test-only
WebView 158.0.8124.0 on API 36. Original emulator settings were restored. The compact
local regression run also restored size, density and font; its test APK was removed and
the preexisting Maestro service restored. No physical device or consuming-app data was
modified.

The Release reused the exact successful dry-run bundle, SHA-256
`7e0ad15d3d1230a324ebe062cd4d8be9740ab30421681556ad2ed14c3758d5a7`.
Central deployment `0a087501-fa71-470a-b9a1-7f922be330e3` reached **PUBLISHED**. The private
Release retains its immutable upload-intent and deployment receipts; there was one
deployment and no replacement upload. Independent anonymous verification of both AARs,
POMs, Gradle metadata, source JARs and documentation JARs completed at 11:22 UTC. The
downloaded files match the original signed manifest and publisher-owned namespace.

The release workflow also completed its final clean public-only Java consumer build:
debug, minified diagnostic and minified production no-op all passed with a fresh Gradle
user home, no local validation repository and no Compose/Kotlin Gradle plugin. This is
fresh verification of the public 0.3.0 artifacts, separate from pre-publication fixture
builds. The complete release run finished successfully.

The consuming application remains unchanged; adoption is deferred to a separate phase.
Documentation status updates do not move the tested tag or change published bytes.
Fresh evidence is retained in ignored `build/reports/release-0.3.0/` and private Actions
artifacts. The earlier local minified clipboard/isolation checks above remain distinct
from these cloud/publication checks. Manual TalkBack review, physical devices and
authenticated host-app flows remain unexercised. No app distribution was performed.

## 0.2.0 namespace migration — local evidence, 1 November 2026

The 0.2.0 sources moved both artifacts to the publisher-qualified package and retained
API 24 and the five facade methods. Local results for that migration:

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

At the tagged source checkpoint, signed CI/API 24 gates, new minified clipboard probes,
Central publication/public-download verification and downstream adoption were still
pending. That preparation snapshot is superseded by the completed 0.2.0 release record
below; it is retained to distinguish local staging from actual publication.

## Published 0.2.0 — 1 November 2026

**Both 0.2.0 artifacts are published on Maven Central.** The repository and
[v0.2.0 GitHub Release](https://github.com/chinmaydash007/WebViewInspector/releases/tag/v0.2.0)
remain private. The annotated tag points to validated source commit
`b78e8e4569b27baba4557689ab44dc69859024fb`; 0.1.0's tag and published assets were preserved.

[Push CI 41278432909](https://github.com/chinmaydash007/WebViewInspector/actions/runs/41278432909),
[signed dry run 41278440242](https://github.com/chinmaydash007/WebViewInspector/actions/runs/41278440242)
and [release run 41279789794](https://github.com/chinmaydash007/WebViewInspector/actions/runs/41279789794)
all completed successfully. These are fresh 0.2.0 checks:

| Check | Result |
|---|---|
| JVM tests, lint and facade parity | 26 tests passed; zero lint errors; five static methods match |
| Publisher and content guards | All 30 tests passed, including nested archives and exact dependency inventories |
| API 24 fallback | All eight native cases passed |
| API 36 keyboard/rotation preflight and navigation prerequisite | Both passed, including actual keyboard bounds and exact copying |
| API 36 normal suite | All 21 cases passed |
| API 36 320 dp / 160% font | All 13 light and six dark cases passed |
| Both publication signatures | All ten primary file signatures verified with the permanent public key |
| Anonymous Central downloads | All ten primary files match the signed bundle |
| Clean public-only Java consumer | Debug, minified diagnostic and minified no-op release built successfully |

CI used explicitly selected API 24 and API 36 emulators. API 24 kept its unsupported
provider for real sampling coverage; API 36 used the pinned official test-only provider
158.0.8124.0. The local API 36 run above used 156.0.8078.47 independently. Font, density,
night mode and animation settings were restored by the native runner.

The release reused the exact previously verified dry-run bundle, SHA-256
`8a067c1257469f4ff3e59698c6974604cbcd5673db8e154795f999a046edbb70`.
Central deployment `e5c5b17b-7af6-44a6-ac5f-a9265f2f5e38` reached **PUBLISHED** at
06:41 UTC / 12:11 IST. Its immutable upload-intent and deployment receipts are attached
to the private Release. No second deployment or replacement asset was needed.

The publisher downloaded both AARs, POMs, Gradle metadata, source JARs and documentation
JARs anonymously, checked every SHA-256, and then built the Java-only consumer with a
fresh Gradle user home and no local validation repository or Kotlin/Compose Gradle
plugin. An independent local download check agreed. Recursive inspection of that exact
signed bundle found only the approved project identities and dependencies, with no
previous host-company packages or content. Both AARs declare API 24.

Five fresh foreground clipboard probes passed on the local API 36 emulator using
minified APKs: the sample's exact initial URL, exact destination after two real HTTP
redirects, the stored historical initial URL after overwriting the clipboard, complete
history export, and the Java-only consumer's exact URL. Redirects remained grouped in
one navigation record. Encoding, duplicate query parameters, `+` and fragments were
preserved. The independent consumer uses a plain Activity and platform theme. These
checks exercised the new package; they are separate from the dated 0.1.0 smoke checks.

After public verification, a downstream application's existing integration was updated
to 0.2.0. All four profiles built, five developer-tools unit tests and five minified
instrumentation cases passed. Runtime graphs select full for debug and both diagnostic
profiles, and no-op for production; all APKs require API 24. Production contains zero
inspector resources, implementation DEX names or internal R8 mappings. The test-target
build produced identical APK bytes. The two central imports changed; individual WebView
screens and callbacks were preserved. Consumer changes remain uncommitted, outside this
repository. Its authenticated routes and API 24 runtime were not rerun; the independent
library's API 24 gate above remains separate evidence.

Evidence includes ignored `build/reports/company-independent-0.2.0/` reports, private
Actions artifacts and the immutable Release assets. Full manual TalkBack review,
authenticated consumer flows and live payments remain unexercised. No app distribution
was performed. The historical records below retain their original dates and refer to
0.1.0 unless explicitly stated otherwise.

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
