package io.github.chinmaydash007.webviewinspector.sample

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.visit.webviewinspector.WebViewInspector

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
            text = "WebView Inspector sample\n\nUse the floating control to select a WebView, copy its exact URL, or inspect navigation history. The release build uses the no-op artifact. All pages are served inside this app on loopback."
            textSize = 18f
        })
        fun action(title: String, run: () -> Unit) = content.addView(Button(this).apply { text = title; setOnClickListener { run() } })
        action("Compose · two embedded WebViews") { startActivity(Intent(this, ComposeWebViewsActivity::class.java)) }
        action("XML · full-page WebView") { startActivity(Intent(this, XmlWebViewActivity::class.java)) }
        action("Inspector settings") { WebViewInspector.showSettings(this) }
        setContentView(content)
    }
}
