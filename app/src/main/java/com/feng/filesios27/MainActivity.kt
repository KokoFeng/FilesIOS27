package com.feng.filesios27

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.speech.RecognizerIntent
import android.text.InputType
import android.text.TextUtils
import android.view.Choreographer
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.animation.OvershootInterpolator
import android.webkit.MimeTypeMap
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.core.content.FileProvider
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Mi(
    val label: String = "",
    val icon: String? = null,
    val destructive: Boolean = false,
    val check: Boolean = false,
    val dot: Int = 0,
    val header: Boolean = false,
    val sep: Boolean = false,
    val action: () -> Unit = {}
)

class Btn(val label: String, val style: Int = 0, val action: (String) -> Unit = {})

class H(val thumb: FileThumbView, val name: TextView, val sub: TextView, val dot: View)

interface Pl {
    fun playing(): Boolean
    fun dur(): Int
    fun pos(): Int
    fun seek(ms: Int)
    fun play()
    fun pause()
}

class MainActivity : Activity() {
    val ctx: Context get() = this
    lateinit var root: FrameLayout
    var pages: FrameLayout? = null
    var overlay: FrameLayout? = null
    var barView: LinearLayout? = null
    var topInset = 0
    var botInset = 0
    var started = false
    val stack = ArrayList<View>()
    var tab = 2
    var clip: Pair<List<File>, Boolean>? = null
    var sortMode = 0
    var gridMode = true
    var activeSearch: EditText? = null
    var permView: View? = null
    var viewerView: View? = null
    var viewerCleanup: (() -> Unit)? = null
    val ui = Handler(Looper.getMainLooper())
    val prefs by lazy { getSharedPreferences("files", MODE_PRIVATE) }
    val df = SimpleDateFormat("M/d/yy", Locale.getDefault())
    val recentTs = HashMap<String, Long>()
    var glassView: GlassBackdropView? = null
    var barHolder: View? = null
    var glassUntil = 0L
    var glassRunning = false
    var blurOn = false
    var curBlur = 0f
    var blurAnim: ValueAnimator? = null
    var pendingShare: Intent? = null
    val trashDir: File by lazy { File(getExternalFilesDir(null) ?: filesDir, "RecentlyDeleted").also { it.mkdirs() } }
    val icloudDir: File by lazy { File(Environment.getExternalStorageDirectory(), "iCloud Drive").also { it.mkdirs() } }

    // ---------- helpers ----------
    fun flp(w: Int, h: Int, g: Int = Gravity.NO_GRAVITY) = FrameLayout.LayoutParams(w, h, g)
    fun llp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    fun tv(t: String, sp: Float, col: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = sp
        setTextColor(col)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    fun hairline(v: Boolean = false): View = View(this).apply { setBackgroundColor(0x1F000000) }

    fun circle(path: String, dark: Boolean, onClick: (View) -> Unit): FrameLayout = FrameLayout(this).apply {
        background = if (dark) GlassDrawable(dp(20f), 0x66707070, 0x44505050, true) else GlassDrawable(dp(20f), 0xD9FFFFFF.toInt(), 0xA6FFFFFF.toInt())
        pressFx(this)
        elevation = if (dark) 0f else dp(2f)
        addView(icon(path, if (dark) Color.WHITE else Color.BLACK, 20), flp(dpi(20), dpi(20), Gravity.CENTER))
        setOnClickListener { onClick(it) }
    }

    fun hideKb() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
    }

    fun hasAccess() = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun blocking(): Boolean {
        val o = overlay ?: return false
        for (i in 0 until o.childCount) if (o.getChildAt(i).tag is Runnable) return true
        return false
    }

    fun tagOf(f: File) = prefs.getInt("t:" + f.path, 0)

