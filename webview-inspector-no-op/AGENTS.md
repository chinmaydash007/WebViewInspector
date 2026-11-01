# Production no-op maintenance guidance

This file applies to this module. Read [README.md](README.md) and the [full library architecture](../webview-inspector/docs/ARCHITECTURE.md). User instructions take precedence.

- Preserve `io.github.chinmaydash007.webviewinspector.WebViewInspector` and Java static signatures for `install(Application)`, `showSettings(Activity)`, `setEnabled(Boolean)`, `isEnabled()`, and `uninstall()`.
- Match facade changes in the implementation module. The duplicate package/class is deliberate because variants select exactly one module.
- Keep methods inert and `isEnabled()` false. Add no lifecycle callbacks, observers, UI, URL access, persistence, or implementation dependencies.
- Preserve minSdk 24 and existing managed build versions. Do not add internal architecture layers to an empty implementation.
- Document production behavior and API compatibility in the README. Keep functions and comments readable.

Use JDK 17 and run `./gradlew :webview-inspector-no-op:assembleRelease :sample:assembleRelease`. Compare the five declared static methods against the full facade, inspect `:sample:dependencyInsight --configuration releaseRuntimeClasspath --dependency webview-inspector`, and verify production APK resources/R8 mapping contain no inspector UI or implementation. Do not claim minified/runtime behavior from compilation alone.
