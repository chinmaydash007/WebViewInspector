# Production no-op maintenance guidance

This file applies to this module. Read [README.md](README.md) and the [full library architecture](../webview-inspector/docs/ARCHITECTURE.md). User instructions take precedence.

- Preserve `com.webviewinspector.WebViewInspector` and Java static signatures for `install(Application)`, `showSettings(Activity)`, `setEnabled(Boolean)`, `isEnabled()`, and `uninstall()`.
- Match facade changes in the implementation module. The duplicate package/class is deliberate because variants select exactly one module.
- Keep lifecycle/settings methods inert and `isEnabled()` false. Both loadUrl helpers delegate the original arguments exactly once on the caller thread, without reading/retaining them or catching WebView errors. Add no lifecycle callbacks, observers, UI, input storage, or implementation dependencies.
- Preserve minSdk 24 and existing managed build versions. Do not add internal architecture layers to an empty implementation.
- Document production behavior and API compatibility in the README. Keep functions and comments readable.
- Follow root `.editorconfig` and pinned formatters. Run root `./gradlew spotlessApply` and `./gradlew spotlessCheck`; checks must not rewrite sources. Formatting must preserve the seven static methods and exact helper forwarding. Formatting dependencies belong only to root tooling and must never enter this artifact or its consumer's plugins.

Use JDK 17 and run `bash scripts/verify.sh` for formatting, Android Lint and the full
non-publishing gate. Focused builds remain
`./gradlew :webview-inspector-no-op:assembleRelease :sample:assembleRelease`. Compare all
seven static methods against full, verify synchronous helper delegation, inspect release
dependency isolation and production resources/R8 mapping. Do not claim minified/runtime
behavior from compilation alone.
