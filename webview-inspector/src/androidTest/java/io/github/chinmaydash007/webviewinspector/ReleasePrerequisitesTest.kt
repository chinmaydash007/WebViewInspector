package io.github.chinmaydash007.webviewinspector

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chinmaydash007.webviewinspector.internal.core.AndroidXNavigationEventSource
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Release gate: event-only history tests must not silently skip on an unsupported provider. */
@RunWith(AndroidJUnit4::class)
class ReleasePrerequisitesTest {
    @Test
    fun navigationEventsAreAvailable() {
        assertTrue(
            "Install a WebView provider supporting navigation events with this WebKit version.",
            AndroidXNavigationEventSource.supportsNavigationEvents(),
        )
    }
}
