# Contributing

Repository access is currently private. Consumers can use public Maven Central artifacts after release, but private source browsing and GitHub issues require granted access.

Use JDK 17, the committed wrapper, and Android SDK 36. Run `bash scripts/verify.sh`; run native coverage on explicitly selected API 24 and supported API 36 emulators. Preserve the five original methods and two load helpers with full/no-op parity. Keep both artifacts versioned together. Runtime changes need tests and architecture notes. Advanced capture is opt-in via the helper; never add production telemetry or implicit client interception.

Before verification, run `./gradlew spotlessApply` to format Kotlin, Java and Gradle
Kotlin scripts. `./gradlew spotlessCheck` checks the entire covered tree without
rewriting files and is the first step in `scripts/verify.sh`, including CI. Follow the
root `.editorconfig`: official ktlint Kotlin style, four-space indentation, 120-column
Kotlin and AOSP Java's 100-column layout. Expand wildcard imports into explicit imports
rather than disabling lint rules. Keep API names and literal URL/header values intact.

The same tasks cover full/no-op, samples, tests and the independent Java consumer.
Build/generated files, caches, `.tools`, `.idea` and Git metadata are excluded; Python,
XML, YAML, Markdown, JSON and properties are not automatically formatted. Run these
tasks in Android Studio's Gradle tool window with Gradle JDK 17; the Gradle formatter
defines the result even if the editor's built-in reformat action differs. No IDE plugin
or shared IDE metadata is necessary. Formatting versions live in the existing catalog,
and formatting tools must not become published runtime dependencies.

Open changes against main. CI validates without signing or Central credentials. Maintainers prepare a changelog, run signed dry-run and native gates, then publish the private GitHub Release to trigger public Central publication. Follow docs/RELEASING.md; ordinary merges never publish.
