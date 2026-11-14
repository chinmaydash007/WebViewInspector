package com.webviewinspector.sample.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserAddressTest {
    @Test fun acceptsHttpsAndPreservesRawPathQueryAndFragment() {
        val path = "/one%2ftwo?raw=A%2Bb+c&dup=1&dup=2#Top"
        val expected = "https://example.com$path"
        assertEquals(expected, browserAddress("  HTTPS://example.com$path  "))
        assertEquals(expected, browserAddress("example.com$path"))
    }

    @Test fun supportsPortsIpLiteralsAndInternationalHostnames() {
        for (host in listOf("example.com:8443", "localhost:8443", "[::1]:8443", "bücher.example")) {
            assertEquals("https://$host/", browserAddress("$host/"))
        }
    }

    @Test fun rejectsMalformedAddressesAndOtherSchemes() {
        for (input in listOf(
            "",
            "https:///path",
            "//example.com",
            "https://a b.example",
            "https://example.com:65536",
            "https://example.com:",
            "https://example.com/%not-an-escape",
            "http://example.com",
            "file:///android_asset/index.html",
            "javascript:alert(1)",
            "intent://example.com",
            "ftp://example.com",
            "https://a@b@example.com",
        )) {
            assertNull(input, browserAddress(input))
        }
    }
}
