package com.feng.filesios27

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** Latar popup Liquid Glass iOS 27: kaca putih tembus pandang dengan bercak warna pastel yang bergerak pelan. */
class LiquidMenuDrawable(private val radius: Float, private val density: Float, private val dark: Boolean = false) : Drawable() {
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) 0xC4262629.toInt() else 0xCFF8F8FB.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = density * 1.2f; color = if (dark) 0x66FFFFFF else 0xE6FFFFFF.toInt() }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cols = intArrayOf(0xC9A6FF, 0x9BE8A0, 0xFFC78A, 0x9FD3FF, 0xFFE58A, 0xB0B8FF)
    var phase = 0f

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val rf = RectF(b)
        c.drawRoundRect(rf, radius, radius, base)
        c.save()
        c.clipPath(Path().apply { addRoundRect(rf, radius, radius, Path.Direction.CW) })
        for (i in cols.indices) {
            val a = phase + i * 1.05f
            val side = if (i % 2 == 0) 0.12f else 0.88f
            val cx = b.left + b.width() * side + sin(a) * b.width() * 0.07f
            val cy = b.top + b.height() * (0.08f + 0.84f * i / (cols.size - 1f)) + cos(a) * b.height() * 0.025f
            val r = b.width() * 0.34f
            blob.shader = RadialGradient(cx, cy, r, cols[i] or (if (dark) 0x55000000 else 0x73000000), cols[i], Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, blob)
        }
        shine.shader = android.graphics.LinearGradient(b.left.toFloat(), b.top.toFloat(), b.left + b.width() * 0.6f, b.top + b.height() * 0.5f,
            if (dark) 0x26FFFFFF else 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(rf, radius, radius, shine)
        c.restore()
        val h = edge.strokeWidth / 2f
        c.drawRoundRect(RectF(rf.left + h, rf.top + h, rf.right - h, rf.bottom - h), radius, radius, edge)
    }

    override fun getOutline(outline: Outline) { outline.setRoundRect(bounds, radius) }
    override fun setAlpha(alpha: Int) { base.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { base.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Emoji iOS atau ikon aplikasi yang dipasang di tengah gambar folder. Nilai: "e:<emoji>" atau "a:<package>". */
object FolderBadges {
    val map = HashMap<String, String>()
    private val icons = HashMap<String, Bitmap>()

    fun appIcon(pkg: String): Bitmap? {
        icons[pkg]?.let { return it }
        return try {
            val d = Thumbs.ctx.packageManager.getApplicationIcon(pkg)
            val s = 160
            val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            d.setBounds(0, 0, s, s)
            d.draw(cv)
            icons[pkg] = b
            b
        } catch (e: Throwable) { null }
    }
}

fun MainActivity.setFolderBadge(f: File, v: String?) {
    if (v == null) {
        FolderBadges.map.remove(f.path)
        prefs.edit().remove("fb:" + f.path).apply()
    } else {
        FolderBadges.map[f.path] = v
        prefs.edit().putString("fb:" + f.path, v).apply()
    }
    reloadAll()
}

private const val EMOJI_SET = "😀😃😄😁😆😅😂🤣😊😇🙂😉😍🥰😘😋😎🤩🥳🤔😴🤯😱🥶🔥⭐🌟✨💫❤️🧡💛💚💙💜🖤🤍💯✅❌⚡🌈☀️🌙☁️❄️🌸🌺🌻🌲🌴🍎🍊🍋🍉🍓🍔🍕🍩🎂☕🎵🎶🎮🎬📷🎨📚📖✏️📝📌📎💼🏠🚗✈️🚀⚽🏀🎯🏆🎁🔒🔑💡🔔📦📁💾💻📱⌚🎧🐶🐱🦊🐼🦁🐸🐵🦄🌍🎓💰🛒🧩🔧🧰"

private fun splitEmoji(s: String): List<String> {
    val out = ArrayList<String>()
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(s)
    var st = it.first()
    var en = it.next()
    while (en != java.text.BreakIterator.DONE) {
        out.add(s.substring(st, en))
        st = en
        en = it.next()
    }
    return out
}

/** Halaman pemilih ikon folder: tab Emoji (grid + ketik sendiri) dan tab Apps (ikon aplikasi terpasang). */
fun MainActivity.badgePage(f: File): View {
    val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(C.BG) }
    val head = FrameLayout(this).apply { setPadding(dpi(12), topInset + dpi(8), dpi(12), dpi(6)) }
    head.addView(circle(P.BACK, false) { popPage() }, flp(dpi(40), dpi(40), Gravity.START))
    head.addView(tv("Folder Icon", 17f, C.TEXT, true).apply { gravity = Gravity.CENTER }, flp(WRAP, dpi(40), Gravity.CENTER_HORIZONTAL))
    if (FolderBadges.map.containsKey(f.path)) {
        head.addView(tv("Remove", 17f, C.RED).apply {
            gravity = Gravity.CENTER
            setOnClickListener { setFolderBadge(f, null); popPage() }
        }, flp(WRAP, dpi(40), Gravity.END))
    }
    page.addView(head)

    val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dpi(16), dpi(4), dpi(16), dpi(8)) }
    val tEmoji = tv("Emoji", 16f, C.BLUE, true).apply { gravity = Gravity.CENTER; setPadding(dpi(20), dpi(8), dpi(20), dpi(8)) }
    val tApps = tv("Apps", 16f, C.GRAY, true).apply { gravity = Gravity.CENTER; setPadding(dpi(20), dpi(8), dpi(20), dpi(8)) }
    tabs.addView(tEmoji)
    tabs.addView(tApps)
    page.addView(tabs)

    val content = FrameLayout(this)
    page.addView(content, llp(MATCH, 0, 1f))

    // ---- Emoji ----
    val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipToPadding = false; setPadding(0, 0, 0, botInset + dpi(110)) }
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(12), dpi(4), dpi(12), 0) }
    sv.addView(col)
    val input = IosEditText(this).apply {
        hint = I18n.t("Type an emoji")
        textSize = 20f
        setSingleLine()
        setTextColor(C.TEXT)
        setHintTextColor(C.GRAY)
        background = GlassDrawable(dp(12f), 0xB3FFFFFF.toInt(), 0x80FFFFFF.toInt())
        setPadding(dpi(14), 0, dpi(14), 0)
    }
    val inRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    inRow.addView(input, llp(0, dpi(46), 1f))
    inRow.addView(tv("Done", 17f, C.BLUE, true).apply {
        gravity = Gravity.CENTER
        setPadding(dpi(14), 0, dpi(4), 0)
        setOnClickListener {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) {
                val first = splitEmoji(t).first()
                hideKb()
                setFolderBadge(f, "e:" + first)
                popPage()
            }
        }
    }, llp(WRAP, dpi(46)))
    col.addView(inRow, llp(MATCH, WRAP).apply { bottomMargin = dpi(10) })
    val list = splitEmoji(EMOJI_SET)
    val perRow = 7
    var i = 0
    while (i < list.size) {
        val r = LinearLayout(this)
        for (j in i until minOf(i + perRow, list.size)) {
            val e = list[j]
            r.addView(tv(e, 30f, C.TEXT, false, true).apply {
                gravity = Gravity.CENTER
                background = pressBg()
                setOnClickListener { setFolderBadge(f, "e:" + e); popPage() }
            }, llp(0, dpi(52), 1f))
        }
        for (k in 0 until perRow - (minOf(i + perRow, list.size) - i)) r.addView(View(this), llp(0, dpi(52), 1f))
        col.addView(r, llp(MATCH, WRAP))
        i += perRow
    }
    content.addView(sv, MATCH, MATCH)

    // ---- Apps ----
    class AppRow(val label: String, val pkg: String)
    val apps = ArrayList<AppRow>()
    val lv = ListView(this).apply {
        divider = null
        selector = android.graphics.drawable.ColorDrawable(0)
        clipToPadding = false
        setPadding(0, 0, 0, botInset + dpi(110))
        visibility = View.GONE
    }
    val adapter = object : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(p: Int): Any = apps[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
            val row = (cv as? LinearLayout) ?: LinearLayout(this@badgePage).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpi(16), dpi(6), dpi(16), dpi(6))
                background = pressBg()
                addView(ImageView(this@badgePage), llp(dpi(44), dpi(44)))
                addView(tv("", 17f, C.TEXT, false, true).apply { maxLines = 1 }, llp(0, WRAP, 1f).apply { marginStart = dpi(14) })
            }
            val a = apps[p]
            (row.getChildAt(0) as ImageView).setImageBitmap(FolderBadges.appIcon(a.pkg))
            (row.getChildAt(1) as android.widget.TextView).text = a.label
            return row
        }
    }
    lv.adapter = adapter
    lv.setOnItemClickListener { _, _, pos, _ -> setFolderBadge(f, "a:" + apps[pos].pkg); popPage() }
    content.addView(lv, MATCH, MATCH)
    var loaded = false
    fun showTab(emoji: Boolean) {
        sv.visibility = if (emoji) View.VISIBLE else View.GONE
        lv.visibility = if (emoji) View.GONE else View.VISIBLE
        tEmoji.setTextColor(if (emoji) C.BLUE else C.GRAY)
        tApps.setTextColor(if (emoji) C.GRAY else C.BLUE)
        if (!emoji && !loaded) {
            loaded = true
            Thread {
                val pm = packageManager
                val res = try {
                    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                        .map { AppRow(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                        .distinctBy { it.pkg }
                        .sortedBy { it.label.lowercase() }
                } catch (e: Exception) { emptyList() }
                ui.post { apps.clear(); apps.addAll(res); adapter.notifyDataSetChanged() }
            }.start()
        }
    }
    tEmoji.setOnClickListener { hideKb(); showTab(true) }
    tApps.setOnClickListener { hideKb(); showTab(false) }
    return page
}
