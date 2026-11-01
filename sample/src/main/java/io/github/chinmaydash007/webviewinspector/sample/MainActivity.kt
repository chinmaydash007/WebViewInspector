package io.github.chinmaydash007.webviewinspector.sample

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.chinmaydash007.webviewinspector.WebViewInspector

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left + 24, bars.top + 24, bars.right + 24, bars.bottom + 24)
            insets
        }
        content.addView(TextView(this).apply {
            text = "WebView Inspector sample\n\nBasic discovers URLs and navigation automatically. Advanced captures app-supplied URLs and synthetic headers through the load helper. Select App loads to inspect those inputs; navigation and redirect headers are not inferred. Release uses no-op and still loads normally. Pages use loopback."
            textSize = 18f
        })
        fun action(title: String, run: () -> Unit) = content.addView(Button(this).apply { text = title; setOnClickListener { run() } })
        action("Compose · Basic and Advanced together") { startActivity(Intent(this, ComposeWebViewsActivity::class.java)) }
        action("XML · Advanced with real redirects") { startActivity(Intent(this, XmlWebViewActivity::class.java)) }
        action("Inspector settings") { WebViewInspector.showSettings(this) }
        setContentView(content)
    }
}
