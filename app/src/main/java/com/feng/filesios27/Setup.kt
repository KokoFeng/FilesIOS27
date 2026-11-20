package com.feng.filesios27

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Locale

// ====================================================================================
// Setup awal ala iOS: Bahasa -> Negara/Wilayah -> Tampilan -> Quick Start (izin akses)
// Aset ilustrasi diambil dari paket iSetup iOS18 yang diberikan user.
// ====================================================================================
class Lang(val code: String, val name: String)

val LANGS = listOf(
    Lang("en", "English"), Lang("es", "Español"), Lang("zhs", "简体中文"), Lang("zht", "繁體中文"),
    Lang("ja", "日本語"), Lang("fr", "Français"), Lang("de", "Deutsch"), Lang("it", "Italiano"),
    Lang("pt", "Português"), Lang("ru", "Русский"), Lang("uk", "Українська"), Lang("pl", "Polski"),
    Lang("id", "Bahasa Indonesia"), Lang("ko", "한국어"), Lang("tr", "Türkçe"), Lang("th", "ไทย"),
    Lang("vi", "Tiếng Việt"), Lang("hi", "हिन्दी"), Lang("ar", "العربية"), Lang("he", "עברית"),
    Lang("ms", "Bahasa Melayu"), Lang("ro", "Română"), Lang("el", "Ελληνικά"), Lang("hr", "Hrvatski"),
    Lang("bg", "Български"), Lang("sv", "Svenska"), Lang("da", "Dansk"), Lang("hu", "Magyar"),
    Lang("cs", "Čeština"), Lang("no", "Norsk"), Lang("kk", "Қазақша")
)

val COUNTRIES = listOf(
    "US", "CA", "GB", "AU", "DE", "FR", "IT", "ES", "JP", "KR", "CN", "ID", "IN", "BR", "MX",
    "RU", "TR", "SA", "AE", "SG", "MY", "TH", "VN", "PH", "NL", "SE", "PL", "UA", "NG", "ZA"
)

fun countryName(cc: String): String = Locale("", cc).getDisplayCountry(Locale.getDefault())

/** Teks setup dalam English atau Bahasa Indonesia (bahasa lain memakai English). */
fun MainActivity.tr(en: String, id: String): String = if (I18n.lang == "id") id else I18n.t(en)

fun MainActivity.applyLocalePrefs() {
    val saved = prefs.getString("lang", null)
    I18n.lang = I18n.resolve(saved)
    if (saved == null) {
        // Otomatis: ikuti bahasa bawaan HP
        try {
            Locale.setDefault(I18n.sysLocale)
            df = SimpleDateFormat("M/d/yy", I18n.sysLocale)
        } catch (e: Exception) {}
        return
    }
    val lang = saved
    val tag = when (lang) { "zhs" -> "zh-CN"; "zht" -> "zh-TW"; "no" -> "nb"; else -> lang }
    val cc = prefs.getString("region", "") ?: ""
    try {
        val loc = if (cc.isNotEmpty()) Locale.Builder().setLanguageTag(tag).setRegion(cc).build() else Locale.forLanguageTag(tag)
        Locale.setDefault(loc)
        df = SimpleDateFormat("M/d/yy", loc)
    } catch (e: Exception) {}
}

fun MainActivity.setupBackBar(onBack: () -> Unit): View = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dpi(8), topInset + dpi(14), dpi(16), dpi(10))
    addView(icon(P.BACK, C.BLUE, 22))
    addView(tv(tr("Back", "Kembali"), 17f, C.BLUE))
    setOnClickListener { onBack() }
}

fun MainActivity.setupFrame(content: View, back: (() -> Unit)?, bottom: View? = null): View {
    val f = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(C.BG)
        isClickable = true
    }
    if (back != null) f.addView(setupBackBar(back)) else f.addView(View(this), llp(MATCH, topInset + dpi(8)))
    f.addView(content, llp(MATCH, 0, 1f))
    if (bottom != null) f.addView(bottom)
    return f
}

fun MainActivity.setupCard(rows: List<Pair<String, () -> Unit>>): View {
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rr(C.SETCARD, dp(14f))
        clipToOutline = true
    }
    rows.forEachIndexed { i, r ->
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(16), 0, dpi(16), 0)
            background = pressBg()
            setOnClickListener { r.second() }
        }
        row.addView(tv(r.first, 17f, C.TEXT), llp(0, WRAP, 1f))
        row.addView(icon(P.CHEV, 0xFFC7C7CC.toInt(), 14), llp(dpi(14), dpi(14)))
        if (i > 0) card.addView(hairline(), llp(MATCH, 1).apply { marginStart = dpi(16) })
        card.addView(row, llp(MATCH, dpi(50)))
    }
    return card
}

