# Changelog

## 0.2.0 — release preparation

- Breaking namespace migration: the public facade is now `io.github.chinmaydash007.webviewinspector.WebViewInspector` in both artifacts, with no legacy alias. Update dependency versions and the facade import together.
- Moved implementation, tests, manifests, generated-resource references and automation to the independent publisher identity. Preserved the five methods, API 24, UI and behavior.
- Replaced host-specific documentation and extraction wording with project-neutral guidance and notices, retaining the Apache license and third-party notices.
- Added archive-content, namespace, dependency-inventory and documentation checks before publication, including strict WebKit pinning and no-op isolation.
- Publication remains pending until signed release gates and anonymous Central download verification complete; see [validation](VALIDATION.md).

## 0.1.0 — 31 October 2026

- Initial independent Android library and matching production no-op artifact.
- Automatic visible WebView discovery, draggable control, exact URL copying and timed Locate.
- Focused native inspector with selection dropdown, Current URL and per-instance navigation history.
- Navigation events when supported, labelled sampling on unsupported providers, bounded in-memory records and lifecycle cleanup.
- Java/Kotlin facade, Compose ComponentActivity and XML AppCompatActivity compatibility.
- Signed release workflow from private GitHub to public Maven Central.
- Minimum SDK 24 for both artifacts. Public AAR, POM, Gradle metadata, sources and documentation downloads verified against the original signed bundle.
