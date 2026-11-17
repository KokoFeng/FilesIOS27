package com.feng.filesios27

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.os.Process
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream

val ICON_BLUE = 0xFF3D9AD8.toInt()

// ====================================================================================
// Tema (Automatic / Light / Dark)
// ====================================================================================
fun MainActivity.resolveDark(): Boolean = when (prefs.getInt("theme", 0)) {
    1 -> false
    2 -> true
    else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

fun MainActivity.applyThemeFlags() {
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    var f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    if (!C.dark) f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    window.decorView.systemUiVisibility = f
    window.setBackgroundDrawable(ColorDrawable(C.BG))
}

fun MainActivity.applyTheme(reopenSettings: Boolean) {
    C.dark = resolveDark()
    applyThemeFlags()
    root.setBackgroundColor(C.BG)
    if (!started || locked) return
    if (permView != null) { showPermission(); return }
    blurAnim?.cancel()
    blurOn = false
    curBlur = 0f
    buildMain()
    if (reopenSettings) ui.postDelayed({ pushPage(settingsPage()) }, 120)
}

fun MainActivity.setTheme(mode: Int, reopen: Boolean = false) {
    prefs.edit().putInt("theme", mode).apply()
    applyTheme(reopen)
}

fun MainActivity.themeName(): String = arrayOf("Automatic", "Light", "Dark")[prefs.getInt("theme", 0).coerceIn(0, 2)]

fun MainActivity.haptic() {
    if (prefs.getBoolean("haptics", true)) root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

// ====================================================================================
// Liquid Glass: gaya & transparansi
// ====================================================================================
fun MainActivity.applyGlassStyle() {
    val g = glassView ?: return
    val tinted = prefs.getInt("glass", 0) == 1
    g.params.tintColor = if (tinted) 0x40FFFFFF else 0x14FFFFFF
    g.params.refraction = if (tinted) 0.25f else 0.4f
    g.params.frost = if (tinted) 12f else 7f
    g.params.dispersion = if (tinted) 0.08f else 0.12f
    g.params.edge = if (tinted) 0.04f else 0.07f
    g.applyParams()
}

fun MainActivity.setGlass(mode: Int) {
    prefs.edit().putInt("glass", mode).apply()
    applyGlassStyle()
}

fun MainActivity.rebuildBar() {
    barHolder?.let { root.removeView(it) }
    glassView = null
    buildBar()
    updateBar()
    val h = barHolder ?: return
    val lp = h.layoutParams
    root.removeView(h)
    root.addView(h, root.indexOfChild(overlay), lp)
}

// ====================================================================================
// Browse: Favorites / Locations / Tags
// ====================================================================================
class Fav(val name: String, val dir: File, val icon: String, val custom: Boolean)

class RowSpec(val label: String, val icon: String?, val dot: Int, val trailing: String?, val onClick: () -> Unit)

fun favIcon(name: String, custom: Boolean): String {
    val n = name.lowercase()
    return when {
        custom -> P.SHAREDFOLDER
        n.contains("desktop") -> P.DESKTOP
        n.contains("document") -> P.DOCS
        n.contains("picture") || n.contains("photo") -> P.PHOTO
        n.contains("music") -> P.NOTE
        n.contains("movie") || n.contains("video") -> P.FILM
        n.contains("dcim") -> P.CAMERA
        else -> P.FOLDER
    }
}

fun MainActivity.allFavs(): List<Fav> {
    val defs = listOf(
        Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_DOCUMENTS, Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_MOVIES
    ).map { Environment.getExternalStoragePublicDirectory(it) }.filter { it.exists() }
        .map { Fav(it.name, it, favIcon(it.name, false), false) }
    val cust = prefs.getString("fav_custom", "")!!.split("\n").filter { it.isNotBlank() }.map { File(it) }
        .filter { it.isDirectory }.map { Fav(it.name, it, favIcon(it.name, true), true) }
    return defs + cust
}

fun MainActivity.hiddenFavs(): Set<String> = prefs.getString("fav_hidden", "")!!.split("\n").filter { it.isNotBlank() }.toSet()

fun MainActivity.setFavHidden(path: String, hidden: Boolean) {
    val s = hiddenFavs().toMutableSet()
    if (hidden) s.add(path) else s.remove(path)
    prefs.edit().putString("fav_hidden", s.joinToString("\n")).apply()
}

fun MainActivity.addCustomFav(f: File) {
    val l = prefs.getString("fav_custom", "")!!.split("\n").filter { it.isNotBlank() && it != f.path } + f.path
    prefs.edit().putString("fav_custom", l.joinToString("\n")).apply()
    setFavHidden(f.path, false)
}

fun MainActivity.removeCustomFav(path: String) {
    val l = prefs.getString("fav_custom", "")!!.split("\n").filter { it.isNotBlank() && it != path }
    prefs.edit().putString("fav_custom", l.joinToString("\n")).apply()
    setFavHidden(path, false)
}

fun MainActivity.refreshBrowse() {
    if (tab != 2 || stack.isEmpty()) return
    val pg = pages ?: return
    val old = stack[0]
    val nv = browsePage()
    nv.translationX = old.translationX
    val idx = pg.indexOfChild(old)
    pg.addView(nv, if (idx >= 0) idx else 0, FrameLayout.LayoutParams(MATCH, MATCH))
    pg.removeView(old)
    stack[0] = nv
}

fun MainActivity.sectionCard(col: LinearLayout, title: String, rows: List<RowSpec>) {
    if (rows.isEmpty()) return
    val head = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpi(4), dpi(18), dpi(4), dpi(8))
    }
    head.addView(tv(title, 21f, C.TEXT, true), llp(0, WRAP, 1f))
    val chev = icon(P.CHEV, C.BLUE, 18)
    chev.rotation = 90f
    head.addView(chev, llp(dpi(18), dpi(18)))
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rr(C.CARD, dp(12f))
        clipToOutline = true
    }
    rows.forEachIndexed { i, r ->
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(14), 0, dpi(14), 0)
            background = pressBg()
            setOnClickListener { r.onClick() }
        }
        if (r.icon != null) row.addView(icon(r.icon, ICON_BLUE, 24))
        else row.addView(View(this).apply { background = oval(r.dot) }, llp(dpi(18), dpi(18)).apply { marginStart = dpi(3); marginEnd = dpi(3) })
        row.addView(tv(r.label, 17f, C.TEXT).apply { setPadding(dpi(14), 0, 0, 0) }, llp(0, WRAP, 1f))
        if (r.trailing != null) row.addView(icon(r.trailing, ICON_BLUE, 20), llp(dpi(20), dpi(20)).apply { marginEnd = dpi(8) })
        row.addView(icon(P.CHEV, 0xFFC7C7CC.toInt(), 16))
        if (i > 0) card.addView(hairline(), llp(MATCH, 1).apply { marginStart = dpi(52) })
        card.addView(row, llp(MATCH, dpi(52)))
    }
    head.setOnClickListener {
        val open = card.visibility == View.VISIBLE
        card.visibility = if (open) View.GONE else View.VISIBLE
        chev.animate().rotation(if (open) 0f else 90f).setDuration(220).setInterpolator(IOS_EASE).start()
    }
    col.addView(head)
    col.addView(card)
}

