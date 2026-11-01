# Production no-op

Publishes `io.github.chinmaydash007:webview-inspector-no-op` with the same seven facade methods and package as full. Installation/settings mutations do nothing; `isEnabled()` returns false. The two `loadUrl` helpers synchronously forward the original arguments exactly once without capture, storage or exception handling. No inspector UI, observers, Material or WebKit dependency is included. Production can use the same Advanced integration call sites while loading normally.

Choose exactly one artifact per variant. See [integration](../README.md), [architecture](../webview-inspector/docs/ARCHITECTURE.md) and [validation](../VALIDATION.md).