fun MainActivity.setupLanguage(): View {
    val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dpi(16), dpi(64), dpi(16), botInset + dpi(24))
    }
    col.addView(icon(P.GLOBE, C.BLUE, 64, true, 2.2f))
    col.addView(View(this), llp(MATCH, dpi(56)))
    col.addView(setupCard(LANGS.map { l ->
        l.name to {
            prefs.edit().putString("lang", l.code).apply()
            applyLocalePrefs()
            applyDirection()
            showSetup(1)
        }
    }), llp(MATCH, WRAP))
    sv.addView(col)
    return setupFrame(sv, null)
}

fun MainActivity.setupRegion(): View {
    val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dpi(16), dpi(40), dpi(16), botInset + dpi(24))
    }
    col.addView(icon(P.GLOBE, C.BLUE, 64, true, 2.2f))
    col.addView(View(this), llp(MATCH, dpi(40)))
    val cur = prefs.getString("region", null) ?: Locale.getDefault().country.ifEmpty { "US" }
    fun choose(cc: String) {
        prefs.edit().putString("region", cc).apply()
        applyLocalePrefs()
        showSetup(2)
    }
    col.addView(setupCard(listOf(countryName(cur) to { choose(cur) })), llp(MATCH, WRAP))
    col.addView(View(this), llp(MATCH, dpi(20)))
    col.addView(setupCard(COUNTRIES.map { cc -> countryName(cc) to { choose(cc) } }), llp(MATCH, WRAP))
    sv.addView(col)
    return setupFrame(sv, { showSetup(0, false) })
}

fun MainActivity.setupAppearance(): View {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dpi(24), dpi(20), dpi(24), dpi(8))
    }
    col.addView(tv(tr("Appearance", "Tampilan"), 34f, C.TEXT, true).apply { gravity = Gravity.CENTER })
    col.addView(tv(
        tr("Choose how you would like text and icons to look on this phone.", "Pilih tampilan teks dan ikon di ponsel ini."),
        17f, C.TEXT
    ).apply { gravity = Gravity.CENTER; setPadding(0, dpi(14), 0, dpi(14)) })
    val preview = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    val holder = FrameLayout(this)
    holder.addView(preview, flp(MATCH, MATCH))
    col.addView(holder, llp(MATCH, 0, 1f))
    val slider = StepSlider(this)
    val idx = prefs.getInt("textscale", 0).coerceIn(0, 2)
    slider.setStep(idx)
    fun applyPreview(s: Int) {
        preview.setImageResource(if (s == 2) R.drawable.setup_home else R.drawable.setup_lock)
        val sc = floatArrayOf(1f, 1.1f, 1.2f)[s]
        preview.animate().scaleX(sc).scaleY(sc).setDuration(250).setInterpolator(IOS_EASE).start()
    }
    applyPreview(idx)
    slider.onStep = { s -> prefs.edit().putInt("textscale", s).apply(); applyPreview(s) }
    col.addView(slider, llp(MATCH, WRAP).apply { topMargin = dpi(12) })
    val labels = LinearLayout(this).apply { setPadding(dpi(2), 0, dpi(2), 0) }
    labels.addView(tv(tr("Default", "Standar"), 15f, C.TEXT).apply { gravity = Gravity.START }, llp(0, WRAP, 1f))
    labels.addView(tv(tr("Medium", "Sedang"), 15f, C.TEXT).apply { gravity = Gravity.CENTER }, llp(0, WRAP, 1f))
    labels.addView(tv(tr("Large", "Besar"), 15f, C.TEXT).apply { gravity = Gravity.END }, llp(0, WRAP, 1f))
    col.addView(labels, llp(MATCH, WRAP))
    val btn = tv(tr("Continue", "Lanjutkan"), 17f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        background = rr(C.BLUE, dp(16f))
        setOnClickListener {
            C.textScale = floatArrayOf(1f, 1.1f, 1.25f)[prefs.getInt("textscale", 0).coerceIn(0, 2)]
            showSetup(3)
        }
    }
    val bottom = FrameLayout(this).apply {
        setPadding(dpi(16), dpi(8), dpi(16), botInset + dpi(24))
        addView(btn, flp(MATCH, dpi(54)))
    }
    return setupFrame(col, { showSetup(1, false) }, bottom)
}