fun MainActivity.browsePage(): View {
    val act = this
    val sv = ScrollView(this).apply { setBackgroundColor(C.BG); isVerticalScrollBarEnabled = false }
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(16), topInset + dpi(8), dpi(16), botInset + dpi(110))
    }
    sv.addView(col)
    val top = FrameLayout(this)
    top.addView(circle(P.MORE, false) { v -> browseMenu(v) }, flp(dpi(40), dpi(40), Gravity.END or Gravity.CENTER_VERTICAL))
    col.addView(top, llp(MATCH, dpi(48)))
    col.addView(tv("Browse", 34f, C.TEXT, true).apply { setPadding(dpi(4), dpi(4), 0, dpi(8)) })

    val internal = Environment.getExternalStorageDirectory()
    val sb = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = GlassDrawable(dp(19f), 0xB3FFFFFF.toInt(), 0x80FFFFFF.toInt())
        elevation = dp(3f)
        setPadding(dpi(8), 0, dpi(8), 0)
        setOnClickListener { act.pushPage(act.ListPage(internal, 0, "On My iPhone", true, true).view) }
    }
    sb.addView(icon(P.SEARCH, C.GRAY, 18))
    sb.addView(tv("Search", 17f, C.GRAY).apply { setPadding(dpi(8), 0, 0, 0) }, llp(0, WRAP, 1f))
    sb.addView(icon(P.MIC, C.GRAY, 18))
    col.addView(sb, llp(MATCH, dpi(38)).apply { bottomMargin = dpi(8) })

    val hidden = hiddenFavs()
    sectionCard(col, "Favorites", allFavs().filter { !hidden.contains(it.dir.path) }.map { f ->
        RowSpec(f.name, f.icon, 0, null) { act.pushPage(act.ListPage(f.dir, 0, f.name, true).view) }
    })

    val locs = ArrayList<RowSpec>()
    locs.add(RowSpec("iCloud Drive", P.CLOUD, 0, P.SYNC) { act.pushPage(act.ListPage(icloudDir, 0, "iCloud Drive", true).view) })
    locs.add(RowSpec("On My iPhone", P.PHONE, 0, null) { act.pushPage(act.ListPage(internal, 0, "On My iPhone", true).view) })
    try {
        val dirs = getExternalFilesDirs(null)
        for (i in 1 until dirs.size) {
            val d = dirs[i] ?: continue
            val f = File(d.absolutePath.substringBefore("/Android"))
            locs.add(RowSpec("SD Card", P.FOLDER, 0, null) { act.pushPage(act.ListPage(f, 0, "SD Card", true).view) })
        }
    } catch (e: Exception) {}
    locs.add(RowSpec("Recently Deleted", P.TRASH, 0, null) { act.pushPage(act.ListPage(trashDir, 3, "Recently Deleted", true).view) })
    sectionCard(col, "Locations", locs)

    sectionCard(col, "Tags", C.tagColors.indices.map { i ->
        RowSpec(C.tagNames[i], null, C.tagColors[i], null) { act.pushPage(act.ListPage(null, 4, C.tagNames[i], true, false, C.tagColors[i]).view) }
    })
    return sv
}

