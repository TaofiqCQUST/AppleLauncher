package com.khan.applelauncher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class MainActivity : Activity() {

    private class AppItem(val label: String, val pkg: String, val intent: Intent, val icon: Drawable)

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var prefs: SharedPreferences
    private lateinit var greeting: TextView
    private lateinit var scroll: ScrollView
    private lateinit var gridBox: LinearLayout
    private lateinit var dock: LinearLayout
    private var apps: List<AppItem> = emptyList()
    private val gridTiles = HashMap<String, View>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun argb(v: Long) = v.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        refresh(null)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        scroll.scrollTo(0, 0)
    }

    // Home screen: back button does nothing
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {}

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(argb(0xFF0A1128), argb(0xFF2A1A56), argb(0xFF6A2C8C))
            )
            setPadding(dp(48), dp(28), dp(48), dp(20))
            clipChildren = false
            clipToPadding = false
        }

        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 54f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            setTextColor(argb(0xCCFFFFFF))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }
        greeting = TextView(this).apply {
            setTextColor(argb(0x99FFFFFF))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(2), 0, 0)
        }
        root.addView(clock)
        root.addView(date)
        root.addView(greeting)

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
        }
        gridBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(16))
            clipChildren = false
        }
        scroll.addView(gridBox, ViewGroup.LayoutParams(MP, WC))
        root.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(30).toFloat()
                setColor(argb(0x33FFFFFF))
            }
            clipChildren = false
        }
        val dockLp = LinearLayout.LayoutParams(WC, WC).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        }
        root.addView(dock, dockLp)

        setContentView(root)
    }

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppItem>()
        for (cat in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val ai = ri.activityInfo
                if (ai.packageName == packageName || !seen.add(ai.packageName)) continue
                val li = Intent(Intent.ACTION_MAIN)
                    .addCategory(cat)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                out.add(AppItem(ri.loadLabel(pm).toString(), ai.packageName, li, ri.loadIcon(pm)))
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    private fun currentPins(): List<String> {
        val saved = prefs.getString("pins", null)
        if (saved != null) return saved.split(",").filter { it.isNotEmpty() }
        val have = apps.map { it.pkg }
        val wanted = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.android.tv.settings"
        )
        val out = wanted.filter { it in have }.toMutableList()
        for (a in apps) {
            if (out.size >= 5) break
            if (a.pkg !in out) out.add(a.pkg)
        }
        return out
    }

    private fun togglePin(pkg: String) {
        val pins = currentPins().toMutableList()
        if (pins.remove(pkg)) {
            Toast.makeText(this, "Removed from dock", Toast.LENGTH_SHORT).show()
        } else {
            if (pins.size >= 7) {
                Toast.makeText(this, "Dock is full (max 7)", Toast.LENGTH_SHORT).show()
                return
            }
            pins.add(pkg)
            Toast.makeText(this, "Pinned to dock", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putString("pins", pins.joinToString(",")).apply()
        refresh(pkg)
    }

    private fun refresh(focusPkg: String?) {
        apps = loadApps()

        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greeting.text = when {
            h < 12 -> "Good morning"
            h < 17 -> "Good afternoon"
            else -> "Good evening"
        }

        dock.removeAllViews()
        for (p in currentPins()) {
            val a = apps.firstOrNull { it.pkg == p } ?: continue
            val lp = LinearLayout.LayoutParams(WC, WC).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            dock.addView(makeTile(a, dp(64), false), lp)
        }
        dock.visibility = if (dock.childCount == 0) View.GONE else View.VISIBLE

        gridBox.removeAllViews()
        gridTiles.clear()
        val tileW = dp(150)
        val cols = maxOf(3, (resources.displayMetrics.widthPixels - dp(120)) / tileW)
        var row: LinearLayout? = null
        for ((i, a) in apps.withIndex()) {
            if (i % cols == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                }
                gridBox.addView(row, LinearLayout.LayoutParams(WC, WC))
            }
            val t = makeTile(a, dp(84), true)
            gridTiles[a.pkg] = t
            row!!.addView(t, LinearLayout.LayoutParams(tileW, WC))
        }

        val target = (if (focusPkg != null) gridTiles[focusPkg] else null)
            ?: apps.firstOrNull()?.let { gridTiles[it.pkg] }
        target?.post { target.requestFocus() }
    }

    private fun tileBg(focused: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(22).toFloat()
        if (focused) {
            setColor(argb(0x33FFFFFF))
            setStroke(dp(2), argb(0x99FFFFFF))
        } else {
            setColor(Color.TRANSPARENT)
        }
    }

    private fun makeTile(a: AppItem, size: Int, showLabel: Boolean): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            isLongClickable = true
            setPadding(dp(8), dp(10), dp(8), dp(8))
            background = tileBg(false)
        }

        val img = ImageView(this).apply {
            setImageDrawable(a.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                cornerRadius = size * 0.22f
                setColor(argb(0x22FFFFFF))
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.22f)
                }
            }
            clipToOutline = true
        }
        tile.addView(img, LinearLayout.LayoutParams(size, size))

        if (showLabel) {
            val tv = TextView(this).apply {
                text = a.label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(8), 0, 0)
            }
            tile.addView(tv, LinearLayout.LayoutParams(MP, WC))
        }

        tile.setOnClickListener { launchApp(a) }
        tile.setOnLongClickListener {
            togglePin(a.pkg)
            true
        }
        tile.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.1f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(120).start()
            v.background = tileBg(focused)
        }
        return tile
    }

    private fun launchApp(a: AppItem) {
        try {
            startActivity(a.intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open ${a.label}", Toast.LENGTH_SHORT).show()
        }
    }
}
