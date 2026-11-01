# Inspector implementation

See the [integration guide](../README.md), [architecture](docs/ARCHITECTURE.md), [validation](../VALIDATION.md) and [maintenance rules](AGENTS.md).

This module publishes `io.github.chinmaydash007:webview-inspector`. Only `io.github.chinmaydash007.webviewinspector.WebViewInspector` is supported public API. Runtime code is native Views/controllers; Compose exists only in test hosts. Sample permissions, host declarations, HTML, servers and test dependencies do not ship.

Basic centrally inspects visible URLs/navigation. Advanced adds the two explicit
`loadUrl` helpers and App loads; it snapshots app-supplied inputs without replacing
clients or claiming network headers. The five original facade methods remain intact.
