package com.webviewinspector.internal.ui.sheet

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.core.widget.NestedScrollView
import com.webviewinspector.R
import com.webviewinspector.internal.state.AppLoadCopyPart
import com.webviewinspector.internal.state.AppLoadRecord
import com.webviewinspector.internal.state.AppLoadsSnapshot
import com.webviewinspector.internal.state.HistoryScrollAnchor
import com.webviewinspector.internal.state.InspectorSheetState
import com.webviewinspector.internal.state.TargetSheetState
import com.webviewinspector.internal.ui.common.color
import com.webviewinspector.internal.ui.common.dp
import com.webviewinspector.internal.ui.common.padding
import com.webviewinspector.internal.ui.common.shape
import com.webviewinspector.internal.util.HistoryPresentation
import com.webviewinspector.internal.util.UrlPresentation

/**
 * Dialog-owned, main-thread rendering of immutable app-load inputs. Keyed rows and lazy details
 * preserve focus/expansion while capture prepends records. No WebViews, clients or live URLs are
 * accessed here. Detachment releases pending scroll work; preferences own only IDs and offsets.
 */
internal class AppLoadsPanel(
    private val context: Context,
    private val presentation: InspectorSheetState,
    private val scroll: NestedScrollView,
    private val copy: (Long, AppLoadCopyPart) -> Unit,
    about: () -> Unit,
    private val contentChanged: () -> Unit,
) {
    val view =
        LinearLayout(context).apply {
            id = R.id.wvi_app_loads_panel
            orientation = LinearLayout.VERTICAL
            padding(horizontal = 20, vertical = 0)
        }
    private val saved = context.focusedLabel(size = 12f, muted = true)
    private val discarded = context.focusedLabel(size = 12f, muted = true)
    private val empty = context.focusedLabel(muted = true).apply { padding(horizontal = 0, vertical = 24) }
    private val entries = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val rows = linkedMapOf<Long, LoadRow>()
    private var snapshot: AppLoadsSnapshot? = null
    private var pendingScroll: OneShotPreDrawListener? = null

    init {
        view.addView(
            LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    context.focusedLabel(context.getString(R.string.wvi_app_loads_boundary), size = 12f, muted = true),
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
                )
                addView(
                    context.focusedIcon(R.drawable.wvi_ic_info, context.getString(R.string.wvi_about_capture), about),
                )
            },
        )
        view.addView(saved)
        view.addView(discarded)
        view.addView(empty)
        view.addView(entries)
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) = clearPendingScroll()
            },
        )
    }

    fun render(
        value: AppLoadsSnapshot?,
        number: Int?,
        live: Boolean,
    ) {
        saved.visibility = if (!live && value != null) View.VISIBLE else View.GONE
        saved.text = context.getString(R.string.wvi_app_loads_saved, number ?: 1)
        discarded.visibility = if ((value?.discardedCount ?: 0) > 0) View.VISIBLE else View.GONE
        discarded.text = context.getString(R.string.wvi_app_loads_discarded, value?.discardedCount ?: 0)
        empty.visibility = if (value?.records.isNullOrEmpty()) View.VISIBLE else View.GONE
        empty.text =
            if (value ==
                null
            ) {
                context.getString(R.string.wvi_app_loads_basic_empty) + "\n\n" +
                    context.getString(R.string.wvi_app_loads_basic_help)
            } else {
                context.getString(R.string.wvi_app_loads_advanced_empty)
            }
        if (snapshot == value) return
        val previousKey = snapshot?.key
        val anchor = entries.findFocus() ?: rows.values.firstOrNull { it.view.getGlobalVisibleRect(Rect()) }?.view
        val previousTop = anchor?.let(::screenTop)
        val preserve = previousKey == value?.key && (scroll.scrollY > 0 || entries.findFocus() != null)
        if (previousKey != value?.key) {
            entries.removeAllViews()
            rows.clear()
        }
        snapshot = value
        val records = value?.records.orEmpty()
        val ids = records.map { it.id }.toSet()
        value?.let { presentation.target(it.key.targetId).expandedAppLoadIds.retainAll(ids) }
        rows.keys.filter { it !in ids }.forEach { entries.removeView(rows.remove(it)?.view) }
        records.forEachIndexed { index, record ->
            val row = rows.getOrPut(record.id) { LoadRow(record) }
            row.renderExpansion()
            if (row.view.parent == null) entries.addView(row.view, index)
        }
        if (preserve && anchor != null && previousTop != null) {
            beforeDraw { if (anchor.isAttachedToWindow) scroll.scrollBy(0, screenTop(anchor) - previousTop) }
        }
    }

    fun saveScroll(preferences: TargetSheetState) {
        preferences.appLoadsScrollY = scroll.scrollY
        preferences.appLoadsAnchor =
            rows.entries.firstOrNull { it.value.view.getGlobalVisibleRect(Rect()) }?.let {
                HistoryScrollAnchor(it.key, screenTop(it.value.view) - screenTop(scroll))
            }
    }

    fun restoreScroll(preferences: TargetSheetState?) =
        beforeDraw {
            scroll.scrollTo(0, preferences?.appLoadsScrollY ?: 0)
            val anchor = preferences?.appLoadsAnchor
            val row = anchor?.let { rows[it.recordId]?.view }
            if (row != null && anchor != null) scroll.scrollBy(0, screenTop(row) - screenTop(scroll) - anchor.offset)
        }

    private inner class LoadRow(
        private val record: AppLoadRecord,
    ) {
        val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private var detailsBuilt = false
        private val arrow =
            ImageView(context).apply {
                setImageResource(R.drawable.wvi_ic_chevron)
                imageTintList =
                    android.content.res.ColorStateList
                        .valueOf(context.color(R.color.wvi_muted))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        private val expand =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = context.dp(76)
                padding(horizontal = 0, vertical = 13)
                isClickable = true
                isFocusable = true
                tag = "app-load-${record.id}"
                addView(
                    LinearLayout(context).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            context.focusedLabel(UrlPresentation.path(record.rawUrl), medium = true).apply {
                                maxLines = 2
                                ellipsize = TextUtils.TruncateAt.END
                            },
                            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
                        )
                        addView(arrow, LinearLayout.LayoutParams(context.dp(16), context.dp(16)))
                    },
                )
                addView(context.focusedLabel(UrlPresentation.domain(record.rawUrl), size = 12f, muted = true))
                addView(
                    context.focusedLabel(
                        HistoryPresentation.time(record.atMillis) + " · " +
                            context.getString(R.string.wvi_app_load_headers_count, record.headers.size),
                        size = 12f,
                        muted = true,
                    ),
                )
                ViewCompat.setScreenReaderFocusable(this, true)
                setOnClickListener {
                    val expanded = preferences()?.expandedAppLoadIds ?: return@setOnClickListener
                    if (!expanded.add(record.id)) expanded.remove(record.id)
                    renderExpansion()
                    contentChanged()
                }
            }

        init {
            view.addView(expand)
            view.addView(details)
            view.addView(context.focusedDivider())
        }

        fun renderExpansion() {
            val expanded = record.id in preferences()?.expandedAppLoadIds.orEmpty()
            if (expanded && !detailsBuilt) buildDetails()
            details.visibility = if (expanded) View.VISIBLE else View.GONE
            expand.isSelected = expanded
            arrow.rotation = if (expanded) 180f else 0f
            expand.contentDescription =
                context.getString(
                    if (expanded) R.string.wvi_app_load_collapse else R.string.wvi_app_load_expand,
                    UrlPresentation.path(record.rawUrl),
                    record.headers.size,
                    HistoryPresentation.time(record.atMillis),
                )
        }

        private fun buildDetails() {
            detailsBuilt = true
            details.addView(rawText(record.rawUrl))
            details.addView(
                context.focusedButton(context.getString(R.string.wvi_app_load_copy_url)) {
                    copy(record.id, AppLoadCopyPart.Url)
                },
            )
            if (record.headers.isEmpty()) {
                details.addView(
                    context
                        .focusedLabel(
                            context.getString(R.string.wvi_app_load_no_headers),
                            muted = true,
                        ).apply {
                            padding(12)
                        },
                )
            } else {
                details.addView(
                    context.focusedButton(context.getString(R.string.wvi_app_load_copy_headers)) {
                        copy(record.id, AppLoadCopyPart.Headers)
                    },
                )
            }
            record.headers.forEachIndexed { index, header ->
                details.addView(
                    LinearLayout(context).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            context.focusedLabel(header.name, medium = true).apply {
                                setTextIsSelectable(true)
                            },
                            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
                        )
                        addView(
                            context.focusedIcon(
                                R.drawable.wvi_ic_copy,
                                context.getString(R.string.wvi_app_load_copy_value, header.name),
                            ) {
                                copy(record.id, AppLoadCopyPart.HeaderValue(index))
                            },
                        )
                    },
                )
                details.addView(rawText(header.value))
            }
        }
    }

    private fun preferences(): TargetSheetState? = snapshot?.let { presentation.target(it.key.targetId) }

    private fun rawText(value: String): TextView =
        context.focusedLabel(value, size = 13f).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            padding(12)
            background = context.shape(context.color(R.color.wvi_background), radius = 8)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = context.dp(8) }
        }

    private fun beforeDraw(action: () -> Unit) {
        clearPendingScroll()
        pendingScroll =
            view.doOnPreDraw {
                pendingScroll = null
                action()
            }
    }

    private fun clearPendingScroll() {
        pendingScroll?.removeListener()
        pendingScroll = null
    }

    private fun screenTop(view: View): Int = IntArray(2).also(view::getLocationOnScreen)[1]
}