fun MainActivity.browseMenu(v: View) {
    val loc = IntArray(2)
    v.getLocationOnScreen(loc)
    val mode = prefs.getInt("theme", 0)
    val items = listOf(
        Mi("Edit Favorites", P.PENCIL) { editFavorites() },
        Mi(sep = true),
        Mi("Appearance", header = true),
        Mi("Automatic", check = mode == 0) { setTheme(0) },
        Mi("Light", check = mode == 1) { setTheme(1) },
        Mi("Dark", check = mode == 2) { setTheme(2) },
        Mi(sep = true),
        Mi("Settings", P.GEAR) { pushPage(settingsPage()) }
    )
    showMenu((loc[0] + v.width - dpi(250)).toFloat(), (loc[1] + v.height + dpi(6)).toFloat(), null, items)
}

// ====================================================================================
// Sheet bawah umum, edit Favorites, pemilih folder
// ====================================================================================
fun MainActivity.bottomSheet(title: String, frac: Float, onClose: () -> Unit, build: (LinearLayout, () -> Unit) -> Unit) {
    val o = overlay ?: return
    hideKb()
    val sh = (root.height * frac).toInt()
    val dim = View(this).apply { setBackgroundColor(0x66000000); alpha = 0f; isClickable = true }
    val sheet = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(C.BG)
            cornerRadii = floatArrayOf(dp(14f), dp(14f), dp(14f), dp(14f), 0f, 0f, 0f, 0f)
        }
        clipToOutline = true
        elevation = dp(20f)
    }
    var gone = false
    fun close() {
        if (gone) return
        gone = true
        dim.animate().alpha(0f).setDuration(250).start()
        sheet.animate().translationY(sh.toFloat()).setDuration(300).setInterpolator(IOS_EASE).withEndAction {
            o.removeView(dim); o.removeView(sheet); onClose()
        }.start()
    }
    dim.tag = Runnable { close() }
    dim.setOnClickListener { close() }
    val head = FrameLayout(this).apply { setBackgroundColor(C.HEAD) }
    head.addView(tv(title, 17f, C.TEXT, true), flp(WRAP, WRAP, Gravity.CENTER))
    head.addView(tv("Done", 17f, C.BLUE, true).apply {
        setPadding(dpi(16), dpi(14), dpi(16), dpi(14))
        setOnClickListener { close() }
    }, flp(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL))
    sheet.addView(head, llp(MATCH, dpi(52)))
    sheet.addView(hairline(), llp(MATCH, 1))
    val sv = ScrollView(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(16), dpi(16), dpi(16), botInset + dpi(24))
    }
    sv.addView(col)
    sheet.addView(sv, llp(MATCH, 0, 1f))
    build(col) { close() }
    o.addView(dim, MATCH, MATCH)
    o.addView(sheet, flp(MATCH, sh, Gravity.BOTTOM))
    sheet.translationY = sh.toFloat()
    dim.animate().alpha(1f).setDuration(250).start()
    sheet.animate().translationY(0f).setDuration(420).setInterpolator(IOS_EASE).start()
}