fun MainActivity.setupQuickStart(): View {
    val sw = resources.displayMetrics.widthPixels
    val page = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(C.BG)
        isClickable = true
    }
    val top = FrameLayout(this).apply { setBackgroundColor(if (C.dark) 0xFF1C1C1E.toInt() else 0xFFF3F2F8.toInt()) }
    top.addView(ImageView(this).apply {
        setImageResource(R.drawable.setup_quick)
        scaleType = ImageView.ScaleType.FIT_XY
    }, flp(sw, sw, Gravity.TOP))
    val code = prefs.getString("lang", null) ?: I18n.lang
    val rid = resources.getIdentifier("hello_$code", "drawable", packageName)
    if (rid != 0) {
        val hw = (sw * 0.164f).toInt()
        val lp = flp(hw, WRAP, Gravity.TOP or Gravity.START)
        lp.leftMargin = (sw * 0.37f - hw / 2f).toInt()
        lp.topMargin = (sw * 0.575f - hw * 0.15f).toInt()
        top.addView(ImageView(this).apply {
            setImageResource(rid)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, lp)
    }
    top.addView(setupBackBar { showSetup(2, false) }, flp(WRAP, WRAP, Gravity.TOP or Gravity.START))
    page.addView(top, llp(MATCH, (sw * 0.86f).toInt()))

    val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dpi(28), dpi(20), dpi(28), dpi(8))
    }
    col.addView(tv(tr("Quick Start", "Mulai Cepat"), 34f, C.TEXT, true).apply { gravity = Gravity.CENTER })
    val spinRow = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dpi(14), 0, dpi(18)) }
    val spin = ImageView(this)
    val sd = SpinnerDrawable(if (C.dark) Color.WHITE else null, dpi(22))
    spin.setImageDrawable(sd)
    spin.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { sd.start() }
        override fun onViewDetachedFromWindow(v: View) { sd.stop() }
    })
    sd.start()
    spinRow.addView(spin, llp(dpi(22), dpi(22)).apply { marginEnd = dpi(10) })
    spinRow.addView(tv(tr("Looking for your files…", "Mencari file Anda…"), 17f, C.TEXT))
    col.addView(spinRow)
    col.addView(tv(
        tr("Files needs access to this phone's storage to show your documents, downloads and photos.",
            "Files perlu akses ke penyimpanan ponsel ini untuk menampilkan dokumen, unduhan, dan foto Anda."),
        17f, C.TEXT
    ).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dpi(14)) })
    col.addView(tv(
        tr("After you tap Allow Access, turn on \"Allow access to manage all files\" for Files, then come back.",
            "Setelah menekan Izinkan Akses, aktifkan \"Izinkan akses untuk mengelola semua file\" untuk Files, lalu kembali ke sini."),
        17f, C.TEXT
    ).apply { gravity = Gravity.CENTER })
    sv.addView(col)
    page.addView(sv, llp(MATCH, 0, 1f))

    val bottom = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dpi(16), dpi(6), dpi(16), botInset + dpi(20))
    }
    bottom.addView(tv(tr("Allow Access to Files", "Izinkan Akses ke File"), 17f, C.BLUE, true).apply {
        gravity = Gravity.CENTER
        setPadding(dpi(16), dpi(12), dpi(16), dpi(12))
        setOnClickListener { setupRequestAccess() }
    })
    bottom.addView(tv(tr("Set Up Later", "Atur Nanti"), 15f, C.GRAY).apply {
        gravity = Gravity.CENTER
        setPadding(dpi(16), dpi(6), dpi(16), dpi(6))
        setOnClickListener { finishSetup() }
    })
    page.addView(bottom)
    return page
}

fun MainActivity.setupRequestAccess() {
    if (hasAccess()) { finishSetup(); return }
    if (Build.VERSION.SDK_INT >= 30) {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    } else {
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
    }
}

fun MainActivity.showSetup(step: Int, forward: Boolean = true) {
    val prev = setupStep
    setupStep = step
    val page = when (step) {
        0 -> setupLanguage()
        1 -> setupRegion()
        2 -> setupAppearance()
        else -> setupQuickStart()
    }
    if (prev < 0) {
        pages = null
        overlay = null
        barView = null
        barHolder = null
        glassView = null
        root.removeAllViews()
        root.setBackgroundColor(C.BG)
        root.addView(page, MATCH, MATCH)
        return
    }
    val old = root.getChildAt(root.childCount - 1)
    root.addView(page, MATCH, MATCH)
    val w = root.width.toFloat()
    page.translationX = if (forward) w else -w * 0.3f
    page.animate().translationX(0f).setDuration(380).setInterpolator(IOS_EASE).start()
    old.animate().translationX(if (forward) -w * 0.3f else w).setDuration(380).setInterpolator(IOS_EASE)
        .withEndAction { root.removeView(old) }.start()
}

fun MainActivity.finishSetup() {
    if (setupStep < 0) return
    setupStep = -1
    prefs.edit().putBoolean("setup_done", true).apply()
    root.removeAllViews()
    root.setBackgroundColor(C.BG)
    val code = prefs.getString("lang", null) ?: I18n.lang
    val rid = resources.getIdentifier("hello_$code", "drawable", packageName)
    val go = { if (hasAccess()) buildMain() else showPermission() }
    if (rid == 0) { go(); return }
    val iv = ImageView(this).apply {
        setImageResource(rid)
        adjustViewBounds = true
        setColorFilter(C.TEXT, PorterDuff.Mode.SRC_IN)
        alpha = 0f
    }
    root.addView(iv, FrameLayout.LayoutParams(dpi(230), WRAP, Gravity.CENTER))
    iv.animate().alpha(1f).setDuration(450).withEndAction {
        iv.animate().alpha(0f).setStartDelay(550).setDuration(400).withEndAction { go() }.start()
    }.start()
}
