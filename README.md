<div align="center">

# WebView Inspector

**See current URLs, navigation history, and app-supplied headers in your Android WebViews.**

[Maven Central 0.4.2](https://repo.maven.apache.org/maven2/io/github/chinmaydash007/webview-inspector/0.4.2/)
· [Production no-op](https://repo.maven.apache.org/maven2/io/github/chinmaydash007/webview-inspector-no-op/0.4.2/)
· [Apache 2.0](LICENSE)

</div>

## See it in Afterframe

[Afterframe](sample/) is an Android sample with four WebView scenarios. Each demo starts with **Locate on page**, which outlines the selected WebView. **Basic** discovers visible WebViews after a single app-wide install. **Advanced** adds a record of the URL and headers passed to the explicit load helper.

| Home · Embedded WebViews | Movies · Offline navigation |
| :---: | :---: |
| <img src="sample/demo-media/home.gif" width="300" alt="Locating Now playing, then inspecting the separate URLs for Now playing and Your watchlist"> | <img src="sample/demo-media/movies.gif" width="300" alt="Locating Midnight Local, then inspecting its 6:45 PM showtime, query parameters, and history"> |
| Locate **Now playing**, then switch between it and **Your watchlist** to see the two embedded WebView URLs. | Locate the offline movie page, then open a showtime. Inspect its query parameters and find both pages in **History**. |
| [Watch Home MP4](sample/demo-media/home.mp4) | [Watch Movies MP4](sample/demo-media/movies.mp4) |

| Headers · Explicit app loads | Browser · Live navigation |
| :---: | :---: |
| <img src="sample/demo-media/headers.gif" width="300" alt="Locating the httpbin receipt, matching a synthetic demo header, then checking a load without supplied headers"> | <img src="sample/demo-media/browser.gif" width="300" alt="Locating the Google homepage, then visiting About and reviewing Back and Forward in Inspector History"> |
| Locate the live receipt, then compare a new request with **App loads** and send another without extra headers. **App loads** shows what the app supplied. The receipt shows what httpbin received. | Locate the Google homepage, then enter `google.com` and visit **About**. Use **Back** and **Forward**, then review the navigation in **History**. |
| [Watch Headers MP4](sample/demo-media/headers.mp4) | [Watch Browser MP4](sample/demo-media/browser.mp4) |

To try it, open the repo in Android Studio with JDK 17 and Android SDK 36,
then run the `sample` **debug** variant. Headers and Browser need internet access.

## Install

WebView Inspector supports Android API 24 and later. Use `google()` and
`mavenCentral()`, and include one artifact per build variant.

```kotlin
dependencies {
    debugImplementation("io.github.chinmaydash007:webview-inspector:0.4.2")
    releaseImplementation("io.github.chinmaydash007:webview-inspector-no-op:0.4.2")
}
```

Import `com.webviewinspector.WebViewInspector` and install it once in your
`Application.onCreate()`.

```kotlin
override fun onCreate() {
    super.onCreate()
    WebViewInspector.install(this)
}
```

Open a screen with a WebView and tap the floating Inspector. The upcoming 0.5.0 Live/Recent
chooser keeps captured history available across Activities after the first discovery,
including screens without WebViews. Published 0.4.2 retains the earlier picker behavior.
See the [0.5.0 release notes](docs/releases/0.5.0.md) for the pending release and its validation status.
Use the optional load
helper to see app-supplied inputs under **App loads**.

```kotlin
WebViewInspector.loadUrl(webView, url, headers)
```

[Full usage guide](USAGE.md) · [Architecture](webview-inspector/docs/ARCHITECTURE.md)
· [Contributing](CONTRIBUTING.md) · [Validation](VALIDATION.md) · [Releasing](docs/RELEASING.md)