fun MainActivity.editFavorites() {
    bottomSheet("Favorites", 0.8f, { refreshBrowse() }) { col, _ ->
        fun fill() {
            col.removeAllViews()
            col.addView(tv("Choose which folders appear under Favorites on the Browse screen.", 13f, C.GRAY).apply {
                setPadding(dpi(4), 0, dpi(4), dpi(10))
            })
            val hidden = hiddenFavs()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rr(C.CARD, dp(12f))
                clipToOutline = true
            }
            allFavs().forEachIndexed { i, f ->
                if (i > 0) card.addView(hairline(), llp(MATCH, 1).apply { marginStart = dpi(52) })
                val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(14), 0, dpi(14), 0) }
                r.addView(icon(f.icon, ICON_BLUE, 24))
                r.addView(tv(f.name, 17f, C.TEXT).apply { setPadding(dpi(14), 0, 0, 0); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, llp(0, WRAP, 1f))
                if (f.custom) r.addView(tv("Remove", 15f, C.RED).apply {
                    setPadding(dpi(8), dpi(8), dpi(12), dpi(8))
                    setOnClickListener { removeCustomFav(f.dir.path); fill() }
                })
                val sw = IosSwitch(this)
                sw.put(!hidden.contains(f.dir.path), false)
                sw.onToggle = { on -> setFavHidden(f.dir.path, !on) }
                r.addView(sw)
                card.addView(r, llp(MATCH, dpi(52)))
            }
            col.addView(card)
            col.addView(tv("Add Folder…", 17f, C.BLUE, true).apply {
                background = rr(C.CARD, dp(12f))
                setPadding(dpi(16), dpi(14), dpi(16), dpi(14))
                setOnClickListener { pickFolder(Environment.getExternalStorageDirectory()) { f -> addCustomFav(f); fill() } }
            }, llp(MATCH, WRAP).apply { topMargin = dpi(20) })
        }
        fill()
    }
}

fun MainActivity.pickFolder(start: File, onPick: (File) -> Unit) {
    val act = this
    var cur = start
    val root0 = Environment.getExternalStorageDirectory()
    val addBtn = tv("Add", 17f, C.BLUE, true).apply { gravity = Gravity.CENTER }
    showViewer("Choose Folder", false, addBtn) { v ->
        val entries = ArrayList<File?>()
        val pathTv = tv("", 13f, C.GRAY).apply {
            setPadding(dpi(16), 0, dpi(16), dpi(8))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        val lv = ListView(this).apply {
            divider = null
            clipToPadding = false
            setPadding(0, topInset + dpi(64), 0, botInset + dpi(16))
        }
        lv.addHeaderView(pathTv, null, false)
        val adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(p: Int): Any = entries[p] ?: ""
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val e = entries[p]
                val row = LinearLayout(act).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpi(16), dpi(12), dpi(16), dpi(12))
                    addView(icon(if (e == null) P.BACK else P.FOLDER, ICON_BLUE, 22))
                    addView(tv(if (e == null) "Up one level" else e.name, 17f, C.TEXT).apply { setPadding(dpi(14), 0, 0, 0) })
                }
                return row
            }
        }
        fun load() {
            entries.clear()
            if (cur.path != root0.path && cur.parentFile != null) entries.add(null)
            entries.addAll(FileOps.list(cur).filter { it.isDirectory }.sortedBy { it.name.lowercase() })
            pathTv.text = cur.path
            adapter.notifyDataSetChanged()
        }
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            val i = pos - lv.headerViewsCount
            if (i >= 0 && i < entries.size) {
                val e = entries[i]
                cur = e ?: (cur.parentFile ?: cur)
                load()
            }
        }
        addBtn.setOnClickListener { onPick(cur); closeViewer() }
        v.addView(lv, MATCH, MATCH)
        load()
    }
}

// ====================================================================================
// Settings (gaya iOS)
// ====================================================================================
fun MainActivity.settingsRowBase(): LinearLayout = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dpi(16), dpi(8), dpi(14), dpi(8))
    minimumHeight = dpi(50)
}

fun MainActivity.valueRow(label: String, value: String, onClick: () -> Unit): View {
    val r = settingsRowBase()
    r.background = pressBg()
    r.setOnClickListener { onClick() }
    r.addView(tv(label, 17f, C.TEXT), llp(0, WRAP, 1f))
    r.addView(tv(value, 17f, C.GRAY))
    r.addView(icon(P.CHEV, C.GRAY, 14), llp(dpi(14), dpi(14)).apply { marginStart = dpi(6) })
    return r
}

