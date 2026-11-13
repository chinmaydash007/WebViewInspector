# WebView Inspector

A floating Android inspector for native WebViews. **Basic** discovers full-page, embedded, subclassed, Compose `AndroidView`, and RecyclerView/Epoxy WebViews centrally, showing current URLs and navigation history. **Advanced** adds explicit app-load URL/header snapshots when developers use the load helper. Neither replaces WebView clients or injects JavaScript.

**Latest published version:** `0.4.0`. Both artifacts are available on Maven Central. All ten primary files match the signed release bundle. See [validation](VALIDATION.md).

The GitHub repository, GitHub Releases and CI assets stay **private**. Maven Central AARs, complete source JARs and documentation are **public**. No public GitHub Pages site is configured.

## Integrate

Use `google()` and `mavenCentral()` for dependencies. Select exactly one artifact per app variant.

```kotlin
dependencies {
    debugImplementation("io.github.chinmaydash007:webview-inspector:0.4.0")
    releaseImplementation("io.github.chinmaydash007:webview-inspector-no-op:0.4.0")
}
```

For custom build types, including diagnostic builds, choose one artifact using that build type's `Implementation` configuration. Never include full and no-op artifacts on the same variant's classpath: they intentionally contain the same facade.

Install centrally in your Application:

```kotlin
override fun onCreate() {
    super.onCreate()
    WebViewInspector.install(this)
}
```

Java uses `WebViewInspector.install(this);` in `Application.onCreate()`. The import is `com.webviewinspector.WebViewInspector` for both languages and artifacts.

The original five methods remain `install(Application)`, `showSettings(Activity)`, `setEnabled(Boolean)`, `isEnabled()` and `uninstall()`. These mutations dispatch to the main thread; install/uninstall are idempotent. Settings, toggle and floating position are process-only. The no-op facade installs nothing and always reports disabled.

### Advanced: inspect app-supplied headers

Replace a load call, keeping your WebView and its clients:

```kotlin
WebViewInspector.loadUrl(webView, url, headers)
// Captures a separate call with no headers, without inheriting previous values:
WebViewInspector.loadUrl(webView, nextUrl)
```

Java uses the same static calls. Both overloads load **exactly once**, synchronously on
the calling WebView UI thread, with the original URL/map. They do not post loading work
or change WebView exceptions. Capture snapshots inputs before delegation; later map
mutation does not change saved values. Calls before installation, while disabled, or
through no-op still load normally and retain nothing. Inspection failures never prevent
loading. Existing users can keep all ordinary `webView.loadUrl` calls for Basic.

Open **App loads** for timestamped calls, complete URLs and full header values, including
Authorization. Copy a URL, individual value, whole header list, or all app loads. Empty
header maps are explicitly labelled. A Basic target has no captured calls; using the
helper marks only that instance Advanced. Clearing its app loads retains that capability
until disable/uninstall or Activity destruction. Multiple Basic/Advanced instances coexist.
Advanced means that at least one helper call was captured; subsequent ordinary
`webView.loadUrl` calls still do not produce header records.

These are **app-supplied inputs, not verified network headers**. WebView may override
some headers. Browser defaults, automatic cookies, responses, subresources, subsequent
redirect headers, POST bodies and JavaScript requests are excluded. A captured call is
not evidence of a network request or successful loading. Navigation history remains a
separate timeline; captured headers are never assigned to another URL by inference.

Records captured before attachment remain weak and bounded until discovery associates
their exact instance. Saved records remain copyable after live selection disappears.
Header copying preserves names, values and captured iteration order (`Name: value`
lines); URL/value copying is exact. Timeline export adds timestamps and capture limits.
Android 13+ system clipboard previews are marked sensitive; the inspector still shows
full values and the copied text is unchanged.

### Upgrade to 0.4.0

Update both artifact versions together, replace the previous facade import with
`com.webviewinspector.WebViewInspector`, and recompile consumers. The package rename
breaks source and binary compatibility. The five original methods and both synchronous
load helpers keep their signatures.

## Use the inspector

Drag the floating control to an edge and tap its selected domain. Choose a WebView in the dropdown, inspect/copy its original Current URL, expand query parameters, or Locate its bounds. History shows newest-first navigation records and reported outcomes, with individual raw-URL copying, redirect steps, local-date groups and saved history after selection loss. Copy actions stay pinned. More contains Clear history and About capture.

