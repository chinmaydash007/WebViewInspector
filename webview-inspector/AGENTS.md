# WebView Inspector maintenance guidance

Read the [root guide](../README.md), [architecture](docs/ARCHITECTURE.md), and [validation](../VALIDATION.md) before editing. This file applies only to this module. User instructions take precedence.

## Responsibilities

- Keep `com.webviewinspector.WebViewInspector` with its five original Java-compatible static methods and both loadUrl helpers. Implementation types belong under `internal` and remain Kotlin `internal`.
- `core` owns lifecycle/session orchestration, scheduling and navigation subscriptions; `detection` owns hierarchy traversal/visibility; `state` owns weak instance selection, immutable navigation records and bounded history; `actions` owns live/saved copying and highlighting.
- `ui.floating` owns control rendering/movement; `ui.sheet` owns Focused content, picker/footer popups and About; `ui.common` owns native measurement/gesture coordination and shared themes. UI emits callbacks rather than importing `core`. Keep inspector-only styling separate from the floating/settings helpers.
- `util` is for narrowly named presentation helpers. Do not put selection, timers, Android UI ownership, or actions there.
- Use focused concrete collaborators. This library does not need Activities, ViewModels, repositories, dependency injection, or a parallel architecture for each function.

## Required invariants

- All WebView access and mutable business state stay on the main thread. The adapter may snapshot provider callback values on their dispatch thread using synchronized private identity bookkeeping, then publish immutable events to the main thread. Preserve asynchronous facade dispatch and idempotent install/uninstall.
- Identify targets by instance identity and weak references, never resource IDs, equality, or adapter positions. After selection disappears, require explicit reselection.
- Keep discovery debounce at 100 ms, fallback discovery at 1 second, URL sampling at 500 ms, and Locate at 5 seconds unless the user requests a behavior change.
- Scan Activity decor, but attach the interactive overlay to Activity content and reattach after replacement. Keep outside-control touch passthrough.
- Preserve monitoring during library-owned dialog focus. Release Activity resources on pause/stop/destruction and stop polling when disabled/backgrounded.
- Keep both sheets on `SheetScrollView`. The Focused content root owns `SheetDragGuard`; its scroller must not also own a guard. The legacy settings scroller owns its own gate. Gestures beginning below the top cannot dismiss (including pinned-region drags); restore dragging on release/cancellation/detachment so a separate gesture at the top can dismiss.
- Copy only a freshly read original URL from the exact validated instance. Display decoding must never feed the clipboard.
- The live copy invariant above applies to Current URL/quick-copy. Historical copying explicitly copies stored original strings and is available for saved history after live selection loss. Never silently substitute a live URL for a recorded URL.
- Keep WebKit 1.15.0 as the compile version and published minimum while supporting API 24. Isolate the experimental 1.15 and stable 1.16 APIs in `AndroidXNavigationEventSource`. Use labelled sampling when the selected API is unavailable. Snapshot mutable Navigation values at provider dispatch before a main-thread hop.
- Remove subscriptions on hidden/detached/inactive targets and reject stale callbacks. History survives pause/resume with gaps and Activity destruction. Destruction releases live Activity resources; only clearing, bounded eviction, disable/uninstall or process death releases saved records. The process archive uses weak instance identities; floating availability latches after first visible discovery until disable/uninstall, independently of record clearing. Store no Views or mutable Navigation objects in history.
- Preserve 100 records per target, 500 process-wide and 2 MiB UTF-8 URL text. Discard whole oldest records, never truncate URLs. Clearing inspector history must not clear WebView browser history or re-add unchanged sampled URLs.
- Do not change WebView clients, settings, JavaScript bridges or HTML. Basic requires no individual screen changes; Advanced is opt-in through load helpers only. Snapshot raw app inputs synchronously before forwarding the original URL/map on the caller thread exactly once. Do not catch WebView's own exception. No input logs, disk storage, upload or analytics.
- Keep app loads separate from navigation. Never infer headers for redirects, sampled URLs, browser defaults, subresources or responses. Full values are intentionally visible. Bound pending/saved captures to 100 per instance, 500 process-wide and 2 MiB UTF-8 URL/header text independently of navigation budgets. Bind helper captures to archive identities immediately so never-discovered inputs remain in Recent without activating the floating control. Retain bound saved records across collection and Activity destruction; clear everything on disable/uninstall.
- Keep the implementation independent of `:app` and `:helper`; production must continue resolving only the no-op module. Keep resources prefixed `wvi_` / `Wvi`.

## Readability and documentation

- Prefer explicit control flow and one statement per line; avoid semicolon-packed code, nested one-line branches, and unexplained Boolean arguments.
- Preserve pinned actions, content-sized Current URL, stable per-instance row identities, and view-free per-target scroll/query preferences. Child windows and pending pre-draw work must be released with the parent.
- Name variables by role and layout/timing constants by purpose. Keep constants near their owner rather than making a global miscellaneous bag.
- Class KDoc should explain responsibility, ownership, thread expectations, and cleanup where relevant. Explain non-obvious invariants rather than narrating every statement.
- Update architecture diagrams, package tables, and maintenance pointers when responsibilities move. Keep historical validation evidence distinct from newly executed checks.

## Checks

Use JDK 17 and the committed wrapper. Set JAVA_HOME and ANDROID_HOME for your machine.
Run root `./gradlew spotlessApply` after source/build-script changes and
`./gradlew spotlessCheck` before verification. This includes JVM and native test hosts;
expand wildcard imports rather than suppressing their lint. Preserve test assertions,
API names and original URL/header literals. Use the root `.editorconfig` and Gradle
formatter in Android Studio instead of committing IDE metadata. The complete gate is
`bash scripts/verify.sh`, including formatting and Android Lint for every Android project.

```sh
./gradlew :webview-inspector:testDebugUnitTest :webview-inspector:lintDebug \
  :webview-inspector:assembleDebugAndroidTest :webview-inspector-no-op:assembleRelease
./gradlew :sample:assembleDebug :sample:assembleRelease
./gradlew :sample:dependencyInsight --configuration releaseRuntimeClasspath --dependency webview-inspector
```

Run all 31 inspector/history/gesture/Focused/App loads/Recent cases on an explicitly selected supported API 36 emulator, plus its navigation prerequisite. At 320 dp / 160% font rerun the 23 light and 16 dark cases. API 24 runs 18 cases: six Focused, two fallback/isolation, six App loads and four Recent lifecycle cases. The optional app clipboard probes run only with explicit expected arguments. Temporarily disable animations and restore settings. Never implicitly target physical devices or uninstall a user app to bypass a downgrade refusal. Stop competing Maestro instrumentation for native tests and restore it afterward.

Check full/no-op facade API parity, production resources/mapping isolation, and a minified-app copy action when moving implementation packages. Tests use local synthetic HTML. An authenticated live payment or backend flow is not proven by compilation or fixtures. Record unexercised scenarios in [VALIDATION.md](../VALIDATION.md).
