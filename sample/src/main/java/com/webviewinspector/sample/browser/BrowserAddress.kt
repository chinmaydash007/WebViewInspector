package com.webviewinspector.sample.browser

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException

private val explicitScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
private val hostnameWithPort = Regex("^(?:[^/?#:@]+\\.[^/?#:@]+|localhost):[0-9]+(?:[/?#]|$)", RegexOption.IGNORE_CASE)

internal fun browserAddress(input: String): String? {
    val address = input.trim()
    if (address.isEmpty() || address.startsWith("//")) return null
    val url =
        when {
            address.startsWith("https://", ignoreCase = true) -> "https://" + address.substring(8)
            explicitScheme.containsMatchIn(address) && !hostnameWithPort.containsMatchIn(address) -> return null
            else -> "https://$address"
        }
    return try {
        val uri = URI(url)
        val authority = uri.rawAuthority ?: return null
        // URI.host is null for internationalized names. Validate their ASCII form without rewriting the URL.
        val server =
            if (uri.host != null) {
                uri
            } else {
                val hostAndPort = authority.substringAfter('@')
                val host = hostAndPort.substringBefore(':')
                val asciiHost = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
                val prefix = authority.removeSuffix(hostAndPort)
                URI("https://$prefix$asciiHost" + hostAndPort.drop(host.length)).parseServerAuthority()
            }
        url.takeIf { server.host != null && server.port <= 65535 && !authority.endsWith(':') }
    } catch (_: URISyntaxException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