fun MainActivity.navRow(label: String, color: Int, onClick: () -> Unit): View {
    val r = settingsRowBase()
    r.background = pressBg()
    r.setOnClickListener { onClick() }
    r.addView(tv(label, 17f, color), llp(0, WRAP, 1f))
    r.addView(icon(P.CHEV, C.GRAY, 14), llp(dpi(14), dpi(14)))
    return r
}

fun MainActivity.switchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit): View {
    val r = settingsRowBase()
    r.addView(tv(label, 17f, C.TEXT), llp(0, WRAP, 1f))
    val sw = IosSwitch(this)
    sw.put(checked, false)
    sw.onToggle = onToggle
    r.addView(sw)
    return r
}

fun MainActivity.infoRow(label: String, value: String): View {
    val r = settingsRowBase()
    r.addView(tv(label, 17f, C.TEXT), llp(0, WRAP, 1f))
    r.addView(tv(value, 15f, C.GRAY).apply { gravity = Gravity.END }, llp(0, WRAP, 1.3f))
    return r
}

fun MainActivity.settingsGroup(col: LinearLayout, header: String?, footer: String?, rows: List<View>) {
    if (header != null) col.addView(tv(header.uppercase(), 13f, C.GRAY).apply { setPadding(dpi(16), dpi(22), 0, dpi(6)) })
    else col.addView(View(this), llp(MATCH, dpi(22)))
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rr(C.CARD, dp(12f))
        clipToOutline = true
    }
    rows.forEachIndexed { i, r ->
        if (i > 0) card.addView(hairline(), llp(MATCH, 1).apply { marginStart = dpi(16) })
        card.addView(r, llp(MATCH, WRAP))
    }
    col.addView(card)
    if (footer != null) col.addView(tv(footer, 13f, C.GRAY).apply { setPadding(dpi(16), dpi(6), dpi(16), 0) })
}

fun MainActivity.settingsPage(): View {
    val page = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(C.BG)
    }
    val head = FrameLayout(this).apply { setPadding(dpi(12), topInset + dpi(8), dpi(12), dpi(6)) }
    head.addView(circle(P.BACK, false) { popPage() }, flp(dpi(40), dpi(40), Gravity.START))
    head.addView(tv("Settings", 17f, C.TEXT, true).apply { gravity = Gravity.CENTER }, flp(WRAP, dpi(40), Gravity.CENTER_HORIZONTAL))
    page.addView(head)
    val sv = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        clipToPadding = false
        setPadding(0, 0, 0, botInset + dpi(110))
    }
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(16), 0, dpi(16), 0)
    }
    sv.addView(col)
    page.addView(sv, llp(MATCH, 0, 1f))

    fun refreshPages() { stack.forEach { (it.tag as? MainActivity.ListPage)?.refresh() } }

    fun fill() {
        col.removeAllViews()
        settingsGroup(col, "Appearance", "Reduce Transparency turns off the glass blur and uses solid surfaces.", listOf(
            valueRow("Appearance", themeName()) {
                actionSheet("Appearance", null, listOf(
                    Btn("Automatic") { setTheme(0, true) }, Btn("Light") { setTheme(1, true) }, Btn("Dark") { setTheme(2, true) }
                ))
            },
            valueRow("Liquid Glass", if (prefs.getInt("glass", 0) == 1) "Tinted" else "Clear") {
                actionSheet("Liquid Glass", "Choose how clear the glass looks.", listOf(
                    Btn("Clear") { setGlass(0); fill() }, Btn("Tinted") { setGlass(1); fill() }
                ))
            },
            valueRow("Text Size", arrayOf("Default", "Medium", "Large")[prefs.getInt("textscale", 0).coerceIn(0, 2)]) {
                actionSheet("Text Size", null, listOf("Default", "Medium", "Large").mapIndexed { i, n ->
                    Btn(n) {
                        prefs.edit().putInt("textscale", i).apply()
                        C.textScale = floatArrayOf(1f, 1.1f, 1.25f)[i]
                        applyTheme(true)
                    }
                })
            },
            switchRow("Reduce Transparency", prefs.getBoolean("reduce", false)) { on ->
                prefs.edit().putBoolean("reduce", on).apply()
                rebuildBar()
            }
        ))
        settingsGroup(col, "Browsing", null, listOf(
            valueRow("Default View", if (gridMode) "Icons" else "List") {
                actionSheet("Default View", null, listOf(
                    Btn("Icons") { gridMode = true; prefs.edit().putBoolean("grid", true).apply(); refreshPages(); fill() },
                    Btn("List") { gridMode = false; prefs.edit().putBoolean("grid", false).apply(); refreshPages(); fill() }
                ))
            },
            valueRow("Sort By", arrayOf("Name", "Kind", "Date", "Size")[sortMode.coerceIn(0, 3)]) {
                val names = arrayOf("Name", "Kind", "Date", "Size")
                actionSheet("Sort By", null, names.mapIndexed { i, n ->
                    Btn(n) { sortMode = i; prefs.edit().putInt("sort", i).apply(); stack.forEach { (it.tag as? MainActivity.ListPage)?.applyQuery() }; fill() }
                })
            },
            navRow("Edit Favorites", C.TEXT) { editFavorites() },
            switchRow("Haptic Feedback", prefs.getBoolean("haptics", true)) { on -> prefs.edit().putBoolean("haptics", on).apply() }
        ))
        settingsGroup(col, "General", null, listOf(
            navRow("Run Setup Again", C.TEXT) { prefs.edit().putBoolean("setup_done", false).apply(); showSetup(0) },
            navRow("Restore Defaults", C.RED) {
                alert("Restore Defaults?", "Appearance, glass, favorites and view options will return to their original values.", null, 0, listOf(
                    Btn("Cancel"),
                    Btn("Restore", 2) {
                        prefs.edit().remove("theme").remove("glass").remove("reduce").remove("grid").remove("sort")
                            .remove("haptics").remove("fav_hidden").remove("fav_custom").remove("textscale").apply()
                        C.textScale = 1f
                        gridMode = true
                        sortMode = 0
                        applyTheme(true)
                    }
                ))
            }
        ))
        val ver = try {
            @Suppress("DEPRECATION")
            val pi = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            "${pi.versionName} ($code)"
        } catch (e: Exception) { "1.0" }
        settingsGroup(col, "About", "Ikon bawaan dan ikon aplikasi dikunci dan tidak dapat diubah.", listOf(
            infoRow("Aplikasi", "Files iOS 27"),
            infoRow("Apps ini dibuat oleh", "Feng-Dev / Feng iOS on Droid"),
            infoRow("Versi aplikasi", ver)
        ))
    }
    fill()
    return page
}