ComponentActivity, AppCompatActivity, and non-Material host themes are supported through a library-themed native BottomSheetDialog. Content scrolls to the top before a separate downward gesture can dismiss the sheet. Native WebViews hosted by Compose are supported; Compose itself is not required by consumers.

## Compatibility and privacy

- Android API 24+. Tested build baseline: JDK 17, Gradle 8.13, AGP 8.12.0, Kotlin 2.2.0 and compile SDK 36. Older consumer toolchains are not claimed as tested.
- The 0.4.0 full artifact declares WebKit 1.15.0 as the minimum and supports apps that resolve 1.16.0.
- Runtime feature detection uses the experimental navigation listener in 1.15 or the stable listener in 1.16. The inspector uses labelled 500 ms observed-URL sampling when the provider lacks support. Initial discovery cannot recover earlier redirects, and sampling can miss rapid changes. Reported commitment does not prove successful page rendering.
- Scope is visible native WebViews in the active Activity hierarchy. Separate popup/dialog windows, external browsers/UPI apps and iframe URLs are excluded.
- URLs and explicitly supplied headers remain in memory until copied. No logging, persistence, upload or analytics. Automatic cookies and request bodies are not collected. Query decoding is display-only; clipboard text remains exact.
- Retention is 100 records per WebView, 500 process-wide and 2 MiB of URL text. Whole oldest records are evicted without truncating URLs. Activity destruction, disabling and uninstall clear their owned records.
- App loads have a separate budget of 100 records per WebView, 500 process-wide and 2 MiB UTF-8 URL/header text, including pending captures. Whole oldest calls are evicted with a discarded count. Collected pending instances are pruned; bound saved records last until Activity cleanup or eviction. No-op owns no capture store or inspector UI; its load helpers only delegate.

## Develop and validate

Use JDK 17 and Android SDK 36. From the repository root:

```sh
./gradlew spotlessCheck   # Check formatting and Kotlin style without rewriting files
./gradlew spotlessApply   # Format covered source and Gradle files
```

Spotless 8.11.0 uses ktlint 1.8.0 for Kotlin/Gradle Kotlin scripts and
google-java-format 1.28.0 (AOSP style) for Java. The root `.editorconfig` defines UTF-8,
LF endings, four-space indentation, Kotlin's official ktlint style at 120 columns,
and Java's formatter-defined 100-column layout. Versions are pinned in the version
catalog. These tools are build dependencies only.

Formatting covers the implementation, no-op, sample, tests and independent Java
consumer, including their Gradle Kotlin scripts. Generated sources, build outputs,
Gradle caches, `.tools`, `.idea` and Git metadata are excluded. Python, XML, YAML,
Markdown, JSON and properties files are outside automatic formatting.

In Android Studio, select JDK 17 as the Gradle JVM and run `spotlessApply` or
`spotlessCheck` from the Gradle tool window. EditorConfig supplies editor defaults;
the Gradle formatter is authoritative. No IDE plugin or shared `.idea` configuration
is required. CI checks formatting; apply fixes locally before submitting changes.

Run the complete non-publishing verification gate:

```sh
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk bash scripts/verify.sh
```

This checks formatting first, then runs JVM tests, Android Lint for the full/no-op
libraries, sample and consumer, native test APK assembly, both sample build types,
full/no-op API checks, unsigned local publication validation, publishing-script tests,
and an independent **Java-only** Maven consumer, including R8. Android Lint errors
fail verification; warnings remain reported under the existing policy. It never
uploads to Central. The consumer uses coordinates rather than project dependencies
and has no Compose or Kotlin Gradle plugin. Root formatting tasks also cover its
source/build files without applying a formatting plugin to the consumer build.

Run native tests only on an explicitly selected emulator:

```sh
python3 scripts/device_tests.py --serial emulator-5554 --api 36
python3 scripts/device_tests.py --serial emulator-5556 --api 24
```

API 36 requires an installed provider supporting navigation events; the prerequisite fails instead of silently skipping event tests. The script restores font, density, night mode and animation settings. It rejects physical devices. The sample uses the full inspector in debug and the no-op artifact in its minified release. Sample pages and real HTTP redirects use loopback, with cleartext exceptions confined to sample/test manifests.

Read [architecture](webview-inspector/docs/ARCHITECTURE.md), [release workflow](docs/RELEASING.md), [validation](VALIDATION.md), [contribution guidance](CONTRIBUTING.md), and [maintenance rules](AGENTS.md). Release source/documentation JARs contain this guide and the [Apache 2.0 license](LICENSE), so consumers do not need private GitHub access.
