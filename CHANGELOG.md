# Changelog

## 0.5.0 — Pending publication

- Add Live and Recent WebViews with saved URLs, navigation history and explicit app-load inputs across Activity changes and destruction.
- Use a rounded Live/Recent segmented control with a sliding selection surface, separate count badges, subtle press feedback and accessible selected states.
- Keep the floating Inspector available on foreground app screens after the first visible WebView is detected, even when no WebViews or saved records remain.
- Include explicit helper captures from never-discovered WebViews in Recent, labelled as app-supplied inputs.
- Refresh all four Afterframe demo recordings and add a Live/Recent demo with no visible WebViews.
- Assign session-stable WebView numbers; retain bounded records in memory until clearing, eviction, disable/uninstall or process death. No public API or integration changes.
- Release preparation is in progress. Maven Central download verification is pending for both artifacts; 0.4.2 remains the latest verified published version.

## 0.4.2 — 15 November 2026

- Draw the Inspector and Settings sheets behind the system navigation bar while keeping controls clear of navigation and display cutout insets.
- Match navigation icon contrast to the sheet surface on API 26 and later. Tint the navigation area on API 24–25 to keep the system's white buttons readable.
- Both artifacts are available on Maven Central. All ten primary files match the signed release bundle. See [validation](VALIDATION.md).

## 0.4.1 — 14 November 2026

- Improve border contrast in dark mode and keep the inspector sheet on its intended surface color.
- Align the Close icon with the sheet's trailing content edge while preserving its touch target.
- Confirm successful clipboard copies with haptic feedback when allowed by system settings. On Android 12L and older, also show a brief checkmark and announce the copy for accessibility.
- No integration changes are required when upgrading from 0.4.0. Keep the full and no-op artifacts on the same version.
- Both artifacts are available on Maven Central. All ten primary files match the signed release bundle. See [validation](VALIDATION.md).

## 0.4.0 — 10 November 2026

- Version 0.4.0 moves both facades to `com.webviewinspector.WebViewInspector`. Update imports and recompile when upgrading. Both artifacts retain the Maven group `io.github.chinmaydash007`.
- Support WebKit 1.15.0 and 1.16.0 navigation events in the same AAR. Declare WebKit 1.15.0 as the minimum so consumers can select 1.16.0.
- Both artifacts are available on Maven Central. All ten primary files match the signed release bundle. See [validation](VALIDATION.md).

## 0.3.0 — 1 November 2026

- Preserve Basic central URL/history inspection and add opt-in Advanced capture per WebView.
- Add synchronous Java/Kotlin loadUrl helpers, with exact single delegation in full/no-op and immutable app-supplied URL/header snapshots only when inspection is installed/enabled.
- Add App loads with full header values, saved records, exact copying and independent bounded retention, including captures before attachment. No network/redirect header inference.
- Extend native hosts, mixed-mode XML/Compose samples and the Java-only consumer.
- Both artifacts are published on Maven Central. All ten primary public files match the original signed bundle; the GitHub repository and Release remain private. Downstream application adoption is a separate phase. See [validation](VALIDATION.md).

## 0.2.0 — 1 November 2026

- Breaking namespace migration: the public facade is now `io.github.chinmaydash007.webviewinspector.WebViewInspector` in both artifacts, with no legacy alias. Update dependency versions and the facade import together.
- Moved implementation, tests, manifests, generated-resource references and automation to the independent publisher identity. Preserved the five methods, API 24, UI and behavior.
- Replaced host-specific documentation and extraction wording with project-neutral guidance and notices, retaining the Apache license and third-party notices.
- Added archive-content, namespace, dependency-inventory and documentation checks before publication, including strict WebKit pinning and no-op isolation.
- Both artifacts are published on Maven Central. All ten primary public files match the original signed bundle; the independent Java consumer builds from public repositories alone. The GitHub repository remains private. See [validation](VALIDATION.md).

## 0.1.0 — 31 October 2026

- Initial independent Android library and matching production no-op artifact.
- Automatic visible WebView discovery, draggable control, exact URL copying and timed Locate.
- Focused native inspector with selection dropdown, Current URL and per-instance navigation history.
- Navigation events when supported, labelled sampling on unsupported providers, bounded in-memory records and lifecycle cleanup.
- Java/Kotlin facade, Compose ComponentActivity and XML AppCompatActivity compatibility.
- Signed release workflow from private GitHub to public Maven Central.
- Minimum SDK 24 for both artifacts. Public AAR, POM, Gradle metadata, sources and documentation downloads verified against the original signed bundle.