// ====================================================================================
// Anti-modifikasi: app "memberontak" kalau terdeteksi dimodifikasi
// ====================================================================================
fun MainActivity.exitApp() {
    finishAffinity()
    Process.killProcess(Process.myPid())
}

fun MainActivity.tamperLock(reason: String) {
    if (locked) return
    locked = true
    ui.removeCallbacksAndMessages(null)
    root.removeAllViews()
    root.setBackgroundColor(Color.BLACK)
    val o = FrameLayout(this)
    overlay = o
    pages = null
    barView = null
    barHolder = null
    glassView = null
    root.addView(o, MATCH, MATCH)
    ObjectAnimator.ofFloat(root, "translationX", 0f, 26f, -26f, 20f, -20f, 12f, -12f, 0f).apply {
        duration = 700
        repeatCount = 2
        start()
    }
    ValueAnimator.ofObject(ArgbEvaluator(), Color.BLACK, 0xFF8B0000.toInt(), Color.BLACK).apply {
        duration = 500
        repeatCount = 3
        addUpdateListener { root.setBackgroundColor(it.animatedValue as Int) }
        start()
    }
    ui.postDelayed({
        root.setBackgroundColor(Color.BLACK)
        root.translationX = 0f
        alert(
            "Apps Ini Telah Dimodifikasi",
            "Apps ini telah dimodifikasi.\n\nKhusus untuk para modder / pemodifikasi: apps ini akan memberontak dan tidak bisa digunakan.\n\nSilakan pasang versi asli dari Feng-Dev / Feng iOS on Droid.",
            null, 0, listOf(Btn("Keluar", 1) { exitApp() })
        )
    }, 1500)
}

// ====================================================================================
// Mode Select + buat ZIP dari banyak file
// ====================================================================================
fun MainActivity.beginSelect(pg: MainActivity.ListPage) {
    endSelect()
    selPage = pg
    pg.selecting = true
    pg.selected.clear()
    barHolder?.visibility = View.GONE
    showSelBar(pg)
    pg.updateSelUi()
    pg.refresh()
}