    // ---------- lifecycle ----------
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Thumbs.ctx = applicationContext
        Thumbs.onLoaded = { kickGlass(250) }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root = FrameLayout(this).apply { setBackgroundColor(C.BG) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, i ->
            topInset = i.systemWindowInsetTop
            val bt = i.systemWindowInsetBottom
            if (bt < dpi(120)) botInset = bt
            if (!started) { started = true; ui.post { start() } }
            i
        }
        root.requestApplyInsets()
        if (intent?.action == Intent.ACTION_SEND) pendingShare = intent
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        if (i?.action == Intent.ACTION_SEND) {
            pendingShare = i
            if (overlay != null) consumeShare()
        }
    }

    fun start() { if (hasAccess()) buildMain() else showPermission() }

    override fun onResume() {
        super.onResume()
        if (!started) return
        if (permView != null && hasAccess()) buildMain() else reloadAll()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (hasAccess()) buildMain()
    }

    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (rc == 7 && res == RESULT_OK) {
            val s = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (s != null) activeSearch?.setText(s)
        }
    }

    override fun onBackPressed() {
        val o = overlay
        if (o != null) for (i in o.childCount - 1 downTo 0) {
            val t = o.getChildAt(i).tag
            if (t is Runnable) { t.run(); return }
        }
        if (stack.size > 1) popPage() else super.onBackPressed()
    }

    fun showPermission() {
        root.removeAllViews()
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpi(32), topInset, dpi(32), botInset)
        }
        v.addView(View(this).apply { background = FolderDrawable() }, llp(dpi(96), dpi(78)))
        v.addView(tv("Allow Access to Files", 22f, Color.BLACK, true).apply { gravity = Gravity.CENTER; setPadding(0, dpi(24), 0, dpi(8)) })
        v.addView(tv("Files needs access to your storage to browse, open and manage your files.", 15f, C.GRAY).apply { gravity = Gravity.CENTER })
        v.addView(tv("Allow Access", 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rr(C.BLUE, dp(14f))
            setPadding(dpi(32), dpi(14), dpi(32), dpi(14))
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }, llp(WRAP, WRAP).apply { topMargin = dpi(32) })
        root.addView(v, MATCH, MATCH)
        permView = v
    }

    fun buildMain() {
        permView = null
        root.removeAllViews()
        stack.clear()
        val p = NotifyFrame(this)
        pages = p
        val o = FrameLayout(this).apply { elevation = dp(40f) }
        overlay = o
        o.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { ui.post { syncBlur() } }
            override fun onChildViewRemoved(parent: View?, child: View?) { ui.post { syncBlur() } }
        })
        root.addView(p, MATCH, MATCH)
        buildBar()
        p.onChange = { glassView?.invalidate() }
        root.addView(o, MATCH, MATCH)
        showTab(2, false)
        purgeTrash()
        ui.postDelayed({ consumeShare() }, 400)
        if (needInstallPerm() && !prefs.getBoolean("askedInstall", false)) {
            prefs.edit().putBoolean("askedInstall", true).apply()
            ui.postDelayed({
                alert("Allow Installing Apps", "Files needs permission to install apps from your files.", null, 0,
                    listOf(Btn("Not Now"), Btn("Settings", 1) { openInstallSettings() }))
            }, 900)
        }
    }

    // ---------- bottom bar ----------
    fun buildBar() {
        val sw = resources.displayMetrics.widthPixels
        val corner = dp(36f)
        val holder = FrameLayout(this).apply {
            elevation = dp(10f)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, corner)
                    o.setAlpha(0.6f)
                }
            }
        }
        val glass = GlassBackdropView(this, pages!!, corner)
        glassView = glass
        holder.addView(glass, flp(MATCH, MATCH))
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpi(6), dpi(6), dpi(6), dpi(6))
            background = GlassDrawable(corner, 0x47FFFFFF, 0x24FFFFFF)
        }
        val names = arrayOf("Recents", "Shared", "Browse")
        for (i in 0..2) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpi(4), dpi(8), dpi(4), dpi(6))
                tag = i
            }
            cell.addView(ImageView(this).apply {
                setImageDrawable(TabIcon(i, Color.BLACK))
                layoutParams = ViewGroup.LayoutParams(dpi(24), dpi(24))
            })
            cell.addView(tv(names[i], 10f, Color.BLACK, true).apply {
                maxLines = 1
                setSingleLine()
                ellipsize = null
                gravity = Gravity.CENTER
            }, llp(WRAP, WRAP))
            pressFx(cell)
            cell.setOnClickListener { if (tab != i) showTab(i, true) else popToRoot() }
            b.addView(cell, llp(0, WRAP, 1f))
        }
        holder.addView(b, flp(MATCH, WRAP))
        barView = b
        barHolder = holder
        root.addView(holder, flp(minOf(maxOf((sw * 0.7f).toInt(), dpi(250)), sw - dpi(24)), WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = botInset + dpi(10) })
    }

    fun updateBar() {
        val b = barView ?: return
        for (i in 0..2) {
            val cell = b.getChildAt(i) as LinearLayout
            val sel = i == tab
            val col = if (sel) C.BLUE else Color.BLACK
            cell.background = if (sel) GlassDrawable(dp(28f), 0x24000000, 0x14000000) else null
            (cell.getChildAt(0) as ImageView).setImageDrawable(TabIcon(cell.tag as Int, col))
            (cell.getChildAt(1) as TextView).setTextColor(col)
        }
    }

    // ---------- liquid glass helpers ----------
    /** Kaca diperbarui langsung oleh NotifyFrame; ini hanya pemicu manual tambahan. */
    fun kickGlass(ms: Long = 0) { glassView?.invalidate() }

    fun pressFx(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(180)
                    .setInterpolator(OvershootInterpolator(2f)).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(IOS_EASE).start()
            }
            false
        }
    }

    /** Blur latar belakang (halaman + bar) saat ada popup. Android 12+. */
    fun syncBlur() {
        if (Build.VERSION.SDK_INT < 31) return
        val o = overlay ?: return
        var need = false
        for (i in 0 until o.childCount) {
            val c = o.getChildAt(i)
            if (c.tag is Runnable && c !== viewerView) need = true
        }
        if (need == blurOn) return
        blurOn = need
        blurAnim?.cancel()
        blurAnim = ValueAnimator.ofFloat(curBlur, if (need) 28f else 0f).apply {
            duration = 280
            addUpdateListener { setBlur(it.animatedValue as Float) }
            start()
        }
    }

    fun setBlur(r: Float) {
        curBlur = r
        val eff = if (r < 0.5f) null else RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
        pages?.setRenderEffect(eff)
        barHolder?.alpha = 1f - (r / 28f).coerceIn(0f, 1f)
    }

    // ---------- page stack ----------
    fun showTab(i: Int, anim: Boolean) {
        tab = i
        kickGlass(600)
        val pg = pages ?: return
        val v: View = when (i) {
            0 -> ListPage(null, 1, "Recents", false).view
            1 -> ListPage(null, 2, "Shared", false).view
            else -> browsePage()
        }
        val old = ArrayList<View>()
        for (k in 0 until pg.childCount) old.add(pg.getChildAt(k))
        stack.clear()
        stack.add(v)
        pg.addView(v, MATCH, MATCH)
        if (anim) {
            v.alpha = 0f
            v.animate().alpha(1f).setDuration(220).withEndAction { old.forEach { pg.removeView(it) } }.start()
        } else old.forEach { pg.removeView(it) }
        updateBar()
    }

    fun pushPage(v: View) {
        kickGlass(700)
        val pg = pages ?: return
        val w = root.width.toFloat()
        val prev = stack.last()
        v.elevation = dp(8f)
        pg.addView(v, MATCH, MATCH)
        v.translationX = w
        v.animate().translationX(0f).setDuration(380).setInterpolator(IOS_EASE).start()
        prev.animate().translationX(-w * 0.3f).setDuration(380).setInterpolator(IOS_EASE).start()
        stack.add(v)
    }

    fun popPage() {
        if (stack.size < 2) return
        kickGlass(700)
        hideKb()
        val w = root.width.toFloat()
        val v = stack.removeAt(stack.size - 1)
        val prev = stack.last()
        v.animate().translationX(w).setDuration(300).setInterpolator(IOS_EASE).withEndAction { pages?.removeView(v) }.start()
        prev.animate().translationX(0f).setDuration(300).setInterpolator(IOS_EASE).start()
    }

    fun popToRoot() {
        while (stack.size > 2) { val v = stack.removeAt(stack.size - 2); pages?.removeView(v) }
        popPage()
    }

    fun reloadAll() { stack.forEach { (it.tag as? ListPage)?.reload() } }

    // Swipe dari tepi kiri untuk kembali (ala iOS)
    private var dragging = false
    private var startX = 0f
    private var vt: VelocityTracker? = null

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (started) kickGlass(1500)
        if (started && stack.size > 1 && !blocking()) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (e.x < dpi(22)) {
                    dragging = true
                    startX = e.x
                    vt = VelocityTracker.obtain().also { it.addMovement(e) }
                    return true
                }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    vt?.addMovement(e)
                    val dx = (e.x - startX).coerceAtLeast(0f)
                    stack.last().translationX = dx
                    stack[stack.size - 2].translationX = -root.width * 0.3f + dx * 0.3f
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragging = false
                    vt?.addMovement(e)
                    vt?.computeCurrentVelocity(1000)
                    val vx = vt?.xVelocity ?: 0f
                    vt?.recycle()
                    vt = null
                    val dx = e.x - startX
                    if (dx > root.width * 0.4f || vx > 1000f) popPage()
                    else {
                        stack.last().animate().translationX(0f).setDuration(250).setInterpolator(IOS_EASE).start()
                        stack[stack.size - 2].animate().translationX(-root.width * 0.3f).setDuration(250).setInterpolator(IOS_EASE).start()
                    }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(e)
    }

    // ---------- Browse ----------
    fun browsePage(): View {
        val sv = ScrollView(this).apply { setBackgroundColor(C.BG); isVerticalScrollBarEnabled = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16), topInset + dpi(16), dpi(16), botInset + dpi(110))
        }
        sv.addView(col)
        col.addView(tv("Browse", 34f, Color.BLACK, true).apply { setPadding(dpi(4), dpi(24), 0, dpi(8)) })
        val internal = Environment.getExternalStorageDirectory()
        val sb = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = GlassDrawable(dp(19f), 0xB3FFFFFF.toInt(), 0x80FFFFFF.toInt()); elevation = dp(3f)
            setPadding(dpi(8), 0, dpi(8), 0)
            setOnClickListener { pushPage(ListPage(internal, 0, "On My iPhone", true, true).view) }
        }
        sb.addView(icon(P.SEARCH, C.GRAY, 18))
        sb.addView(tv("Search", 17f, C.GRAY).apply { setPadding(dpi(8), 0, 0, 0) }, llp(0, WRAP, 1f))
        sb.addView(icon(P.MIC, C.GRAY, 18))
        col.addView(sb, llp(MATCH, dpi(38)).apply { bottomMargin = dpi(12) })

        val favs = listOf(
            Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_DOCUMENTS, Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_MOVIES
        ).map { Environment.getExternalStoragePublicDirectory(it) }.filter { it.exists() }
        section(col, "Favorites", favs.map { Triple(it.name, P.FOLDER, it) })

        val locs = ArrayList<Triple<String, String, File>>()
        locs.add(Triple("iCloud Drive", P.CLOUD, icloudDir))
        locs.add(Triple("On My iPhone", P.PHONE, internal))
        try {
            val dirs = getExternalFilesDirs(null)
            for (i in 1 until dirs.size) {
                val d = dirs[i] ?: continue
                val p = d.absolutePath.substringBefore("/Android")
                locs.add(Triple("SD Card", P.FOLDER, File(p)))
            }
        } catch (e: Exception) {}
        locs.add(Triple("Recently Deleted", P.TRASH, trashDir))
        section(col, "Locations", locs)
        return sv
    }

    fun section(col: LinearLayout, title: String, rows: List<Triple<String, String, File>>) {
        if (rows.isEmpty()) return
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(4), dpi(16), 0, dpi(8)) }
        head.addView(tv(title, 22f, Color.BLACK, true))
        val chev = icon(P.CHEV, C.BLUE, 18)
        chev.rotation = 90f
        head.addView(chev, llp(dpi(18), dpi(18)).apply { marginStart = dpi(8) })
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rr(Color.WHITE, dp(12f)); clipToOutline = true }
        for ((i, r) in rows.withIndex()) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpi(14), 0, dpi(14), 0)
                background = pressBg()
                setOnClickListener { pushPage(ListPage(r.third, if (r.third.path == trashDir.path) 3 else 0, r.first, true).view) }
            }
            row.addView(icon(r.second, C.BLUE, 24))
            row.addView(tv(r.first, 17f, Color.BLACK).apply { setPadding(dpi(14), 0, 0, 0) }, llp(0, WRAP, 1f))
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

    // ---------- list page ----------
    fun sortFiles(l: List<File>): List<File> {
        val cmp: Comparator<File> = when (sortMode) {
            1 -> compareBy<File>({ FileOps.ext(it) }, { it.name.lowercase() })
            2 -> compareByDescending<File> { it.lastModified() }
            3 -> compareByDescending<File> { it.length() }
            else -> compareBy<File> { it.name.lowercase() }
        }
        return l.sortedWith(compareByDescending<File> { it.isDirectory }.then(cmp))
    }

    fun makeCell(g: Boolean): LinearLayout {
        val c = LinearLayout(this).apply { background = pressBg() }
        val thumb = FileThumbView(this)
        val name = tv("", if (g) 14f else 16f, Color.BLACK)
        val sub = tv("", if (g) 12f else 13f, C.GRAY)
        val dot = View(this)
        name.maxLines = 2
        name.ellipsize = TextUtils.TruncateAt.END
        sub.maxLines = if (g) 2 else 1
        val nr = LinearLayout(this).apply { gravity = if (g) Gravity.CENTER else Gravity.CENTER_VERTICAL }
        nr.addView(dot, llp(dpi(8), dpi(8)).apply { marginEnd = dpi(4) })
        if (g) {
            name.gravity = Gravity.CENTER
            sub.gravity = Gravity.CENTER
            nr.addView(name, llp(WRAP, WRAP))
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER_HORIZONTAL
            c.setPadding(dpi(4), dpi(10), dpi(4), dpi(8))
            c.addView(thumb, llp(MATCH, dpi(96)))
            c.addView(nr, llp(WRAP, WRAP).apply { topMargin = dpi(4) })
            c.addView(sub, llp(WRAP, WRAP))
        } else {
            nr.addView(name, llp(WRAP, WRAP))
            c.orientation = LinearLayout.HORIZONTAL
            c.gravity = Gravity.CENTER_VERTICAL
            c.setPadding(dpi(16), dpi(8), dpi(16), dpi(8))
            c.minimumHeight = dpi(64)
            c.addView(thumb, llp(dpi(44), dpi(44)))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(nr)
            col.addView(sub)
            c.addView(col, llp(0, WRAP, 1f).apply { marginStart = dpi(14) })
        }
        c.tag = H(thumb, name, sub, dot)
        return c
    }

    fun midCut(n: String, max: Int): String =
        if (n.length <= max) n else n.substring(0, (max * 0.55).toInt()) + "…" + n.substring(n.length - (max * 0.35).toInt())

    fun dayStr(ts: Long): String {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        if (now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR))
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        now.add(Calendar.DAY_OF_YEAR, -1)
        if (now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)) return "Yesterday"
        return df.format(Date(ts))
    }

    fun locationOf(f: File): String {
        val p = f.path
        return when {
            p.startsWith(trashDir.path) -> "Recently Deleted"
            p.startsWith(icloudDir.path) -> "iCloud Drive"
            p.startsWith(Environment.getExternalStorageDirectory().path) -> "On My iPhone"
            else -> "SD Card"
        }
    }

    fun subText(f: File, g: Boolean, kind: Int): String {
        val sep = if (g) "\n" else " – "
        if (kind == 1) return dayStr(recentTs[f.path] ?: f.lastModified()) + sep + locationOf(f)
        if (f.isDirectory) {
            val n = f.list()?.size ?: 0
            return if (n == 1) "1 item" else "$n items"
        }
        return df.format(Date(f.lastModified())) + sep + FileOps.size(f.length())
    }

    fun bindCell(c: View, f: File, g: Boolean, kind: Int) {
        val h = c.tag as H
        h.thumb.bind(f)
        h.name.text = if (g) midCut(f.name, 28) else f.name
        val t = tagOf(f)
        h.dot.visibility = if (t != 0) View.VISIBLE else View.GONE
        if (t != 0) h.dot.background = oval(t)
        h.sub.text = subText(f, g, kind)
    }

    inner class ListPage(val dir: File?, val kind: Int, val title: String, val back: Boolean, val focusSearch: Boolean = false) {
        val view = LinearLayout(ctx)
        var items: List<File> = emptyList()
        var base: List<File> = emptyList()
        var query = ""
        var searchJob = 0
        val la = A(false)
        val ga = A(true)
        val list = ListView(ctx)
        val grid = GridView(ctx)
        val empty = tv("No Items", 20f, C.GRAY, true)
        val search = IosEditText(ctx)

        inner class A(val g: Boolean) : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(p: Int): Any = items[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val row = cv ?: makeCell(g)
                bindCell(row, items[p], g, kind)
                return row
            }
        }

        init {
            view.orientation = LinearLayout.VERTICAL
            view.setBackgroundColor(C.BG)
            view.tag = this
            val head = FrameLayout(ctx).apply { setPadding(dpi(12), topInset + dpi(8), dpi(12), dpi(6)) }
            if (back) {
                head.addView(circle(P.BACK, false) { popPage() }, flp(dpi(40), dpi(40), Gravity.START))
                head.addView(tv(title, 17f, Color.BLACK, true).apply {
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER; maxWidth = dpi(200)
                }, flp(WRAP, dpi(40), Gravity.CENTER_HORIZONTAL))
            } else {
                head.minimumHeight = topInset + dpi(64)
                head.addView(tv(title, 34f, Color.BLACK, true).apply { setPadding(dpi(6), 0, 0, 0) }, flp(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL))
            }
            head.addView(circle(P.MORE, false) { v -> showMore(this@ListPage, v) }, flp(dpi(40), dpi(40), Gravity.END or (if (back) Gravity.NO_GRAVITY else Gravity.CENTER_VERTICAL)))
            view.addView(head, llp(MATCH, WRAP))

            val sb = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = GlassDrawable(dp(19f), 0xB3FFFFFF.toInt(), 0x80FFFFFF.toInt()); elevation = dp(3f)
                setPadding(dpi(8), 0, dpi(8), 0)
            }
            sb.addView(icon(P.SEARCH, C.GRAY, 18))
            search.apply {
                hint = "Search"
                textSize = 17f
                setSingleLine()
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setHintTextColor(C.GRAY)
                setPadding(dpi(8), 0, 0, 0)
                setOnEditorActionListener { _, _, _ -> hideKb(); true }
                onChange { query = text.toString(); applyQuery() }
            }
            sb.addView(search, llp(0, MATCH, 1f))
            sb.addView(icon(P.MIC, C.GRAY, 18).apply {
                setOnClickListener {
                    activeSearch = search
                    try {
                        startActivityForResult(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM), 7
                        )
                    } catch (e: ActivityNotFoundException) { toast("Voice search unavailable") }
                }
            })
            view.addView(sb, llp(MATCH, dpi(38)).apply { setMargins(dpi(16), dpi(4), dpi(16), dpi(8)) })

            val content = FrameLayout(ctx)
            list.apply {
                divider = null
                selector = ColorDrawable(0)
                clipToPadding = false
                setPadding(0, 0, 0, botInset + dpi(110))
                adapter = la
                setOnItemClickListener { _, _, pos, _ -> onItem(items[pos]) }
                setOnItemLongClickListener { _, v, pos, _ -> contextMenu(items[pos], v, kind); true }
            }
            grid.apply {
                numColumns = (root.width / dpi(115)).coerceAtLeast(3)
                selector = ColorDrawable(0)
                clipToPadding = false
                horizontalSpacing = dpi(4)
                setPadding(dpi(8), dpi(4), dpi(8), botInset + dpi(110))
                adapter = ga
                setOnItemClickListener { _, _, pos, _ -> onItem(items[pos]) }
                setOnItemLongClickListener { _, v, pos, _ -> contextMenu(items[pos], v, kind); true }
            }
            empty.gravity = Gravity.CENTER
            empty.visibility = View.GONE
            content.addView(list, MATCH, MATCH)
            content.addView(grid, MATCH, MATCH)
            content.addView(empty, flp(MATCH, WRAP, Gravity.CENTER))
            view.addView(content, llp(MATCH, 0, 1f))
            applyMode()
            reload()
            if (focusSearch) search.postDelayed({
                search.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(search, 0)
            }, 400)
        }

        fun applyMode() {
            list.visibility = if (gridMode) View.GONE else View.VISIBLE
            grid.visibility = if (gridMode) View.VISIBLE else View.GONE
        }

        fun onItem(f: File) {
            hideKb()
            if (kind == 3) {
                alert("Recently Deleted", "\"${f.name}\" was deleted recently. Recover it to open it.", null, 0,
                    listOf(Btn("Cancel"), Btn("Recover", 1) { bg("Recovered") { recover(f) } }))
                return
            }
            if (f.isDirectory) pushPage(ListPage(f, 0, f.name, true).view) else openFile(f)
        }

        fun reload() {
            Thread {
                val r: List<File> = try {
                    when (kind) {
                        0 -> FileOps.list(dir!!)
                        3 -> FileOps.list(dir!!).sortedByDescending { prefs.getLong("del:" + it.path, it.lastModified()) }
                        1 -> loadRecents()
                        else -> prefs.getString("shared", "")!!.split("\n").filter { it.isNotBlank() }.map { File(it) }.filter { it.exists() }
                    }
                } catch (e: Exception) { emptyList() }
                ui.post { base = r; applyQuery() }
            }.start()
        }

        fun applyQuery() {
            if (query.isBlank()) {
                items = if (kind == 0) sortFiles(base) else base
                refresh()
            } else if (kind == 0) {
                val q = query
                val id = ++searchJob
                Thread {
                    val res = FileOps.search(dir!!, q)
                    ui.post { if (id == searchJob) { items = res; refresh() } }
                }.start()
            } else {
                items = base.filter { it.name.contains(query, true) }
                refresh()
            }
        }

        fun refresh() {
            applyMode()
            kickGlass(400)
            la.notifyDataSetChanged()
            ga.notifyDataSetChanged()
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    fun showMore(pg: ListPage, v: View) {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val items = ArrayList<Mi>()
        if (pg.kind == 3) {
            items.add(Mi("Delete All", P.TRASH, destructive = true) { confirmPurge(pg.base) })
            items.add(Mi(sep = true))
        }
        if (pg.kind == 0) {
            items.add(Mi("Download from URL", P.DOWNLOAD) { askUrl(pg.dir!!) })
            items.add(Mi("New Folder", P.NEWFOLDER) {
                alert("New Folder", null, "untitled folder", 14, listOf(Btn("Cancel"), Btn("Done", 1) { n ->
                    val nn = n.trim()
                    if (nn.isNotEmpty()) bg("Folder created") {
                        val d = File(pg.dir, nn)
                        if (d.exists()) throw Exception("Name already taken")
                        d.mkdir()
                    }
                }))
            })
            if (clip != null) items.add(Mi("Paste", P.COPY) { paste(pg.dir!!) })
            items.add(Mi(sep = true))
        }
        items.add(Mi("as Icons", P.GRID, check = gridMode) { gridMode = true; stack.forEach { (it.tag as? ListPage)?.refresh() } })
        items.add(Mi("as List", P.LIST, check = !gridMode) { gridMode = false; stack.forEach { (it.tag as? ListPage)?.refresh() } })
        if (pg.kind == 0) {
            items.add(Mi(sep = true))
            items.add(Mi("Sort By", header = true))
            val n = arrayOf("Name", "Kind", "Date", "Size")
            for (i in 0..3) items.add(Mi(n[i], check = sortMode == i) { sortMode = i; stack.forEach { (it.tag as? ListPage)?.applyQuery() } })
        }
        showMenu((loc[0] + v.width - dpi(250)).toFloat(), (loc[1] + v.height + dpi(6)).toFloat(), null, items)
    }

    // ---------- popup menu ----------
    fun showMenu(ax: Float, ay: Float, top: List<Mi>?, items: List<Mi>, preview: File? = null) {
        val o = overlay ?: return
        hideKb()
        val w = dpi(270)
        val dim = View(this).apply { setBackgroundColor(0x33000000); alpha = 0f; isClickable = true }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassDrawable(dp(22f), 0xE8F6F6F8.toInt(), 0xDCF0F0F3.toInt())
            clipToOutline = true
            elevation = dp(20f)
        }
        val pv: FileThumbView? = if (preview != null) FileThumbView(this).also { it.bind(preview) } else null
        var gone = false
        fun dismiss(after: (() -> Unit)?) {
            if (gone) return
            gone = true
            dim.animate().alpha(0f).setDuration(140).start()
            pv?.animate()?.alpha(0f)?.setDuration(140)?.start()
            card.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(140).withEndAction {
                o.removeView(dim); o.removeView(card)
                if (pv != null) o.removeView(pv)
                after?.invoke()
            }.start()
        }
        dim.tag = Runnable { dismiss(null) }
        dim.setOnClickListener { dismiss(null) }
        if (top != null) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (m in top) {
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(0, dpi(12), 0, dpi(10))
                    background = pressBg()
                    setOnClickListener { dismiss { m.action() } }
                }
                cell.addView(icon(m.icon!!, Color.BLACK, 22))
                cell.addView(tv(m.label, 11f, Color.BLACK).apply { setPadding(0, dpi(4), 0, 0) })
                r.addView(cell, llp(0, WRAP, 1f))
            }
            card.addView(r)
            card.addView(hairline(), llp(MATCH, 1))
        }
        var prevRow = false
        for (m in items) {
            when {
                m.sep -> {
                    card.addView(View(this).apply { setBackgroundColor(0x1F000000) }, llp(MATCH, dpi(8)))
                    prevRow = false
                }
                m.header -> {
                    card.addView(tv(m.label, 13f, C.GRAY).apply { setPadding(dpi(16), dpi(10), dpi(16), dpi(4)) })
                    prevRow = false
                }
                else -> {
                    if (prevRow) card.addView(hairline(), llp(MATCH, 1))
                    val col = if (m.destructive) C.RED else Color.BLACK
                    val row = LinearLayout(this).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dpi(16), 0, dpi(16), 0)
                        background = pressBg()
                        setOnClickListener { dismiss { m.action() } }
                    }
                    row.addView(tv(m.label, 17f, col), llp(0, WRAP, 1f))
                    when {
                        m.dot != 0 -> row.addView(View(this).apply { background = oval(m.dot) }, llp(dpi(16), dpi(16)))
                        m.check -> row.addView(icon(P.CHECK, Color.BLACK, 18))
                        m.icon != null -> row.addView(icon(m.icon, col, 22))
                    }
                    card.addView(row, llp(MATCH, dpi(44)))
                    prevRow = true
                }
            }
        }
        card.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val h = card.measuredHeight
        val pvW = dpi(86)
        val pvH = if (pv != null) dpi(112) else 0
        val gap = if (pv != null) dpi(14) else 0
        val x = ax.coerceIn(dpi(12).toFloat(), (root.width - w - dpi(12)).toFloat())
        val y = minOf(ay, (root.height - botInset - h - pvH - gap - dpi(12)).toFloat()).coerceAtLeast((topInset + dpi(8)).toFloat())
        o.addView(dim, MATCH, MATCH)
        if (pv != null) {
            o.addView(pv, flp(pvW, pvH))
            pv.x = x + (w - pvW) / 2f
            pv.y = y
            pv.scaleX = 0.6f
            pv.scaleY = 0.6f
            pv.alpha = 0f
            pv.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.1f)).start()
        }
        o.addView(card, flp(w, WRAP))
        card.x = x
        card.y = y + pvH + gap
        card.pivotX = w / 2f
        card.pivotY = 0f
        card.scaleX = 0.6f
        card.scaleY = 0.6f
        card.alpha = 0f
        dim.animate().alpha(1f).setDuration(200).start()
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.1f)).start()
    }

    // ---------- iOS alert ----------
    fun alert(title: String, msg: String?, initial: String?, selEnd: Int, buttons: List<Btn>, selStart: Int = 0) {
        val o = overlay ?: return
        val dim = View(this).apply { setBackgroundColor(0x55000000); alpha = 0f; isClickable = true }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rr(0xF2F2F2F2.toInt(), dp(14f))
            clipToOutline = true
            elevation = dp(16f)
        }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(16), dpi(18), dpi(16), dpi(16)) }
        body.addView(tv(title, 17f, Color.BLACK, true).apply { gravity = Gravity.CENTER })
        if (msg != null) body.addView(tv(msg, 13f, Color.BLACK).apply { gravity = Gravity.CENTER; setPadding(0, dpi(4), 0, 0) })
        var ed: IosEditText? = null
        if (initial != null) {
            val e = IosEditText(this).apply {
                setText(initial)
                setSingleLine()
                textSize = 15f
                setTextColor(Color.BLACK)
                setPadding(dpi(8), dpi(7), dpi(8), dpi(7))
                background = rr(Color.WHITE, dp(6f))
            }
            ed = e
            body.addView(e, llp(MATCH, WRAP).apply { topMargin = dpi(12) })
        }
        card.addView(body)
        card.addView(hairline(), llp(MATCH, 1))
        var gone = false
        fun dismiss(after: () -> Unit) {
            if (gone) return
            gone = true
            hideKb()
            dim.animate().alpha(0f).setDuration(160).start()
            card.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f).setDuration(160).withEndAction {
                o.removeView(dim); o.removeView(card); after()
            }.start()
        }
        dim.tag = Runnable { dismiss {} }
        val horizontal = buttons.size == 2
        val row = LinearLayout(this).apply { orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        for ((i, b) in buttons.withIndex()) {
            if (i > 0) row.addView(hairline(), if (horizontal) llp(1, MATCH) else llp(MATCH, 1))
            val t = tv(b.label, 17f, if (b.style == 2) C.RED else C.BLUE, b.style == 1).apply {
                gravity = Gravity.CENTER
                background = pressBg()
                setOnClickListener {
                    val txt = ed?.text?.toString() ?: ""
                    dismiss { b.action(txt) }
                }
            }
            row.addView(t, if (horizontal) llp(0, dpi(44), 1f) else llp(MATCH, dpi(44)))
        }
        card.addView(row)
        o.addView(dim, MATCH, MATCH)
        o.addView(card, flp(dpi(270), WRAP, Gravity.CENTER))
        card.scaleX = 1.15f
        card.scaleY = 1.15f
        card.alpha = 0f
        dim.animate().alpha(1f).setDuration(220).start()
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(IOS_EASE).start()
        val e = ed
        if (e != null) e.post {
            e.requestFocus()
            e.setSelection(selStart.coerceIn(0, e.text.length), selEnd.coerceIn(0, e.text.length))
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(e, 0)
        }
    }

    fun toast(m: String) {
        val o = overlay ?: return
        val t = tv(m, 14f, Color.WHITE).apply {
            background = rr(0xE62C2C2E.toInt(), dp(20f))
            setPadding(dpi(18), dpi(10), dpi(18), dpi(10))
            alpha = 0f
            elevation = dp(6f)
        }
        o.addView(t, flp(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = botInset + dpi(100) })
        t.animate().alpha(1f).translationY(-dp(8f)).setDuration(200).withEndAction {
            t.animate().alpha(0f).setStartDelay(1600).setDuration(250).withEndAction { o.removeView(t) }.start()
        }.start()
    }

    // ---------- file actions ----------
    fun bg(okMsg: String?, work: () -> Unit) {
        Thread {
            try {
                work()
                ui.post { reloadAll(); if (okMsg != null) toast(okMsg) }
            } catch (e: Throwable) {
                ui.post { reloadAll(); toast("Failed: " + (e.message ?: "error")) }
            }
        }.start()
    }

    fun contextMenu(f: File, cell: View, kind: Int = 0) {
        val loc = IntArray(2)
        cell.getLocationOnScreen(loc)
        val x = loc[0] + cell.width / 2f - dpi(135)
        val y = loc[1] + cell.height - dpi(8).toFloat()
        if (kind == 3) { trashMenu(f, x, y); return }
        val ext = FileOps.ext(f)
        val items = ArrayList<Mi>()
        items.add(Mi("Copy", P.COPY) { clip = Pair(listOf(f), false); toast("Copied") })
        items.add(Mi("Duplicate", P.DUP) { bg("Duplicated") { FileOps.copy(f, f.parentFile!!) } })
        items.add(Mi("Move", P.FOLDER) { clip = Pair(listOf(f), true); toast("Open a folder, tap ••• then Paste") })
        items.add(Mi("Delete", P.TRASH, destructive = true) { bg("Moved to Recently Deleted") { trashIt(f) } })
        items.add(Mi(sep = true))
        items.add(Mi("Info", P.INFO) { info(f) })
        if (f.isDirectory) items.add(Mi("Open", P.FOLDER) { pushPage(ListPage(f, 0, f.name, true).view) })
        else items.add(Mi("Quick Look", P.EYE) { quickLook(f) })
        items.add(Mi("Tags", P.TAG) { tagMenu(f, x, y) })
        items.add(Mi("Rename", P.PENCIL) { rename(f) })
        items.add(Mi(sep = true))
        if (ext in INSTALLABLE) items.add(Mi("Install", P.DOWNLOAD) { openOther(f) })
        items.add(Mi("Share", P.SHARE) { share(f) })
        if (ext in FileOps.IMG) items.add(Mi("Markup", P.MARKUP) { markup(f) })
        if (ext == "zip") items.add(Mi("Uncompress", P.UNZIP) { extract(f) })
        else items.add(Mi("Compress", P.ARCHIVE) { bg("Compressed") { FileOps.zip(listOf(f), f.parentFile!!) } })
        showMenu(x, y, null, items, f)
    }

    fun tagMenu(f: File, x: Float, y: Float) {
        val cur = tagOf(f)
        val items = ArrayList<Mi>()
        for (i in C.tagColors.indices) items.add(Mi(C.tagNames[i], dot = C.tagColors[i], check = false) {
            prefs.edit().putInt("t:" + f.path, C.tagColors[i]).apply(); reloadAll()
        })
        if (cur != 0) items.add(Mi("Remove Tag", destructive = true) { prefs.edit().remove("t:" + f.path).apply(); reloadAll() })
        showMenu(x, y, null, items)
    }

    fun rename(f: File) {
        val sel = if (f.isDirectory || !f.name.contains('.')) f.name.length else f.name.lastIndexOf('.')
        alert("Rename", null, f.name, sel, listOf(Btn("Cancel"), Btn("Done", 1) { n ->
            val nn = n.trim()
            if (nn.isNotEmpty() && nn != f.name) bg(null) {
                val d = File(f.parentFile, nn)
                if (d.exists()) throw Exception("Name already taken")
                if (!f.renameTo(d)) throw Exception("Cannot rename")
                val t = prefs.getInt("t:" + f.path, 0)
                if (t != 0) prefs.edit().remove("t:" + f.path).putInt("t:" + d.path, t).apply()
            }
        }))
    }

    fun extract(f: File) {
        toast("Extracting…")
        bg("Extracted") { FileOps.unzip(f, f.parentFile!!) }
    }

    fun lastOpened(f: File): Long? {
        for (line in prefs.getString("recent", "")!!.split("\n")) {
            val i = line.indexOf('|')
            if (i < 0) continue
            if (line.substring(i + 1) == f.path) return line.substring(0, i).toLongOrNull()
        }
        return null
    }

    fun info(f: File) {
        Thread {
            val sz = FileOps.dirSize(f)
            var created = f.lastModified()
            try {
                created = java.nio.file.Files.readAttributes(f.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).creationTime().toMillis()
            } catch (e: Throwable) {}
            ui.post { showInfo(f, sz, created) }
        }.start()
    }

    fun showInfo(f: File, sz: Long, created: Long) {
        val o = overlay ?: return
        hideKb()
        val sh = (root.height * 0.9f).toInt()
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
                o.removeView(dim); o.removeView(sheet)
            }.start()
        }
        dim.tag = Runnable { close() }
        dim.setOnClickListener { close() }

        val head = FrameLayout(this).apply { setBackgroundColor(0xF2F9F9F9.toInt()) }
        head.addView(tv("Info", 17f, Color.BLACK, true), flp(WRAP, WRAP, Gravity.CENTER))
        head.addView(tv("Done", 17f, C.BLUE, true).apply {
            setPadding(dpi(16), dpi(14), dpi(16), dpi(14))
            setOnClickListener { close() }
        }, flp(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL))
        sheet.addView(head, llp(MATCH, dpi(52)))
        sheet.addView(hairline(), llp(MATCH, 1))

        val sv = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20), dpi(20), dpi(20), botInset + dpi(24))
        }
        sv.addView(col)
        col.addView(FileThumbView(this).also { it.bind(f) }, llp(MATCH, dpi(190)))
        col.addView(tv(f.name, 22f, Color.BLACK, true).apply { setPadding(0, dpi(16), 0, dpi(2)) })
        col.addView(tv(FileOps.kind(f) + " - " + FileOps.size(sz), 15f, C.GRAY))
        if (!f.isDirectory) col.addView(tv("OPEN", 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rr(C.BLUE, dp(18f))
            setPadding(dpi(22), dpi(8), dpi(22), dpi(8))
            setOnClickListener { close(); ui.postDelayed({ openFile(f) }, 330) }
        }, llp(WRAP, WRAP).apply { topMargin = dpi(14) })
        col.addView(hairline(), llp(MATCH, 1).apply { topMargin = dpi(20) })

        val ih = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(16), 0, dpi(8)) }
        ih.addView(tv("Information", 20f, Color.BLACK, true), llp(0, WRAP, 1f))
        val more = tv("Show More", 15f, C.BLUE)
        ih.addView(more)
        col.addView(ih)

        fun row(k: String, v: String, blue: Boolean = false): View {
            val r = LinearLayout(this).apply { setPadding(0, dpi(10), 0, dpi(10)) }
            r.addView(tv(k, 15f, C.GRAY), llp(dpi(90), WRAP))
            r.addView(tv(v, 15f, if (blue) C.BLUE else Color.BLACK).apply { gravity = Gravity.END }, llp(0, WRAP, 1f))
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            box.addView(hairline(), llp(MATCH, 1))
            box.addView(r)
            return box
        }
        val dfm = SimpleDateFormat("d MMMM yyyy 'at' HH:mm", Locale.getDefault())
        val where = locationOf(f)
        val base = when (where) {
            "iCloud Drive" -> icloudDir
            "Recently Deleted" -> trashDir
            else -> Environment.getExternalStorageDirectory()
        }
        val rel = (f.parentFile?.path ?: "").removePrefix(base.path).trim('/')
        val whereStr = if (rel.isEmpty()) where else where + " > " + rel.split('/').joinToString(" > ")
        col.addView(row("Kind", FileOps.kind(f)))
        col.addView(row("Size", FileOps.size(sz)))
        val extra = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        extra.addView(row("Created", dfm.format(Date(created))))
        extra.addView(row("Modified", dfm.format(Date(f.lastModified()))))
        val lo = lastOpened(f)
        if (lo != null) extra.addView(row("Last opened", dfm.format(Date(lo))))
        extra.addView(row("Where", whereStr, true))
        col.addView(extra)
        more.setOnClickListener {
            val open = extra.visibility == View.VISIBLE
            extra.visibility = if (open) View.GONE else View.VISIBLE
            more.text = if (open) "Show More" else "Show Less"
        }

        col.addView(hairline(), llp(MATCH, 1).apply { topMargin = dpi(20) })
        val th = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(16), 0, dpi(8)) }
        th.addView(tv("Tags", 20f, Color.BLACK, true), llp(0, WRAP, 1f))
        val cur = tagOf(f)
        val idx = C.tagColors.indexOf(cur)
        th.addView(tv(if (idx >= 0) C.tagNames[idx] else "Add Tags", 15f, C.BLUE).apply {
            setOnClickListener { tagMenu(f, root.width / 2f - dpi(135), root.height / 2f) }
        })
        col.addView(th)

        sheet.addView(sv, llp(MATCH, 0, 1f))
        o.addView(dim, MATCH, MATCH)
        o.addView(sheet, flp(MATCH, sh, Gravity.BOTTOM))
        sheet.translationY = sh.toFloat()
        dim.animate().alpha(1f).setDuration(250).start()
        sheet.animate().translationY(0f).setDuration(420).setInterpolator(IOS_EASE).start()
    }

    fun markup(f: File) {
        val mv = MarkupView(this)
        var bmp: Bitmap? = null
        val done = tv("Done", 17f, C.BLUE, true).apply { gravity = Gravity.CENTER }
        showViewer("Markup", true, done) { v ->
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            v.addView(iv, MATCH, MATCH)
            v.addView(mv, MATCH, MATCH)
            Thread { val b = Thumbs.decode(f, 2048); ui.post { bmp = b; iv.setImageBitmap(b) } }.start()
            val tools = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = rr(0xCC2C2C2E.toInt(), dp(28f))
                setPadding(dpi(16), dpi(10), dpi(16), dpi(10))
            }
            for (c in intArrayOf(0xFFFF3B30.toInt(), 0xFF007AFF.toInt(), 0xFF34C759.toInt(), 0xFFFFCC00.toInt(), Color.WHITE)) {
                tools.addView(View(this).apply {
                    background = oval(c)
                    setOnClickListener { mv.color = c }
                }, llp(dpi(32), dpi(32)).apply { setMargins(dpi(8), 0, dpi(8), 0) })
            }
            tools.addView(tv("Undo", 16f, Color.WHITE).apply {
                setPadding(dpi(12), 0, dpi(4), 0)
                setOnClickListener { mv.undo() }
            })
            v.addView(tools, flp(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = botInset + dpi(24) })
            done.setOnClickListener {
                val b = bmp
                if (b != null && mv.strokes.isNotEmpty()) {
                    val out = mv.render(b, iv.width, iv.height)
                    Thread {
                        try {
                            val e = FileOps.ext(f)
                            val jpg = e == "jpg" || e == "jpeg"
                            val dst = FileOps.unique(f.parentFile!!, f.nameWithoutExtension + " markup." + (if (jpg) e else "png"))
                            FileOutputStream(dst).use { out.compress(if (jpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 95, it) }
                            ui.post { reloadAll(); toast("Markup saved as a copy") }
                        } catch (x: Throwable) { ui.post { toast("Cannot save markup") } }
                    }.start()
                }
                closeViewer()
            }
        }
    }

    fun paste(dir: File) {
        val c = clip ?: return
        if (c.second) clip = null
        bg(if (c.second) "Moved" else "Copied") {
            for (s in c.first) if (c.second) FileOps.move(s, dir) else FileOps.copy(s, dir)
        }
    }

    fun uriOf(f: File): Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)

    fun share(f: File) = shareSheet(f)

    fun shareSystem(f: File, pkg: String? = null) {
        val cur = prefs.getString("shared", "")!!.split("\n").filter { it.isNotBlank() && it != f.path }
        prefs.edit().putString("shared", (listOf(f.path) + cur).take(100).joinToString("\n")).apply()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileOps.ext(f)) ?: "*/*"
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uriOf(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (pkg != null) {
            i.setPackage(pkg)
            try { startActivity(i); return } catch (e: Exception) { i.setPackage(null) }
        }
        startActivity(Intent.createChooser(i, f.name))
    }

    fun shareMail(f: File) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_STREAM, uriOf(f)).putExtra(Intent.EXTRA_SUBJECT, f.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(i) } catch (e: Exception) { toast("No mail app found") }
    }

    fun saveToGallery(f: File, type: String) {
        bg("Saved") {
            val dir = Environment.getExternalStoragePublicDirectory(type)
            dir.mkdirs()
            val dst = FileOps.copy(f, dir)
            android.media.MediaScannerConnection.scanFile(this, arrayOf(dst.path), null, null)
        }
    }

    fun saveToFiles(f: File) {
        bg("Saved to Downloads") {
            val d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            d.mkdirs()
            FileOps.copy(f, d)
        }
    }

    fun sizeLocal(b: Long): String = FileOps.size(b).replace('.', java.text.DecimalFormatSymbols.getInstance().decimalSeparator)

    /** Share sheet persis iOS: baris aplikasi (AirDrop, Messages, Mail, ...) + kartu aksi. */
    fun shareSheet(f: File) {
        if (f.isDirectory) { toast("Compress the folder first"); return }
        val o = overlay ?: return
        hideKb()
        val dim = View(this).apply { setBackgroundColor(0x55000000); alpha = 0f; isClickable = true }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(C.BG)
                cornerRadii = floatArrayOf(dp(22f), dp(22f), dp(22f), dp(22f), 0f, 0f, 0f, 0f)
            }
            clipToOutline = true
            elevation = dp(20f)
        }
        var gone = false
        fun close(after: () -> Unit) {
            if (gone) return
            gone = true
            dim.animate().alpha(0f).setDuration(250).start()
            sheet.animate().translationY(sheet.height.toFloat()).setDuration(300).setInterpolator(IOS_EASE).withEndAction {
                o.removeView(dim); o.removeView(sheet); after()
            }.start()
        }
        dim.tag = Runnable { close {} }
        dim.setOnClickListener { close {} }

        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(18), dpi(14), dpi(16), dpi(14))
            setBackgroundColor(0xF2F9F9F9.toInt())
        }
        head.addView(FileThumbView(this).also { it.bind(f) }, llp(dpi(40), dpi(48)))
        val hc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hc.addView(tv(f.name, 17f, Color.BLACK, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE })
        hc.addView(tv(FileOps.kind(f) + " · " + sizeLocal(f.length()), 15f, C.GRAY))
        head.addView(hc, llp(0, WRAP, 1f).apply { marginStart = dpi(12); marginEnd = dpi(8) })
        head.addView(FrameLayout(this).apply {
            background = oval(0xFFE3E3E8.toInt())
            addView(icon(P.CLOSE, 0xFF6C6C70.toInt(), 16), flp(dpi(16), dpi(16), Gravity.CENTER))
            setOnClickListener { close {} }
        }, llp(dpi(34), dpi(34)))
        sheet.addView(head)
        sheet.addView(hairline(), llp(MATCH, 1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16), dpi(16), dpi(16), botInset + dpi(24))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun appItem(label: String, iconView: View, act: () -> Unit) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dpi(4), 0, dpi(4), 0)
                setOnClickListener { close { act() } }
            }
            cell.addView(iconView, llp(dpi(62), dpi(62)))
            cell.addView(tv(label, 12f, Color.BLACK).apply { gravity = Gravity.CENTER; maxLines = 1; setPadding(0, dpi(6), 0, 0) })
            row.addView(cell, llp(dpi(76), WRAP))
        }
        fun shareIc(kind: Int) = ImageView(this).apply { setImageDrawable(ShareIconDrawable(kind)) }
        appItem("AirDrop", shareIc(0)) { shareSystem(f) }
        appItem("Messages", shareIc(1)) { shareSystem(f, Telephony.Sms.getDefaultSmsPackage(this)) }
        appItem("Mail", shareIc(2)) { shareMail(f) }
        val rnd = object : ViewOutlineProvider() {
            override fun getOutline(v: View, ol: Outline) { ol.setRoundRect(0, 0, v.width, v.height, dp(14f)) }
        }
        val pkgs = listOf("com.whatsapp" to "WhatsApp", "org.telegram.messenger" to "Telegram",
            "com.facebook.orca" to "Messenger", "com.instagram.android" to "Instagram")
        for ((pk, nm) in pkgs) {
            val ic: Drawable? = try { packageManager.getApplicationIcon(pk) } catch (e: Exception) { null }
            if (ic == null) continue
            val iv = ImageView(this).apply { setImageDrawable(ic); outlineProvider = rnd; clipToOutline = true }
            appItem(nm, iv) { shareSystem(f, pk) }
        }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        hs.addView(row)
        content.addView(hs, llp(MATCH, WRAP))

        fun card(items: List<Triple<String, String, () -> Unit>>) {
            val cd = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rr(Color.WHITE, dp(14f))
                clipToOutline = true
            }
            for ((i, item) in items.withIndex()) {
                if (i > 0) cd.addView(hairline(), llp(MATCH, 1).apply { marginStart = dpi(16) })
                val r = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpi(16), 0, dpi(16), 0)
                    background = pressBg()
                    setOnClickListener { close { item.third() } }
                }
                r.addView(tv(item.first, 17f, Color.BLACK), llp(0, WRAP, 1f))
                r.addView(icon(item.second, Color.BLACK, 22))
                cd.addView(r, llp(MATCH, dpi(50)))
            }
            content.addView(cd, llp(MATCH, WRAP).apply { topMargin = dpi(20) })
        }
        card(listOf(Triple("Copy", P.COPY, { clip = Pair(listOf(f), false); toast("Copied") })))
        val e = FileOps.ext(f)
        val rows2 = ArrayList<Triple<String, String, () -> Unit>>()
        if (e in FileOps.VID) rows2.add(Triple("Save Video", P.DOWNLOAD, { saveToGallery(f, Environment.DIRECTORY_MOVIES) }))
        if (e in FileOps.IMG) rows2.add(Triple("Save Image", P.DOWNLOAD, { saveToGallery(f, Environment.DIRECTORY_PICTURES) }))
        rows2.add(Triple("Save to Files", P.FOLDER, { saveToFiles(f) }))
        rows2.add(Triple("Open in…", P.OPENIN, { openWith(f) }))
        card(rows2)

        val sc = ScrollView(this)
        sc.addView(content)
        sheet.addView(sc, llp(MATCH, WRAP))
        sheet.measure(
            View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val hgt = minOf(sheet.measuredHeight, (root.height * 0.88f).toInt())
        o.addView(dim, MATCH, MATCH)
        o.addView(sheet, flp(MATCH, hgt, Gravity.BOTTOM))
        sheet.translationY = hgt.toFloat()
        dim.animate().alpha(1f).setDuration(250).start()
        sheet.animate().translationY(0f).setDuration(420).setInterpolator(IOS_EASE).start()
    }

    val INSTALLABLE = setOf("apk", "apks", "xapk", "apkm")

    fun needInstallPerm() = Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()

    fun openInstallSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) } catch (x: Exception) { toast("Open Settings manually") }
        }
    }

    /** Popup gagal instal ala iOS: OK / Settings. */
    fun unableToInstall(f: File, msg: String) {
        alert("Unable To Install", "\"${f.name}\"\n\n$msg", null, 0, listOf(Btn("OK"), Btn("Settings", 1) { openInstallSettings() }))
    }

    fun install(f: File) {
        if (needInstallPerm()) {
            unableToInstall(f, "Files isn't allowed to install apps yet. Tap Settings and turn on \"Allow from this source\".")
            return
        }
        if (FileOps.ext(f) == "apk") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(f), "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (e: Exception) { unableToInstall(f, "This file can't be installed.") }
        } else openWith(f, true)
    }

    fun openWith(f: File, installer: Boolean = false) {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileOps.ext(f)) ?: "application/octet-stream"
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(f), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), f.name))
        } catch (e: Exception) {
            if (installer) unableToInstall(f, "No app on this phone can install this kind of file.") else toast("No app can open this file")
        }
    }

    fun openFile(f: File) {
        addRecent(f)
        val e = FileOps.ext(f)
        when {
            e in FileOps.IMG -> viewerImage(f)
            e in FileOps.VID -> viewerVideo(f)
            e in FileOps.AUD -> viewerAudio(f)
            e in FileOps.TXT -> viewerText(f)
            else -> openOther(f)
        }
    }

    /** Semua file selain foto/video/audio/teks: popup ubah nama -> Done -> popup Install/Extract/Open. */
    fun openOther(f: File) {
        val dot = f.name.lastIndexOf('.')
        val bak = FileOps.ext(f) == "bak" && dot > 0
        val start = if (bak) dot else 0
        val end = if (bak) f.name.length else if (dot > 0) dot else f.name.length
        alert("Rename", "Change the name or keep it, then tap Done.", f.name, end, listOf(Btn("Cancel"), Btn("Done", 1) { n ->
            val nn = n.trim().replace("/", "_")
            if (nn.isEmpty()) { toast("A name is required"); openOther(f) }
            else try {
                var d = f
                if (nn != f.name) {
                    val t = File(f.parentFile, nn)
                    if (t.exists()) throw Exception("Name already taken")
                    if (!f.renameTo(t)) throw Exception("Cannot rename")
                    val tg = prefs.getInt("t:" + f.path, 0)
                    if (tg != 0) prefs.edit().remove("t:" + f.path).putInt("t:" + t.path, tg).apply()
                    d = t
                }
                reloadAll()
                followUp(d, true)
            } catch (e: Exception) { toast(e.message ?: "Cannot rename") }
        }), start)
    }

    fun followUp(d: File, open: Boolean) {
        addRecent(d)
        val e = FileOps.ext(d)
        val trash = Btn("Delete", 2) { bg("Moved to Recently Deleted") { trashIt(d) } }
        when {
            e in INSTALLABLE -> chooseAction(d)
            e in FileOps.IMG || e in FileOps.VID || e in FileOps.AUD || e in FileOps.TXT ->
                if (open) openFile(d) else toast("Downloaded")
            e == "zip" -> actionSheet("Choose Action", "Choose desired action to perform with this.",
                listOf(Btn("Open") { openInApp(d) }, Btn("Extract") { extract(d) }, Btn("Share File") { share(d) }, trash))
            else -> actionSheet("Choose Action", "Choose desired action to perform with this.",
                listOf(Btn("Open") { openInApp(d) }, Btn("Open in…") { openWith(d) }, Btn("Share File") { share(d) }, trash))
        }
    }

    /** Sheet aksi dari bawah ala iOS: kartu aksi + kartu Cancel terpisah. */
    fun actionSheet(title: String, sub: String?, buttons: List<Btn>, cancel: String = "Cancel", accent: Int = 0xFF2F9DF0.toInt(), big: Boolean = true) {
        val o = overlay ?: return
        hideKb()
        val dim = View(this).apply { setBackgroundColor(0x55000000); alpha = 0f; isClickable = true }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(8), 0, dpi(8), botInset + dpi(8))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rr(0xF2F2F2F2.toInt(), dp(14f))
            clipToOutline = true
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(16), dpi(18), dpi(16), dpi(if (big) 22 else 16))
        }
        head.addView(tv(title, 13f, 0xFF8A8A8E.toInt(), true).apply { gravity = Gravity.CENTER })
        if (sub != null) head.addView(tv(sub, 13f, 0xFF8A8A8E.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dpi(if (big) 14 else 8), 0, 0)
        })
        card.addView(head)
        val rh = dpi(if (big) 56 else 50)
        val ts = if (big) 20f else 17f
        var gone = false
        fun dismiss(after: () -> Unit) {
            if (gone) return
            gone = true
            dim.animate().alpha(0f).setDuration(220).start()
            wrap.animate().translationY(wrap.height.toFloat()).setDuration(260).setInterpolator(IOS_EASE).withEndAction {
                o.removeView(dim); o.removeView(wrap); after()
            }.start()
        }
        for (b in buttons) {
            card.addView(hairline(), llp(MATCH, 1))
            card.addView(tv(b.label, ts, if (b.style == 2) C.RED else accent, b.style == 1).apply {
                gravity = Gravity.CENTER
                background = pressBg()
                setOnClickListener { dismiss { b.action("") } }
            }, llp(MATCH, rh))
        }
        val cc = tv(cancel, ts, accent, true).apply {
            gravity = Gravity.CENTER
            background = rr(Color.WHITE, dp(14f))
            setOnClickListener { dismiss {} }
        }
        wrap.addView(card, llp(MATCH, WRAP))
        wrap.addView(cc, llp(MATCH, rh).apply { topMargin = dpi(8) })
        dim.tag = Runnable { dismiss {} }
        dim.setOnClickListener { dismiss {} }
        o.addView(dim, MATCH, MATCH)
        o.addView(wrap, flp(MATCH, WRAP, Gravity.BOTTOM))
        wrap.translationY = root.height.toFloat()
        dim.animate().alpha(1f).setDuration(220).start()
        wrap.animate().translationY(0f).setDuration(380).setInterpolator(IOS_EASE).start()
    }

    fun chooseAction(d: File) {
        actionSheet("Choose Action", "Choose desired action to perform with this.", listOf(
            Btn("Install") { confirmInstall(d) },
            Btn("Custom Install") { customInstall(d) },
            Btn("Share App") { share(d) },
            Btn("Delete", 2) { bg("Moved to Recently Deleted") { trashIt(d) } }
        ))
    }

    fun customInstall(d: File) {
        val ts = SimpleDateFormat("d MMM yyyy 'at' HH:mm:ss", Locale.getDefault()).format(Date(d.lastModified()))
        val sz = String.format(Locale.US, "%.2fMB", d.length() / 1048576.0)
        actionSheet(d.name, "$ts  $sz", listOf(
            Btn("signature") { showSignature(d) },
            Btn("Unsigned install") { confirmInstall(d) },
            Btn("delete", 2) { bg("Moved to Recently Deleted") { trashIt(d) } }
        ), "cancel", 0xFF3AAFCB.toInt(), false)
    }

    /** Popup "would like to install" persis iOS. */
    fun confirmInstall(d: File) {
        val name = appLabel(d)
        alert("\u201CFiles\u201D would like to install \u201C$name\u201D", null, null, 0,
            listOf(Btn("Cancel", 1), Btn("Install") { install(d) }))
    }

    fun appLabel(d: File): String {
        try {
            val pm = packageManager
            val ai = pm.getPackageArchiveInfo(d.path, 0)?.applicationInfo
            if (ai != null) {
                ai.sourceDir = d.path
                ai.publicSourceDir = d.path
                val l = ai.loadLabel(pm).toString()
                if (l.isNotBlank()) return l
            }
        } catch (e: Throwable) {}
        return d.nameWithoutExtension
    }

    @Suppress("DEPRECATION")
    fun sigInfo(path: String): String {
        val pm = packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pi = pm.getPackageArchiveInfo(path, flags) ?: return "Not a valid app package. No signature found."
        val sigs = if (Build.VERSION.SDK_INT >= 28) pi.signingInfo?.apkContentsSigners else pi.signatures
        if (sigs == null || sigs.isEmpty()) return "${pi.packageName}\nUnsigned"
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        return "${pi.packageName}\nVersion ${pi.versionName}\nSigned\nSHA-256 $sha…"
    }

    fun showSignature(d: File) {
        Thread {
            val txt = try { sigInfo(d.path) } catch (e: Throwable) { "Cannot read signature." }
            ui.post { alert("Signature", txt, null, 0, listOf(Btn("OK", 1))) }
        }.start()
    }

    // ---------- buka file di dalam app ----------
    fun quickLook(f: File) {
        addRecent(f)
        val e = FileOps.ext(f)
        when {
            e in FileOps.IMG -> viewerImage(f)
            e in FileOps.VID -> viewerVideo(f)
            e in FileOps.AUD -> viewerAudio(f)
            e in FileOps.TXT -> viewerText(f)
            else -> openInApp(f)
        }
    }

    fun openInApp(f: File) {
        val e = FileOps.ext(f)
        when {
            e in FileOps.IMG || e in FileOps.VID || e in FileOps.AUD || e in FileOps.TXT -> quickLook(f)
            e == "pdf" -> viewerPdf(f)
            e == "zip" || e == "jar" || e == "apk" || e == "apks" || e == "xapk" || e == "apkm" || e == "docx" || e == "xlsx" || e == "pptx" -> viewerArchive(f)
            e == "ttf" || e == "otf" -> viewerFont(f)
            else -> viewerGeneric(f)
        }
    }

    fun viewerFont(f: File) {
        showViewer(f.name, false, shareBtn(f, false)) { v ->
            val sv = ScrollView(this).apply {
                clipToPadding = false
                setPadding(dpi(20), topInset + dpi(72), dpi(20), botInset + dpi(24))
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val tf = try { Typeface.createFromFile(f) } catch (e: Exception) { null }
            if (tf == null) col.addView(tv("This font can't be previewed.", 16f, C.GRAY))
            else {
                col.addView(tv(f.nameWithoutExtension, 24f, Color.BLACK).apply { typeface = tf })
                for (s in floatArrayOf(40f, 30f, 22f, 17f, 13f)) {
                    col.addView(tv("ABCDEFGHIJKLM\nNOPQRSTUVWXYZ\nabcdefghijklm\nnopqrstuvwxyz\n0123456789 !?&@#", s, Color.BLACK).apply {
                        typeface = tf
                        setPadding(0, dpi(16), 0, 0)
                    })
                }
            }
            sv.addView(col)
            v.addView(sv, MATCH, MATCH)
        }
    }

    fun viewerArchive(f: File) {
        val extractBtn = tv("Extract", 17f, C.BLUE, true).apply { gravity = Gravity.CENTER }
        showViewer(f.name, false, extractBtn) { v ->
            extractBtn.setOnClickListener { closeViewer(); ui.postDelayed({ extract(f) }, 300) }
            val lv = ListView(this).apply {
                divider = null
                clipToPadding = false
                setPadding(0, topInset + dpi(64), 0, botInset + dpi(16))
            }
            v.addView(lv, MATCH, MATCH)
            Thread {
                val rows = ArrayList<Pair<String, String>>()
                var total = 0L
                try {
                    java.util.zip.ZipFile(f).use { z ->
                        val en = z.entries()
                        while (en.hasMoreElements() && rows.size < 3000) {
                            val x = en.nextElement()
                            if (!x.isDirectory && x.size > 0) total += x.size
                            rows.add(Pair(x.name, if (x.isDirectory) "Folder" else FileOps.size(x.size)))
                        }
                    }
                } catch (x: Exception) {
                    rows.add(Pair("This archive can't be listed (only ZIP-based files).", ""))
                }
                ui.post {
                    lv.addHeaderView(tv("${rows.size} items · ${FileOps.size(total)} · tap a file to peek", 13f, C.GRAY).apply {
                        setPadding(dpi(16), 0, dpi(16), dpi(8))
                    }, null, false)
                    lv.setOnItemClickListener { _, _, pos, _ ->
                        val i = pos - lv.headerViewsCount
                        if (i >= 0 && i < rows.size) peekEntry(f, rows[i].first)
                    }
                    lv.adapter = object : BaseAdapter() {
                        override fun getCount() = rows.size
                        override fun getItem(p: Int): Any = rows[p]
                        override fun getItemId(p: Int) = p.toLong()
                        override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                            val row = (cv as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(dpi(16), dpi(10), dpi(16), dpi(10))
                                addView(tv("", 15f, Color.BLACK).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE }, llp(0, WRAP, 1f))
                                addView(tv("", 13f, C.GRAY))
                            }
                            (row.getChildAt(0) as TextView).text = rows[p].first
                            (row.getChildAt(1) as TextView).text = rows[p].second
                            return row
                        }
                    }
                }
            }.start()
        }
    }

    /** Intip satu file di dalam zip tanpa mengekstrak semuanya. */
    fun peekEntry(zip: File, name: String) {
        if (name.endsWith("/")) return
        Thread {
            try {
                val dir = File(cacheDir, "peek").also { it.mkdirs() }
                val out = File(dir, name.substringAfterLast('/'))
                java.util.zip.ZipFile(zip).use { z ->
                    val en = z.getEntry(name) ?: throw Exception("Missing entry")
                    z.getInputStream(en).use { ins -> FileOutputStream(out).use { ins.copyTo(it) } }
                }
                ui.post { closeViewer(); ui.postDelayed({ quickLook(out) }, 330) }
            } catch (e: Throwable) {
                ui.post { toast("Cannot open this item") }
            }
        }.start()
    }

    fun viewerPdf(f: File) {
        showViewer(f.name, true, shareBtn(f, true)) { v ->
            val sv = ScrollView(this).apply {
                clipToPadding = false
                setPadding(0, topInset + dpi(64), 0, botInset + dpi(16))
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            sv.addView(col)
            v.addView(sv, MATCH, MATCH)
            var closed = false
            viewerCleanup = { closed = true }
            Thread {
                try {
                    val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = PdfRenderer(pfd)
                    val wpx = resources.displayMetrics.widthPixels
                    for (i in 0 until minOf(r.pageCount, 40)) {
                        if (closed) break
                        val page = r.openPage(i)
                        val bh = (wpx.toFloat() * page.height / page.width).toInt()
                        val bmp = Bitmap.createBitmap(wpx, bh, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        ui.post {
                            if (!closed) col.addView(ImageView(this).apply {
                                setImageBitmap(bmp)
                                adjustViewBounds = true
                            }, llp(MATCH, WRAP).apply { bottomMargin = dpi(8) })
                        }
                    }
                    r.close()
                    pfd.close()
                } catch (e: Throwable) {
                    ui.post { toast("Cannot open this PDF") }
                }
            }.start()
        }
    }

    /** File apa pun: teks kalau terbaca sebagai teks, selain itu tampilan hex. */
    fun viewerGeneric(f: File) {
        Thread {
            val head = ByteArray(4096)
            var n = 0
            try { f.inputStream().use { n = it.read(head).coerceAtLeast(0) } } catch (e: Exception) {}
            var bad = 0
            for (i in 0 until n) {
                val b = head[i].toInt() and 0xFF
                if (b == 0 || b < 9 || b in 14..31) bad++
            }
            val text = n > 0 && bad * 100 / n < 2
            ui.post {
                if (text && f.length() <= 2_000_000) viewerText(f) else viewerHex(f, head, n)
            }
        }.start()
    }

    fun viewerHex(f: File, head: ByteArray, n: Int) {
        showViewer(f.name, false, shareBtn(f, false)) { v ->
            val sv = ScrollView(this).apply {
                clipToPadding = false
                setPadding(dpi(16), topInset + dpi(72), dpi(16), botInset + dpi(24))
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(tv(FileOps.kind(f) + " - " + FileOps.size(f.length()), 15f, C.GRAY).apply { setPadding(0, 0, 0, dpi(12)) })
            val sb = StringBuilder()
            var off = 0
            while (off < n) {
                sb.append(String.format("%06X  ", off))
                val asc = StringBuilder()
                for (i in 0 until 8) {
                    if (off + i < n) {
                        val b = head[off + i].toInt() and 0xFF
                        sb.append(String.format("%02X ", b))
                        asc.append(if (b in 32..126) b.toChar() else '.')
                    } else sb.append("   ")
                }
                sb.append(" ").append(asc).append("\n")
                off += 8
            }
            col.addView(tv(if (n == 0) "Empty file" else sb.toString(), 12f, Color.BLACK).apply { typeface = Typeface.MONOSPACE })
            if (f.length() > n) col.addView(tv("Showing the first $n bytes.", 13f, C.GRAY).apply { setPadding(0, dpi(12), 0, 0) })
            sv.addView(col)
            v.addView(sv, MATCH, MATCH)
        }
    }

    // ---------- recents / trash ----------
    fun addRecent(f: File) {
        val cur = prefs.getString("recent", "")!!.split("\n").filter { it.isNotBlank() && !it.endsWith("|" + f.path) }
        val n = (listOf(System.currentTimeMillis().toString() + "|" + f.path) + cur).take(100)
        prefs.edit().putString("recent", n.joinToString("\n")).apply()
    }

    fun loadRecents(): List<File> {
        val out = ArrayList<File>()
        val ts = HashMap<String, Long>()
        for (line in prefs.getString("recent", "")!!.split("\n")) {
            val i = line.indexOf('|')
            if (i < 0) continue
            val t = line.substring(0, i).toLongOrNull() ?: continue
            val f = File(line.substring(i + 1))
            if (f.exists()) { out.add(f); ts[f.path] = t }
        }
        recentTs.clear()
        recentTs.putAll(ts)
        return out
    }

    fun trashIt(f: File) {
        val dst = FileOps.move(f, trashDir)
        prefs.edit().putString("orig:" + dst.path, f.parent ?: "").putLong("del:" + dst.path, System.currentTimeMillis()).apply()
    }

    fun recover(f: File) {
        var dir = File(prefs.getString("orig:" + f.path, "") ?: "")
        if (!dir.isDirectory) dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).also { it.mkdirs() }
        FileOps.move(f, dir)
        prefs.edit().remove("orig:" + f.path).remove("del:" + f.path).apply()
    }

    fun purgeTrash() {
        Thread {
            val now = System.currentTimeMillis()
            trashDir.listFiles()?.forEach {
                val t = prefs.getLong("del:" + it.path, 0L)
                if (t > 0 && now - t > 30L * 24 * 3600 * 1000) {
                    it.deleteRecursively()
                    prefs.edit().remove("del:" + it.path).remove("orig:" + it.path).apply()
                }
            }
        }.start()
    }

    fun trashMenu(f: File, x: Float, y: Float) {
        val items = listOf(
            Mi("Recover", P.UNZIP) { bg("Recovered") { recover(f) } },
            Mi("Delete Now", P.TRASH, destructive = true) { confirmPurge(listOf(f)) }
        )
        showMenu(x, y, null, items)
    }

    fun confirmPurge(files: List<File>) {
        if (files.isEmpty()) return
        val title = if (files.size == 1) "Delete \"${files[0].name}\"?" else "Delete ${files.size} items?"
        alert(title, "This can't be undone.", null, 0, listOf(Btn("Cancel"), Btn("Delete", 2) {
            bg("Deleted") {
                for (x in files) {
                    x.deleteRecursively()
                    prefs.edit().remove("orig:" + x.path).remove("del:" + x.path).remove("t:" + x.path).apply()
                }
            }
        }))
    }

    // ---------- download / save ----------
    fun guessName(url: String): String {
        val p = try { Uri.parse(url).lastPathSegment } catch (e: Exception) { null }
        val n = (p ?: "").substringBefore('?').trim()
        return if (n.isBlank()) "download" else n
    }

    fun finalName(typed: String, orig: String): String {
        val n = typed.trim().replace("/", "_")
        val oe = orig.substringAfterLast('.', "")
        return if (!n.contains('.') && oe.isNotEmpty() && oe.length <= 5) "$n.$oe" else n
    }

    fun askUrl(dir: File) {
        alert("Download from URL", "Enter the link of the file", "https://", 8, listOf(Btn("Cancel"), Btn("Next", 1) { n ->
            val u = n.trim()
            if (u.startsWith("http://") || u.startsWith("https://")) askDownload(u, dir) else toast("Invalid link")
        }))
    }

    fun askDownload(url: String, dir: File) {
        val orig = guessName(url)
        val sel = if (orig.contains('.')) orig.lastIndexOf('.') else orig.length
        alert("Download", "Give this file a name before it downloads.", orig, sel, listOf(Btn("Cancel"), Btn("Done", 1) { n ->
            if (n.isBlank()) { toast("A name is required"); askDownload(url, dir) }
            else startDownload(url, dir, finalName(n, orig))
        }))
    }

    fun progressPill(m: String): TextView {
        val t = tv(m, 14f, Color.WHITE).apply {
            background = rr(0xE62C2C2E.toInt(), dp(20f))
            setPadding(dpi(18), dpi(10), dpi(18), dpi(10))
            elevation = dp(6f)
        }
        overlay?.addView(t, flp(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = botInset + dpi(100) })
        return t
    }

    fun removePill(t: View) { overlay?.removeView(t) }

    fun openConn(url: String): HttpURLConnection {
        var u = URL(url)
        var n = 0
        while (true) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Files/1.0")
            val code = c.responseCode
            if (code in 301..303 || code == 307 || code == 308) {
                val loc = c.getHeaderField("Location") ?: throw Exception("Bad redirect")
                u = URL(u, loc)
                c.disconnect()
                if (++n > 6) throw Exception("Too many redirects")
                continue
            }
            if (code !in 200..299) throw Exception("HTTP $code")
            return c
        }
    }

    fun startDownload(url: String, dir: File, name: String) {
        val pill = progressPill("Downloading…")
        Thread {
            val tmp = File(dir, ".$name.part")
            try {
                dir.mkdirs()
                val dst = FileOps.unique(dir, name)
                val c = openConn(url)
                val total = c.contentLengthLong
                var got = 0L
                var last = 0L
                c.inputStream.use { ins ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val r = ins.read(buf)
                            if (r < 0) break
                            out.write(buf, 0, r)
                            got += r
                            val now = System.currentTimeMillis()
                            if (now - last > 300) {
                                last = now
                                val txt = if (total > 0) "Downloading ${got * 100 / total}%" else "Downloading ${FileOps.size(got)}"
                                ui.post { pill.text = txt }
                            }
                        }
                    }
                }
                if (!tmp.renameTo(dst)) throw Exception("Cannot save file")
                ui.post { removePill(pill); finishSave(dst) }
            } catch (e: Throwable) {
                tmp.delete()
                ui.post { removePill(pill); toast("Download failed: " + (e.message ?: "error")) }
            }
        }.start()
    }

    fun finishSave(dst: File) {
        reloadAll()
        followUp(dst, false)
    }

    fun consumeShare() {
        val i = pendingShare ?: return
        pendingShare = null
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        @Suppress("DEPRECATION")
        val stream: Uri? = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (stream != null) { askSave(stream, dl); return }
        val txt = i.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val m = Regex("https?://\\S+").find(txt)
        if (m != null) askDownload(m.value, dl) else toast("Nothing to download")
    }

    fun askSave(u: Uri, dir: File) {
        var orig = u.lastPathSegment ?: "file"
        try {
            contentResolver.query(u, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ix >= 0) orig = c.getString(ix) ?: orig
                }
            }
        } catch (e: Exception) {}
        val o = orig
        val sel = if (o.contains('.')) o.lastIndexOf('.') else o.length
        alert("Save to Files", "Give this file a name.", o, sel, listOf(Btn("Cancel"), Btn("Done", 1) { n ->
            if (n.isBlank()) { toast("A name is required"); askSave(u, dir) }
            else {
                val name = finalName(n, o)
                val pill = progressPill("Saving…")
                Thread {
                    try {
                        dir.mkdirs()
                        val dst = FileOps.unique(dir, name)
                        val ins = contentResolver.openInputStream(u) ?: throw Exception("Cannot read file")
                        ins.use { s -> FileOutputStream(dst).use { s.copyTo(it) } }
                        ui.post { removePill(pill); finishSave(dst) }
                    } catch (e: Throwable) {
                        ui.post { removePill(pill); toast("Failed: " + (e.message ?: "error")) }
                    }
                }.start()
            }
        }))
    }

    // ---------- viewers ----------
    fun showViewer(title: String, dark: Boolean, right: View?, build: (FrameLayout) -> Unit) {
        val o = overlay ?: return
        if (viewerView != null) return
        hideKb()
        val v = FrameLayout(this).apply { setBackgroundColor(if (dark) Color.BLACK else Color.WHITE); isClickable = true }
        build(v)
        val bar = FrameLayout(this).apply {
            setPadding(dpi(12), topInset + dpi(8), dpi(12), dpi(8))
            setBackgroundColor(if (dark) 0x66000000 else 0xF0FFFFFF.toInt())
        }
        bar.addView(circle(P.BACK, dark) { closeViewer() }, flp(dpi(40), dpi(40), Gravity.START))
        bar.addView(tv(title, 17f, if (dark) Color.WHITE else Color.BLACK, true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE; maxWidth = dpi(200); gravity = Gravity.CENTER
        }, flp(WRAP, dpi(40), Gravity.CENTER_HORIZONTAL))
        if (right != null) bar.addView(right, flp(if (right is TextView) WRAP else dpi(40), dpi(40), Gravity.END))
        v.addView(bar, flp(MATCH, WRAP, Gravity.TOP))
        v.tag = Runnable { closeViewer() }
        o.addView(v, MATCH, MATCH)
        viewerView = v
        v.alpha = 0f
        v.scaleX = 0.92f
        v.scaleY = 0.92f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(IOS_EASE).start()
    }

    fun closeViewer() {
        val v = viewerView ?: return
        viewerView = null
        viewerCleanup?.invoke()
        viewerCleanup = null
        hideKb()
        v.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(220).withEndAction { overlay?.removeView(v) }.start()
    }

    fun shareBtn(f: File, dark: Boolean) = circle(P.SHARE, dark) { share(f) }

    fun viewerImage(f: File) {
        showViewer(f.name, true, shareBtn(f, true)) { v ->
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            v.addView(iv, MATCH, MATCH)
            Thread { val b = Thumbs.decode(f, 2048); ui.post { iv.setImageBitmap(b) } }.start()
            val sd = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: ScaleGestureDetector): Boolean {
                    val s = (iv.scaleX * d.scaleFactor).coerceIn(1f, 6f)
                    iv.scaleX = s; iv.scaleY = s
                    return true
                }
            })
            val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val s = if (iv.scaleX > 1.1f) 1f else 2.5f
                    iv.animate().scaleX(s).scaleY(s).translationX(0f).translationY(0f).setDuration(250).setInterpolator(IOS_EASE).start()
                    return true
                }

                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                    if (iv.scaleX > 1f) { iv.translationX -= dx; iv.translationY -= dy }
                    return true
                }
            })
            iv.setOnTouchListener { _, e -> sd.onTouchEvent(e); gd.onTouchEvent(e); true }
        }
    }

    fun fmt(ms: Int): String {
        val s = ms / 1000
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    fun mediaControls(pl: Pl): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rr(0xCC2C2C2E.toInt(), dp(28f))
            setPadding(dpi(20), dpi(14), dpi(20), dpi(16))
        }
        val white = ColorStateList.valueOf(Color.WHITE)
        val sb = SeekBar(this).apply {
            progressTintList = white
            thumbTintList = white
            progressBackgroundTintList = ColorStateList.valueOf(0x55FFFFFF)
        }
        val cur = tv("0:00", 12f, 0xFFAEAEB2.toInt())
        val tot = tv("0:00", 12f, 0xFFAEAEB2.toInt())
        val times = LinearLayout(this)
        times.addView(cur, llp(0, WRAP, 1f))
        times.addView(tot)
        val play = ImageView(this).apply {
            background = oval(Color.WHITE)
            setPadding(dpi(16), dpi(16), dpi(16), dpi(16))
        }
        panel.addView(sb)
        panel.addView(times)
        panel.addView(play, llp(dpi(60), dpi(60)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dpi(8) })
        var seeking = false
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) try { pl.seek(p) } catch (e: Exception) {} }
            override fun onStartTrackingTouch(s: SeekBar?) { seeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) { seeking = false }
        })
        play.setOnClickListener { try { if (pl.playing()) pl.pause() else pl.play() } catch (e: Exception) {} }
        val tick = object : Runnable {
            override fun run() {
                if (!panel.isAttachedToWindow) return
                try {
                    val d = pl.dur()
                    if (d > 0) { sb.max = d; if (!seeking) sb.progress = pl.pos() }
                    cur.text = fmt(pl.pos())
                    tot.text = fmt(d)
                    play.setImageDrawable(IconDrawable(if (pl.playing()) P.PAUSE else P.PLAY, Color.BLACK, false))
                } catch (e: Exception) { return }
                ui.postDelayed(this, 250)
            }
        }
        ui.post(tick)
        return panel
    }

    fun viewerVideo(f: File) {
        showViewer(f.name, true, shareBtn(f, true)) { v ->
            val vv = VideoView(this)
            v.addView(vv, flp(MATCH, MATCH, Gravity.CENTER))
            vv.setVideoPath(f.absolutePath)
            vv.setOnPreparedListener { it.isLooping = false; vv.start() }
            viewerCleanup = { vv.stopPlayback() }
            val pl = object : Pl {
                override fun playing() = vv.isPlaying
                override fun dur() = vv.duration
                override fun pos() = vv.currentPosition
                override fun seek(ms: Int) = vv.seekTo(ms)
                override fun play() = vv.start()
                override fun pause() = vv.pause()
            }
            v.addView(mediaControls(pl), flp(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(dpi(16), 0, dpi(16), botInset + dpi(16)) })
        }
    }

    fun viewerAudio(f: File) {
        showViewer(f.name, true, shareBtn(f, true)) { v ->
            v.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF3A3A5C.toInt(), Color.BLACK))
            val mp = MediaPlayer()
            var ready = false
            try {
                mp.setDataSource(f.absolutePath)
                mp.setOnPreparedListener { ready = true; it.start() }
                mp.prepareAsync()
            } catch (e: Exception) { toast("Cannot play this file") }
            viewerCleanup = { ready = false; try { mp.release() } catch (e: Exception) {} }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            val art = FrameLayout(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFFF5F6D.toInt(), 0xFFAF52DE.toInt())).apply { cornerRadius = dp(28f) }
                elevation = dp(16f)
            }
            art.addView(icon(P.NOTE, Color.WHITE, 96, true, 1.2f), flp(dpi(96), dpi(96), Gravity.CENTER))
            col.addView(art, llp(dpi(240), dpi(240)))
            col.addView(tv(f.nameWithoutExtension, 20f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(dpi(24), dpi(28), dpi(24), dpi(4)); maxLines = 2 })
            col.addView(tv(FileOps.ext(f).uppercase() + " • " + FileOps.size(f.length()), 14f, 0xFFAEAEB2.toInt()))
            v.addView(col, flp(MATCH, WRAP, Gravity.CENTER).apply { bottomMargin = dpi(80) })
            val pl = object : Pl {
                override fun playing() = ready && mp.isPlaying
                override fun dur() = if (ready) mp.duration else 0
                override fun pos() = if (ready) mp.currentPosition else 0
                override fun seek(ms: Int) { if (ready) mp.seekTo(ms) }
                override fun play() { if (ready) mp.start() }
                override fun pause() { if (ready) mp.pause() }
            }
            v.addView(mediaControls(pl), flp(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(dpi(16), 0, dpi(16), botInset + dpi(16)) })
        }
    }

    fun viewerText(f: File) {
        if (f.length() > 2_000_000) { toast("File is too large to open"); return }
        val save = tv("Save", 17f, C.BLUE, true).apply { gravity = Gravity.CENTER; alpha = 0.4f }
        showViewer(f.name, false, save) { v ->
            val sv = ScrollView(this).apply {
                clipToPadding = false
                isFillViewport = true
                setPadding(dpi(16), topInset + dpi(64), dpi(16), botInset + dpi(24))
            }
            val et = IosEditText(this).apply {
                textSize = 15f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.TOP
                setTextColor(Color.BLACK)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            sv.addView(et, MATCH, WRAP)
            v.addView(sv, MATCH, MATCH)
            var dirty = false
            Thread {
                val t = try { f.readText() } catch (e: Exception) { "" }
                ui.post {
                    et.setText(t)
                    et.onChange { dirty = true; save.alpha = 1f }
                }
            }.start()
            save.setOnClickListener {
                if (dirty) try {
                    f.writeText(et.text.toString())
                    dirty = false
                    save.alpha = 0.4f
                    hideKb()
                    toast("Saved")
                    reloadAll()
                } catch (e: Exception) { toast("Cannot save") }
            }
        }
    }
}
