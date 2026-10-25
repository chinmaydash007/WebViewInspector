package example.consumer;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/** Java-only consumer: synthetic HTTPS content, without a Compose or Kotlin Gradle plugin. */
public final class ConsumerActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WebView browser = new WebView(this);
        browser.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                byte[] html = "<h2>Independent Java consumer</h2>".getBytes(StandardCharsets.UTF_8);
                return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html));
            }
        });
        setContentView(browser);
        browser.loadUrl("https://consumer.example/start?raw=A%2Bb&dup=1&dup=2#raw");
    }
}