fun MainActivity.endSelect() {
    val pg = selPage ?: return
    selPage = null
    pg.selecting = false
    pg.selected.clear()
    pg.onSelChanged = null
    pg.updateSelUi()
    pg.refresh()
    selBarView?.let { root.removeView(it) }
    selBarView = null
    barHolder?.visibility = View.VISIBLE
}

fun MainActivity.showSelBar(pg: MainActivity.ListPage) {
    val corner = dp(30f)
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dpi(6), dpi(6), dpi(6), dpi(6))
        background = GlassDrawable(corner, 0xF2FFFFFF.toInt(), 0xE6FFFFFF.toInt())
        elevation = dp(10f)
    }
    val cells = ArrayList<View>()
    fun addAct(label: String, path: String, col: Int, act: () -> Unit) {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpi(10), dpi(8), dpi(10), dpi(6))
        }
        cell.addView(icon(path, col, 24))
        cell.addView(tv(label, 10f, col, true).apply { gravity = Gravity.CENTER; setSingleLine() })
        cell.setOnClickListener { if (pg.selected.isNotEmpty()) act() }
        pressFx(cell)
        bar.addView(cell, llp(0, WRAP, 1f))
        cells.add(cell)
    }
    addAct("Compress", P.ARCHIVE, C.TEXT) {
        val l = pg.selected.toList()
        val target = pg.dir ?: l[0].parentFile!!
        endSelect()
        bg("Compressed") { FileOps.zip(l, target) }
    }
    addAct("Copy", P.COPY, C.TEXT) {
        val l = pg.selected.toList()
        clip = Pair(l, false)
        endSelect()
        toast("Copied ${l.size} items")
    }
    addAct("Move", P.FOLDER, C.TEXT) {
        val l = pg.selected.toList()
        clip = Pair(l, true)
        endSelect()
        toast("Open a folder, tap ••• then Paste")
    }
    addAct("Delete", P.TRASH, C.RED) {
        val l = pg.selected.toList()
        endSelect()
        bg("Moved to Recently Deleted") { l.forEach { trashIt(it) } }
    }
    pg.onSelChanged = { cells.forEach { it.alpha = if (pg.selected.isEmpty()) 0.4f else 1f } }
    pg.onSelChanged?.invoke()
    val sw = resources.displayMetrics.widthPixels
    val idx = root.indexOfChild(overlay)
    root.addView(bar, idx, flp(minOf(sw - dpi(32), dpi(360)), WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
        bottomMargin = botInset + dpi(10)
    })
    selBarView = bar
}

// ====================================================================================
// Download dari URL (diperbaiki): cek link dulu, nama dari server, progres, batal, error jelas
// ====================================================================================
fun MainActivity.guessName(url: String): String {
    val p = try { android.net.Uri.parse(url).lastPathSegment } catch (e: Exception) { null }
    val n = (p ?: "").substringBefore('?').trim()
    return if (n.isBlank()) "download" else android.net.Uri.decode(n)
}

fun MainActivity.finalName(typed: String, orig: String): String {
    val n = typed.trim().replace("/", "_")
    val oe = orig.substringAfterLast('.', "")
    return if (!n.contains('.') && oe.isNotEmpty() && oe.length <= 5) "$n.$oe" else n
}

fun MainActivity.normalizeUrl(s: String): String? {
    var u = s.trim().replace("\n", "").replace("\r", "").replace(" ", "")
    if (u.isEmpty() || u.equals("https://", true) || u.equals("http://", true)) return null
    if (!u.contains("://")) u = "https://$u"
    val low = u.lowercase()
    return if (low.startsWith("http://") || low.startsWith("https://")) u else null
}

fun MainActivity.askUrl(dir: File) {
    var initial = "https://"
    try {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cb.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        if (t != null && (t.startsWith("http://") || t.startsWith("https://")) && !t.contains(" ")) initial = t
    } catch (e: Exception) {}
    alert("Download from URL", "Paste the link of the file.", initial, initial.length, listOf(Btn("Cancel"), Btn("Next", 1) { n ->
        val u = normalizeUrl(n)
        if (u == null) { toast("Invalid link"); askUrl(dir) } else probeUrl(u, dir)
    }))
}

fun MainActivity.askDownload(url: String, dir: File) {
    val u = normalizeUrl(url)
    if (u == null) toast("Invalid link") else probeUrl(u, dir)
}

