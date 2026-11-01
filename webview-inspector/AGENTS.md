# WebView Inspector maintenance guidance

Read the [root guide](../README.md), [architecture](docs/ARCHITECTURE.md), and [validation](../VALIDATION.md) before editing. This file applies only to this module. User instructions take precedence.

## Responsibilities

- Keep the public `io.github.chinmaydash007.webviewinspector.WebViewInspector` facade at its existing package with the same five Java-compatible static methods. Implementation types belong under `internal` and remain Kotlin `internal`.
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
- Keep WebKit strictly at 1.15.0 while supporting API 24. Isolate experimental APIs in `AndroidXNavigationEventSource`, check `NAVIGATION_LISTENER_V1`, and use labelled sampling when unsupported. Snapshot mutable Navigation values at provider dispatch before a main-thread hop.
- Remove subscriptions on hidden/detached/inactive targets and reject stale callbacks. History survives pause/resume with gaps; destruction releases the Activity scope and disabling/uninstall releases all scopes. Store no Views or mutable Navigation objects in history.
- Preserve 100 records per target, 500 process-wide and 2 MiB UTF-8 URL text. Discard whole oldest records, never truncate URLs. Clearing inspector history must not clear WebView browser history or re-add unchanged sampled URLs.
- Do not change WebView clients, settings, JavaScript bridges, HTML, or individual app screens. No URL logs, disk storage, upload, or analytics.
- Keep the implementation independent of `:app` and `:helper`; production must continue resolving only the no-op module. Keep resources prefixed `wvi_` / `Wvi`.

## Readability and documentation

- Prefer explicit control flow and one statement per line; avoid semicolon-packed code, nested one-line branches, and unexplained Boolean arguments.
- Preserve pinned actions, content-sized Current URL, stable per-instance row identities, and view-free per-target scroll/query preferences. Child windows and pending pre-draw work must be released with the parent.
- Name variables by role and layout/timing constants by purpose. Keep constants near their owner rather than making a global miscellaneous bag.
- Class KDoc should explain responsibility, ownership, thread expectations, and cleanup where relevant. Explain non-obvious invariants rather than narrating every statement.
- Update architecture diagrams, package tables, and maintenance pointers when responsibilities move. Keep historical validation evidence distinct from newly executed checks.

## Checks

Use JDK 17 and the committed wrapper. Set JAVA_HOME and ANDROID_HOME for your machine.

```sh
./gradlew :webview-inspector:testDebugUnitTest :webview-inspector:lintDebug \
  :webview-inspector:assembleDebugAndroidTest :webview-inspector-no-op:assembleRelease
./gradlew :sample:assembleDebug :sample:assembleRelease \
  :sample:assembleDiagnostic \
  :sample:assembleDebug
./gradlew :sample:dependencyInsight --configuration releaseRuntimeClasspath --dependency webview-inspector
```

Run all 21 inspector/history/gesture/Focused instrumentation cases on an explicitly selected API 36 emulator. Rerun the seven earlier narrow/large-font cases and the Focused cases at 320 dp / 160% font in light/dark modes. On an explicitly selected API 24 emulator, run the six Focused cases and the two supported fallback/isolation history cases. The optional app clipboard probes run only with explicit expected-URL arguments after copying in the minified app. Temporarily disable system animations and restore original settings afterward. Never target a connected physical device implicitly. Preserve existing app data; a version downgrade refusal is not authorization to uninstall that app.

Check full/no-op facade API parity, production resources/mapping isolation, and a minified-app copy action when moving implementation packages. Tests use local synthetic HTML. An authenticated live payment or backend flow is not proven by compilation or fixtures. Record unexercised scenarios in [VALIDATION.md](../VALIDATION.md).
