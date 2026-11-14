package com.webviewinspector.sample

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.webviewinspector.WebViewInspector
import com.webviewinspector.sample.browser.BrowserTab
import com.webviewinspector.sample.catalog.Film
import com.webviewinspector.sample.catalog.WatchlistStore
import com.webviewinspector.sample.databinding.ActivityMainBinding
import com.webviewinspector.sample.headers.HeaderReceipt
import com.webviewinspector.sample.home.HomeTab
import com.webviewinspector.sample.movies.MoviesTab

class MainActivity : AppCompatActivity() {
    private lateinit var views: ActivityMainBinding
    private lateinit var receipt: HeaderReceipt
    private lateinit var browserTab: BrowserTab
    private lateinit var homeTab: HomeTab
    private lateinit var moviesTab: MoviesTab
    private var tab = Tab.HOME
    private var selectedPage: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val dark =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = if (Build.VERSION.SDK_INT >= 26) Color.TRANSPARENT else Color.BLACK
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        views = ActivityMainBinding.inflate(layoutInflater)
        setContentView(views.root)
        ViewCompat.setOnApplyWindowInsetsListener(views.root) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            views.root.updatePadding(left = safe.left, right = safe.right)
            views.topBar.updatePadding(top = safe.top)
            views.bottomBar.updatePadding(bottom = safe.bottom + bottomBarPadding)
            insets
        }
        val watchlist = WatchlistStore(this)
        homeTab = HomeTab(views.homePage, watchlist) { film -> select(Tab.MOVIES, film) }
        moviesTab =
            MoviesTab(
                views.moviesWeb,
                watchlist,
                savedInstanceState?.getBundle(STATE_MOVIES),
                homeTab::invalidateWatchlist,
            )
        val showSettings = resources.getBoolean(R.bool.show_inspector_settings)
        receipt = HeaderReceipt(views.headersPage, showSettings)
        browserTab = BrowserTab(views.browserPage, window, savedInstanceState?.getBundle(STATE_BROWSER))

        views.inspectorSettings.isVisible = showSettings
        views.inspectorSettings.setOnClickListener { WebViewInspector.showSettings(this) }
        views.bottomBar.setOnItemSelectedListener { item ->
            select(Tab.entries.first { it.menuId == item.itemId })
            true
        }
        select(Tab.entries[savedInstanceState?.getInt(STATE_TAB) ?: 0])
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (tab == Tab.BROWSER && browserTab.goBack()) return
                    if (tab == Tab.MOVIES && moviesTab.goBack()) return
                    if (tab != Tab.HOME) {
                        select(Tab.HOME)
                        return
                    }
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            },
        )
    }

    private val bottomBarPadding by lazy { views.bottomBar.paddingBottom }

    private fun select(
        destination: Tab,
        film: Film? = null,
    ) {
        if (tab == Tab.BROWSER && destination != Tab.BROWSER) {
            browserTab.finishEditing()
        }
        val previousPage = selectedPage
        tab = destination
        val page =
            when (tab) {
                Tab.HOME -> views.homePage.root
                Tab.MOVIES -> views.moviesWeb.root
                Tab.HEADERS -> views.headersPage.root
                Tab.BROWSER -> views.browserPage.root
            }
        selectedPage = page
        previousPage?.takeIf { it !== page }?.resetSampleFade()
        views.homePage.root.isVisible = tab == Tab.HOME
        views.moviesWeb.root.isVisible = tab == Tab.MOVIES
        views.headersPage.root.isVisible = tab == Tab.HEADERS
        views.browserPage.root.isVisible = tab == Tab.BROWSER
        // Check the menu item directly so programmatic navigation doesn't re-enter its listener.
        views.bottomBar.menu
            .findItem(tab.menuId)
            .isChecked = true
        views.root.setBackgroundColor(getColor(tab.background))
        views.topBar.setBackgroundColor(getColor(tab.appBar))
        if (previousPage != null && previousPage !== page) page.fadeInSampleContent()

        if (tab == Tab.HOME) homeTab.refreshWatchlistIfNeeded()
        if (tab == Tab.MOVIES) moviesTab.show(film)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_TAB, tab.ordinal)
        moviesTab.saveState()?.let { outState.putBundle(STATE_MOVIES, it) }
        outState.putBundle(STATE_BROWSER, browserTab.saveState())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        receipt.dispose()
        homeTab.dispose()
        moviesTab.dispose()
        browserTab.dispose()
        super.onDestroy()
    }

    private enum class Tab(
        val menuId: Int,
        val background: Int = R.color.sample_bg,
        val appBar: Int = R.color.sample_app_bar,
    ) {
        HOME(R.id.tab_home),
        MOVIES(R.id.tab_movies, R.color.sample_movies_bg, R.color.sample_movies_bar),
        HEADERS(R.id.tab_headers),
        BROWSER(R.id.tab_browser, R.color.sample_browser_bg, R.color.sample_browser_bar),
    }

    private companion object {
        const val STATE_TAB = "selectedTab"
        const val STATE_BROWSER = "browserState"
        const val STATE_MOVIES = "moviesState"
    }
}