fun MainActivity.nameFromHeaders(url: String, cd: String?, ct: String?): String {
    var n: String? = null
    if (cd != null) {
        val star = Regex("filename\\*\\s*=\\s*(?:UTF-8|utf-8)''([^;]+)").find(cd)
        if (star != null) n = android.net.Uri.decode(star.groupValues[1].trim().trim('"'))
        if (n == null) {
            val plain = Regex("filename\\s*=\\s*\"?([^\";]+)\"?").find(cd)
            if (plain != null) n = plain.groupValues[1].trim()
        }
    }
    var name = (n ?: guessName(url)).replace("/", "_").trim()
    if (name.isBlank()) name = "download"
    if (!name.contains('.') && ct != null) {
        val mt = ct.substringBefore(';').trim().lowercase()
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mt)
        if (!ext.isNullOrEmpty()) name = "$name.$ext"
    }
    return name
}

fun MainActivity.probeUrl(u: String, dir: File) {
    val pill = progressPill("Checking link…")
    Thread {
        try {
            val c = openConn(u)
            val cd = c.getHeaderField("Content-Disposition")
            val ct = c.contentType
            val len = c.contentLengthLong
            c.disconnect()
            val name = nameFromHeaders(u, cd, ct)
            ui.post { removePill(pill); afterProbe(u, dir, name, ct, len) }
        } catch (e: Throwable) {
            ui.post { removePill(pill); downloadError(e) }
        }
    }.start()
}

fun MainActivity.afterProbe(u: String, dir: File, name: String, ct: String?, len: Long) {
    val urlName = guessName(u).lowercase()
    val isHtml = ct?.lowercase()?.startsWith("text/html") == true && !urlName.endsWith(".html") && !urlName.endsWith(".htm")
    if (isHtml) {
        alert("This Is a Web Page", "This link opens a web page, not a file. Download it anyway?", null, 0, listOf(
            Btn("Cancel"),
            Btn("Download", 1) { askDownloadNamed(u, dir, name, len) }
        ))
    } else askDownloadNamed(u, dir, name, len)
}

fun MainActivity.askDownloadNamed(u: String, dir: File, orig: String, len: Long) {
    val sel = if (orig.contains('.')) orig.lastIndexOf('.') else orig.length
    val msg = "Give this file a name before it downloads." + (if (len > 0) "\n" + FileOps.size(len) else "")
    alert("Download", msg, orig, sel, listOf(Btn("Cancel"), Btn("Done", 1) { n ->
        if (n.isBlank()) { toast("A name is required"); askDownloadNamed(u, dir, orig, len) }
        else startDownload(u, dir, finalName(n, orig), len)
    }))
}

fun MainActivity.startDownload(url: String, dir: File, name: String, knownLen: Long = -1L) {
    val pill = progressPill("Starting…")
    val cancel = BooleanArray(1)
    pill.setOnClickListener { cancel[0] = true }
    Thread {
        val tmp = File(dir, ".$name.part")
        try {
            dir.mkdirs()
            val dst = FileOps.unique(dir, name)
            val c = openConn(url)
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else knownLen
            var got = 0L
            var last = 0L
            c.inputStream.use { ins ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(65536)
                    while (true) {
                        if (cancel[0]) throw java.io.InterruptedIOException("Canceled")
                        val r = ins.read(buf)
                        if (r < 0) break
                        out.write(buf, 0, r)
                        got += r
                        val now = System.currentTimeMillis()
                        if (now - last > 250) {
                            last = now
                            val txt = (if (total > 0) "Downloading ${got * 100 / total}%" else "Downloading ${FileOps.size(got)}") + " · tap to cancel"
                            ui.post { pill.text = txt }
                        }
                    }
                }
            }
            if (!tmp.renameTo(dst)) { tmp.copyTo(dst, true); tmp.delete() }
            ui.post { removePill(pill); finishSave(dst) }
        } catch (e: Throwable) {
            tmp.delete()
            ui.post {
                removePill(pill)
                if (cancel[0]) toast("Download canceled") else downloadError(e)
            }
        }
    }.start()
}

fun MainActivity.downloadError(e: Throwable) {
    val msg = when (e) {
        is java.net.UnknownHostException -> "The address can't be found, or there is no internet connection."
        is javax.net.ssl.SSLException -> "A secure connection could not be made."
        is java.net.SocketTimeoutException -> "The server took too long to respond."
        is java.net.ConnectException -> "Could not connect to the server."
        else -> e.message ?: "Something went wrong."
    }
    alert("Unable To Download", msg, null, 0, listOf(Btn("OK", 1)))
}

fun MainActivity.finishSave(dst: File) {
    addRecent(dst)
    reloadAll()
    if (FileOps.ext(dst) in INSTALLABLE) chooseAction(dst) else toast("Downloaded")
}
