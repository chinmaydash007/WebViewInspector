# WebView Inspector

A floating Android inspector for native WebViews. **Basic** discovers full-page, embedded, subclassed, Compose `AndroidView`, and RecyclerView/Epoxy WebViews centrally, showing current URLs and navigation history. **Advanced** adds explicit app-load URL/header snapshots when developers use the load helper. Neither replaces WebView clients or injects JavaScript.

Check the available versions on Maven Central for the [full inspector](https://repo.maven.apache.org/maven2/io/github/chinmaydash007/webview-inspector/) and [production no-op](https://repo.maven.apache.org/maven2/io/github/chinmaydash007/webview-inspector-no-op/). See [validation](VALIDATION.md) for verified releases.

The GitHub repository, GitHub Releases and CI assets stay **private**. Maven Central AARs, complete source JARs and documentation are **public**. No public GitHub Pages site is configured.

## Integrate

Use `google()` and `mavenCentral()` for dependencies. Select exactly one artifact per app variant and use the same published version for both artifacts.

```kotlin
dependencies {
    debugImplementation("io.github.chinmaydash007:webview-inspector:0.4.2")
    releaseImplementation("io.github.chinmaydash007:webview-inspector-no-op:0.4.2")
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

### Upgrade from a version earlier than 0.4.0

Update both artifact versions together, replace the previous facade import with
`com.webviewinspector.WebViewInspector`, and recompile consumers. The package rename
breaks source and binary compatibility. The five original methods and both synchronous
load helpers keep their signatures.

## Use the inspector

Drag the floating control to an edge and tap its selected domain. Choose a WebView in the dropdown, inspect/copy its original Current URL, expand query parameters, or Locate its bounds. History shows newest-first navigation records and reported outcomes, with individual raw-URL copying, redirect steps, local-date groups and saved history after selection loss. Copy actions stay pinned. More contains Clear history and About capture.

Successful copies give a short haptic in Ring or Vibrate mode when Do Not Disturb is off and Android touch feedback is enabled. On Android 12L and older, the pressed Copy control briefly shows a checkmark and announces “Copied to clipboard” for TalkBack. Android 13 and newer can show their system clipboard confirmation.

ComponentActivity, AppCompatActivity, and non-Material host themes are supported through a library-themed native BottomSheetDialog. Content scrolls to the top before a separate downward gesture can dismiss the sheet. Native WebViews hosted by Compose are supported; Compose itself is not required by consumers.

## Compatibility and privacy

- Android API 24+. Tested build baseline: JDK 17, Gradle 8.13, AGP 8.12.0, Kotlin 2.2.0 and compile SDK 36. Older consumer toolchains are not claimed as tested.
- The full artifact declares WebKit 1.15.0 as the minimum and supports apps that resolve 1.16.0.
- Runtime feature detection uses the experimental navigation listener in 1.15 or the stable listener in 1.16. The inspector uses labelled 500 ms observed-URL sampling when the provider lacks support. Initial discovery cannot recover earlier redirects, and sampling can miss rapid changes. Reported commitment does not prove successful page rendering.
- Scope is visible native WebViews in the active Activity hierarchy. Separate popup/dialog windows, external browsers/UPI apps and iframe URLs are excluded.
- URLs and explicitly supplied headers remain in memory until copied. No logging, persistence, upload or analytics. Automatic cookies and request bodies are not collected. Query decoding is display-only; clipboard text remains exact.
- Retention is 100 records per WebView, 500 process-wide and 2 MiB of URL text. Whole oldest records are evicted without truncating URLs. Activity destruction, disabling and uninstall clear their owned records.
- App loads have a separate budget of 100 records per WebView, 500 process-wide and 2 MiB UTF-8 URL/header text, including pending captures. Whole oldest calls are evicted with a discarded count. Collected pending instances are pruned; bound saved records last until Activity cleanup or eviction. No-op owns no capture store or inspector UI; its load helpers only delegate.

For build and device verification, see [contributing](CONTRIBUTING.md) and
[validation](VALIDATION.md). See the [architecture](webview-inspector/docs/ARCHITECTURE.md)
for capture and lifecycle details, and [LICENSE](LICENSE) for the Apache 2.0 terms.
