package com.feng.filesios27

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.PathParser
import java.io.File
import java.util.concurrent.Executors

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object C {
    val BLUE = 0xFF007AFF.toInt()
    var dark = false
    var textScale = 1f
    val SETCARD: Int get() = if (dark) 0xFF1C1C1E.toInt() else 0xFFE9E9EB.toInt()
    val BG: Int get() = if (dark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
    val TEXT: Int get() = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    val CARD: Int get() = if (dark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
    val SEP: Int get() = if (dark) 0x33FFFFFF else 0x1F000000
    val SEP2: Int get() = if (dark) 0x26FFFFFF else 0x1F000000
    val PRESS: Int get() = if (dark) 0x33FFFFFF else 0x1F000000
    val SHEET: Int get() = if (dark) 0xF22C2C2E.toInt() else 0xF2F2F2F2.toInt()
    val HEAD: Int get() = if (dark) 0xF21C1C1E.toInt() else 0xF2F9F9F9.toInt()
    val CHIP: Int get() = if (dark) 0xFF3A3A3C.toInt() else 0xFFE3E3E8.toInt()
    val GRAY = 0xFF8E8E93.toInt()
    val RED = 0xFFFF3B30.toInt()
    val tagColors = intArrayOf(
        0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
        0xFF007AFF.toInt(), 0xFFAF52DE.toInt(), 0xFF8E8E93.toInt()
    )
    val tagNames = arrayOf("Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Gray")
}

/** Kurva animasi ala iOS (cepat di awal, melambat halus). */
val IOS_EASE = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

fun Context.dp(v: Float) = v * resources.displayMetrics.density
fun Context.dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

fun rr(col: Int, r: Float) = GradientDrawable().apply { setColor(col); cornerRadius = r }
fun oval(col: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col) }
fun pressBg(): Drawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(C.PRESS))
    addState(intArrayOf(), ColorDrawable(0))
}

fun EditText.onChange(cb: () -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { cb() }
    })
}

/** Path ikon garis (24x24), gaya SF Symbols. */
object P {
    const val BACK = "M15,5L8,12L15,19"
    const val CHEV = "M9,5L16,12L9,19"
    const val SEARCH = "M10.5,4a6.5,6.5 0 1,0 0,13a6.5,6.5 0 1,0 0,-13 M15.5,15.5L20,20"
    const val MIC = "M12,3.2a2.8,2.8 0 0,0 -2.8,2.8v5.2a2.8,2.8 0 0,0 5.6,0V6a2.8,2.8 0 0,0 -2.8,-2.8z M6,11a6,6 0 0,0 12,0 M12,17v3.5"
    const val MORE = "M6,12L6.01,12M12,12L12.01,12M18,12L18.01,12"
    const val COPY = "M8,8h11v12h-11z M5,16V4h11"
    const val FOLDER = "M5,6.5h3.4a1,1 0 0,1 .8,.4l.9,1.2a1,1 0 0,0 .8,.4H19a1.5,1.5 0 0,1 1.5,1.5v8a1.5,1.5 0 0,1 -1.5,1.5H5a1.5,1.5 0 0,1 -1.5,-1.5V8A1.5,1.5 0 0,1 5,6.5z"
    const val NEWFOLDER = "M5,6.5h3.4a1,1 0 0,1 .8,.4l.9,1.2a1,1 0 0,0 .8,.4H19a1.5,1.5 0 0,1 1.5,1.5v8a1.5,1.5 0 0,1 -1.5,1.5H5a1.5,1.5 0 0,1 -1.5,-1.5V8A1.5,1.5 0 0,1 5,6.5z M12,11.5v5 M9.5,14h5"
    const val SHARE = "M12,15V4 M8,8l4,-4l4,4 M6,11H5v9h14v-9h-1"
    const val TRASH = "M4.5,7h15 M9.5,7V5.2a1,1 0 0,1 1,-1h3a1,1 0 0,1 1,1V7 M6.5,7l.8,11.5a2,2 0 0,0 2,1.9h5.4a2,2 0 0,0 2,-1.9L17.5,7 M10,10.5v6 M14,10.5v6"
    const val INFO = "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18 M12,11v6 M12,7.5L12.01,7.5"
    const val PENCIL = "M4,20l1,-4l11,-11l3,3l-11,11z"
    const val ARCHIVE = "M4,5h16v4h-16z M5,9v10h14v-10 M10,13h4"
    const val UNZIP = "M4,5h16v4h-16z M5,9v10h14v-10 M12,17v-6 M9,14l3,-3l3,3"
    const val DUP = "M9,9h11v11h-11z M4,15V4h11 M14.5,12v5 M12,14.5h5"
    const val EYE = "M2,12c3,-5 7,-7 10,-7s7,2 10,7c-3,5 -7,7 -10,7s-7,-2 -10,-7z M12,9a3,3 0 1,0 0.01,0"
    const val TAG = "M3,12V4h8l10,10l-8,8z M7,8L7.01,8"
    const val DOWNLOAD = "M12,4v11 M7,11l5,5l5,-5 M5,20h14"
    const val CLOCK = "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18 M12,7v5l3,2"
    const val PEOPLE = "M9,8a3,3 0 1,0 0.01,0 M3,20c0,-4 3,-6 6,-6s6,2 6,6 M17,8a2.5,2.5 0 1,0 0.01,0 M17,14c3,0 4,2 4,5"
    const val PHONE = "M8.5,2.8h7a2,2 0 0,1 2,2v14.4a2,2 0 0,1 -2,2h-7a2,2 0 0,1 -2,-2V4.8a2,2 0 0,1 2,-2z M10.7,18.2h2.6"
    const val CHECK = "M5,12.5L10,17.5L19,7"
    const val GRID = "M4,4h6v6h-6z M14,4h6v6h-6z M4,14h6v6h-6z M14,14h6v6h-6z"
    const val LIST = "M4,6h16 M4,12h16 M4,18h16"
    const val PLAY = "M8.2,5.4a1,1 0 0,0 -1.5,.9v11.4a1,1 0 0,0 1.5,.9l9.5,-5.7a1,1 0 0,0 0,-1.7z"
    const val PAUSE = "M7,3.5h2.2a1.2,1.2 0 0,1 1.2,1.2v14.6a1.2,1.2 0 0,1 -1.2,1.2H7a1.2,1.2 0 0,1 -1.2,-1.2V4.7A1.2,1.2 0 0,1 7,3.5z M14.8,3.5h2.2a1.2,1.2 0 0,1 1.2,1.2v14.6a1.2,1.2 0 0,1 -1.2,1.2h-2.2a1.2,1.2 0 0,1 -1.2,-1.2V4.7a1.2,1.2 0 0,1 1.2,-1.2z"
    const val SHAREDFOLDER = "M5,6.5h3.4a1,1 0 0,1 .8,.4l.9,1.2a1,1 0 0,0 .8,.4H19a1.5,1.5 0 0,1 1.5,1.5v8a1.5,1.5 0 0,1 -1.5,1.5H5a1.5,1.5 0 0,1 -1.5,-1.5V8A1.5,1.5 0 0,1 5,6.5z M18,3.6a2.6,2.6 0 1,0 .01,0z M18,5.2a.7,.7 0 1,0 .01,0 M16.8,7.6c.3,-.7 .8,-1 1.2,-1s.9,.3 1.2,1"
    const val CLOUD = "M7.5,18.5a4,4 0 0,1 -.6,-7.95a5.5,5.5 0 0,1 10.6,-.6a4.3,4.3 0 0,1 -.4,8.55z"
    const val MARKUP = "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18 M8.5,16l3.5,-9l3.5,9 M9.8,13h4.4"
    const val DESKTOP = "M5,5h14a1.5,1.5 0 0,1 1.5,1.5v9a1.5,1.5 0 0,1 -1.5,1.5H5a1.5,1.5 0 0,1 -1.5,-1.5v-9A1.5,1.5 0 0,1 5,5z M3.5,12.5h17 M9,19.5h6 M12,17v2.5"
    const val DOCGLYPH = "M7,3h7l4,4v13a1,1 0 0,1 -1,1h-10a1,1 0 0,1 -1,-1v-16a1,1 0 0,1 1,-1z M14,3v4h4"
    const val DLCIRCLE = "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18 M12,8v8 M8.5,12.5l3.5,3.5l3.5,-3.5"
    const val PHOTO = "M5.5,4.5h13a2,2 0 0,1 2,2v11a2,2 0 0,1 -2,2h-13a2,2 0 0,1 -2,-2v-11a2,2 0 0,1 2,-2z M3.5,16.2l4.6,-4.6a1,1 0 0,1 1.4,0l4.5,4.5 M13.2,14.3l1.9,-1.9a1,1 0 0,1 1.4,0l4,4 M8.2,9.3a1.2,1.2 0 1,0 .01,0"
    const val FILM = "M5.5,5h13a2,2 0 0,1 2,2v10a2,2 0 0,1 -2,2h-13a2,2 0 0,1 -2,-2v-10a2,2 0 0,1 2,-2z M10.2,9.2l4.6,2.8l-4.6,2.8z"
    const val CAMERA = "M4.5,8h2.2l1.1,-1.8a1,1 0 0,1 .9,-.5h6.6a1,1 0 0,1 .9,.5l1.1,1.8h2.2a1.5,1.5 0 0,1 1.5,1.5v8a1.5,1.5 0 0,1 -1.5,1.5h-15a1.5,1.5 0 0,1 -1.5,-1.5v-8A1.5,1.5 0 0,1 4.5,8z M12,10.2a3.3,3.3 0 1,0 .01,0"
    const val PACKAGE = "M12,3l8,4v10l-8,4l-8,-4v-10z M4,7l8,4l8,-4 M12,11v10"
    const val APPA = "M12,4l-6,14 M12,4l6,14 M8,13.5h8"
    const val CLOSE = "M6,6L18,18 M18,6L6,18"
    const val OPENIN = "M14,4h6v6 M20,4l-9,9 M18,14v4a2,2 0 0,1 -2,2h-9a2,2 0 0,1 -2,-2v-9a2,2 0 0,1 2,-2h4"
    const val DOCS = "M8.5,8.5a1.5,1.5 0 0,1 1.5,-1.5h4.5l4,4v7.5a1.5,1.5 0 0,1 -1.5,1.5h-7a1.5,1.5 0 0,1 -1.5,-1.5z M14.5,7v3.5a.5,.5 0 0,0 .5,.5h3.5 M6,16H5.5A1.5,1.5 0 0,1 4,14.5V5.5A1.5,1.5 0 0,1 5.5,4H12"
    const val SYNC = "M12,3.5a8.5,8.5 0 1,0 .01,0z M12,7.5V12l3.2,1.8"
    const val GEAR = "M12,9a3,3 0 1,0 0.01,0 M12,3v3 M12,18v3 M3,12h3 M18,12h3 M5.6,5.6l2.1,2.1 M16.3,16.3l2.1,2.1 M5.6,18.4l2.1,-2.1 M16.3,7.7l2.1,-2.1"
    const val GLOBE = "M12,2.5a9.5,9.5 0 1,0 .01,0z M12,2.5c-4,2.5 -4,16.5 0,19 M12,2.5c4,2.5 4,16.5 0,19 M2.5,12h19 M4,7.2c5,2 11,2 16,0 M4,16.8c5,-2 11,-2 16,0"
    const val AIRPLAYF = "M6.5,17H5.5a2,2 0 0,1 -2,-2V6.5a2,2 0 0,1 2,-2h13a2,2 0 0,1 2,2V15a2,2 0 0,1 -2,2h-1"
    const val AIRPLAYT = "M12,14.2L17.2,20.5H6.8z"
    const val PALETTE = "M12,3a9,9 0 1,0 0,18c1.2,0 1.6,-.9 1.2,-1.8c-.5,-1.1 .2,-2.2 1.5,-2.2H17a4,4 0 0,0 4,-4c0,-5 -4,-10 -9,-10z M7.5,11a.9,.9 0 1,0 .01,0 M10,7a.9,.9 0 1,0 .01,0 M14.5,7a.9,.9 0 1,0 .01,0 M17.2,10.5a.9,.9 0 1,0 .01,0"
    const val NOTE = "M9,17.5V6.2l9,-2v11.3 M9,17.5a2.5,2.5 0 1,1 -5,0a2.5,2.5 0 0,1 5,0z M18,15.5a2.5,2.5 0 1,1 -5,0a2.5,2.5 0 0,1 5,0z"
}

class IconDrawable(private val pathData: String, col: Int, private val stroke: Boolean = true, sw: Float = 1.8f) : Drawable() {
    private val path = PathParser.createPathFromPathData(pathData)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = if (stroke) Paint.Style.STROKE else Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = if (pathData == P.MORE) 3.4f else sw
        color = col
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = b.width() / 24f
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        c.scale(s, s)
        c.drawPath(path, paint)
        c.restore()
    }

    override fun setAlpha(a: Int) { paint.alpha = a }
    override fun setColorFilter(f: ColorFilter?) { paint.colorFilter = f }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun Context.icon(path: String, col: Int, sizeDp: Int, stroke: Boolean = true, sw: Float = 1.8f): ImageView =
    ImageView(this).apply {
        setImageDrawable(if (path == P.GEAR) MaskIconDrawable(R.drawable.ic_settings, col) else IconDrawable(path, col, stroke, sw))
        layoutParams = ViewGroup.LayoutParams(dpi(sizeDp), dpi(sizeDp))
    }

/** Gambar folder & dokumen dari user (res/drawable-nodpi). */
object Assets {
    val folder: Bitmap by lazy { BitmapFactory.decodeResource(Thumbs.ctx.resources, R.drawable.ic_folder) }
    val doc: Bitmap by lazy { BitmapFactory.decodeResource(Thumbs.ctx.resources, R.drawable.ic_doc) }
}

object Glyph {
    private val m = HashMap<String, IconDrawable>()
    fun get(path: String, col: Int, stroke: Boolean = true, sw: Float = 1.8f): IconDrawable =
        m.getOrPut("$path/$col/$stroke/$sw") { IconDrawable(path, col, stroke, sw) }
}

/** Ikon tengah folder sesuai fungsinya. */
fun folderGlyph(name: String): String? {
    val n = name.lowercase()
    return when {
        n.contains("download") || n.contains("unduh") -> P.DLCIRCLE
        n.contains("document") || n.contains("dokumen") || n == "docs" -> P.DOCGLYPH
        n.contains("desktop") -> P.DESKTOP
        n.contains("music") || n.contains("audio") || n.contains("lagu") || n.contains("ringtone") -> P.NOTE
        n.contains("movie") || n.contains("video") || n.contains("film") -> P.FILM
        n.contains("dcim") || n.contains("camera") || n.contains("kamera") -> P.CAMERA
        n.contains("picture") || n.contains("photo") || n.contains("image") || n.contains("screenshot") || n.contains("gambar") || n.contains("wallpaper") -> P.PHOTO
        n.contains("apk") || n.contains("apps") -> P.PACKAGE
        n.contains("backup") -> P.CLOCK
        n.contains("android") -> P.PHONE
        n.contains("icloud") -> P.CLOUD
        n.contains("deleted") -> P.TRASH
        n.contains("zip") || n.contains("archive") -> P.ARCHIVE
        else -> null
    }
}

class FolderDrawable : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun draw(c: Canvas) {
        val bm = Assets.folder
        val b = bounds
        val s = minOf(b.width().toFloat() / bm.width, b.height().toFloat() / bm.height)
        val dw = bm.width * s
        val dh = bm.height * s
        val l = b.left + (b.width() - dw) / 2
        val t = b.top + (b.height() - dh) / 2
        c.drawBitmap(bm, null, RectF(l, t, l + dw, t + dh), p)
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Liquid glass: isi tembus, kilau atas, dan pinggiran terang (rim) seperti kaca Apple. */
class GlassDrawable(
    private val corner: Float,
    private val top: Int = 0x66FFFFFF,
    private val bottom: Int = 0x40FFFFFF,
    private val dark: Boolean = false
) : Drawable() {
    private val d = Resources.getSystem().displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    private fun rad(): Float = minOf(corner, minOf(bounds.width(), bounds.height()) / 2f)

    /** Mode gelap: isi tebal jadi abu gelap, isi tipis jadi putih tembus yang lebih redup. */
    private fun adapt(c: Int): Int {
        if (!C.dark || dark) return c
        val a = c ushr 24
        return if (a > 0xC0) (a shl 24) or 0x2C2C2E else ((a * 0.22f).toInt() shl 24) or 0xFFFFFF
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val rd = rad()
        r.set(b)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, adapt(top), adapt(bottom), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rd, rd, p)
        val darkish = C.dark || dark
        // garis luar sangat tipis supaya tepi tetap terbaca di latar terang
        if (!darkish) {
            val h = 0.25f * d
            r.set(b.left + h, b.top + h, b.right - h, b.bottom - h)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.5f * d
            p.shader = null
            p.color = 0x14000000
            c.drawRoundRect(r, maxOf(0f, rd - h), maxOf(0f, rd - h), p)
        }
        // rim: terang di kiri atas dan kanan bawah, hampir bening di tengah
        val sw = 1.0f * d
        r.set(b.left + sw / 2, b.top + sw / 2, b.right - sw / 2, b.bottom - sw / 2)
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        val rim = if (darkish) intArrayOf(0x73FFFFFF, 0x0FFFFFFF, 0x0FFFFFFF, 0x4DFFFFFF)
        else intArrayOf(0xCCFFFFFF.toInt(), 0x33FFFFFF, 0x33FFFFFF, 0xA6FFFFFF.toInt())
        p.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            rim, floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, maxOf(0f, rd - sw / 2), maxOf(0f, rd - sw / 2), p)
        p.shader = null
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, rad())
        outline.setAlpha(0.9f)
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Wadah halaman: memberi tahu kaca setiap kali ada anak-cucu yang berubah (scroll, animasi, gambar termuat). */
class NotifyFrame(ctx: Context) : FrameLayout(ctx) {
    var onChange: (() -> Unit)? = null
    override fun onDescendantInvalidated(child: View, target: View) {
        super.onDescendantInvalidated(child, target)
        onChange?.invoke()
    }
}

/** Parameter efek liquid, mengikuti LiquidScope milik FletchMcKee/liquid. */
class LiquidParams(
    var frost: Float = 8f,            // dp, blur latar (Android 12+)
    var refraction: Float = 0.35f,     // 0..1 seberapa jauh latar dibelokkan (Android 13+)
    var curve: Float = 0.5f,          // 0..1 kelengkungan lensa pusat vs tepi (Android 13+)
    var edge: Float = 0.06f,          // kilau pinggiran
    var tintColor: Int = 0x24FFFFFF,
    var saturation: Float = 1.5f,
    var dispersion: Float = 0.12f,    // aberasi kromatik di tepi (Android 13+)
    var contrast: Float = 0f
)

/**
 * Latar kaca dinamis ala FletchMcKee/liquid: "source" (halaman) direkam ulang setiap berubah,
 * lalu efek mengambil sampel piksel di belakangnya: frost (blur) -> lensa bola (refraksi + kurva)
 * -> dispersi -> saturasi/kontras/tint -> kilau tepi.
 *  - Android 13+ : semua efek (shader AGSL)
 *  - Android 12  : frost + saturasi (tanpa refraksi/kurva/dispersi)
 *  - Android 11- : kaca statis tembus
 * Tidak memakai polling: dibuat ulang tepat saat halaman berubah (lihat NotifyFrame).
 * View ini bukan keturunan source-nya, jadi tidak ada rekursi gambar.
 */
class GlassBackdropView(
    ctx: Context,
    private val source: View,
    private val corner: Float,
    val params: LiquidParams = LiquidParams()
) : View(ctx) {
    private val loc = IntArray(2)
    private val sloc = IntArray(2)
    private val sat = Paint()

    private val AGSL = """
        uniform shader content;
        uniform float2 viewSize;
        uniform float cornerR;
        uniform float maxOffset;
        uniform float curve;
        uniform float dispersion;
        uniform float edge;
        uniform float saturation;
        uniform float contrast;
        uniform float4 tint;

        float sdRound(float2 p, float2 hs, float r) {
            float2 q = abs(p) - (hs - float2(r));
            return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
        }

        half4 main(float2 coord) {
            float2 hs = viewSize * 0.5;
            float2 p = coord - hs;
            float d = sdRound(p, hs, cornerR);
            float2 g = float2(
                sdRound(p + float2(1.0, 0.0), hs, cornerR) - sdRound(p - float2(1.0, 0.0), hs, cornerR),
                sdRound(p + float2(0.0, 1.0), hs, cornerR) - sdRound(p - float2(0.0, 1.0), hs, cornerR));
            float2 n = g / max(length(g), 0.0001);
            float bevel = min(viewSize.x, viewSize.y) * 0.5 * (0.25 + 0.5 * curve);
            float t = clamp(-d / bevel, 0.0, 1.0);
            float m = maxOffset * pow(1.0 - t, 2.0 + 1.5 * (1.0 - curve));
            float2 off = n * m;
            float2 lo = float2(0.5);
            float2 hi = viewSize - float2(0.5);
            float4 cr = float4(content.eval(clamp(coord - off * (1.0 - 0.3 * dispersion), lo, hi)));
            float4 cg = float4(content.eval(clamp(coord - off, lo, hi)));
            float4 cb = float4(content.eval(clamp(coord - off * (1.0 + 0.3 * dispersion), lo, hi)));
            float3 rgb = float3(cr.r, cg.g, cb.b);
            float lum = dot(rgb, float3(0.2126, 0.7152, 0.0722));
            rgb = mix(float3(lum), rgb, saturation);
            rgb = (rgb - 0.5) * (1.0 + contrast) + 0.5;
            rgb = mix(rgb, tint.rgb, tint.a);
            float rim = 1.0 - clamp(-d / 3.0, 0.0, 1.0);
            float l1 = max(dot(n, normalize(float2(-0.55, -0.83))), 0.0);
            float l2 = max(dot(n, normalize(float2(0.55, 0.83))), 0.0) * 0.55;
            rgb += float3(rim * edge * (l1 + l2));
            return half4(half3(clamp(rgb, 0.0, 1.0)), half(cg.a));
        }
    """

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, corner) }
        }
        clipToOutline = true
        if (Build.VERSION.SDK_INT in 31..32) {
            sat.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(params.saturation) })
        }
    }

    /** Panggil setelah mengubah params. */
    fun applyParams() {
        buildEffect(width, height)
        invalidate()
    }

    private fun buildEffect(w: Int, h: Int) {
        if (Build.VERSION.SDK_INT < 31 || w <= 0 || h <= 0) return
        val d = resources.displayMetrics.density
        val br = maxOf(params.frost * d, 0.1f)
        val blur = RenderEffect.createBlurEffect(br, br, Shader.TileMode.CLAMP)
        var eff: RenderEffect = blur
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val sh = RuntimeShader(AGSL)
                sh.setFloatUniform("viewSize", w.toFloat(), h.toFloat())
                sh.setFloatUniform("cornerR", minOf(corner, minOf(w, h) / 2f))
                sh.setFloatUniform("maxOffset", params.refraction * 34f * d)
                sh.setFloatUniform("curve", params.curve)
                sh.setFloatUniform("dispersion", params.dispersion)
                sh.setFloatUniform("edge", params.edge)
                sh.setFloatUniform("saturation", params.saturation)
                sh.setFloatUniform("contrast", params.contrast)
                val t = params.tintColor
                sh.setFloatUniform(
                    "tint",
                    ((t shr 16) and 0xFF) / 255f, ((t shr 8) and 0xFF) / 255f, (t and 0xFF) / 255f, ((t ushr 24) and 0xFF) / 255f
                )
                eff = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(sh, "content"), blur)
            } catch (t: Throwable) {
                eff = blur
            }
        }
        setRenderEffect(eff)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        buildEffect(w, h)
    }

    // Jangan ikut membesarkan induk (WRAP_CONTENT); ukuran hanya dipakai saat sudah pasti.
    override fun onMeasure(w: Int, h: Int) {
        val ww = if (View.MeasureSpec.getMode(w) == View.MeasureSpec.EXACTLY) View.MeasureSpec.getSize(w) else 0
        val hh = if (View.MeasureSpec.getMode(h) == View.MeasureSpec.EXACTLY) View.MeasureSpec.getSize(h) else 0
        setMeasuredDimension(ww, hh)
    }

    override fun onDraw(c: Canvas) {
        if (Build.VERSION.SDK_INT < 31) { c.drawColor((C.BG and 0x00FFFFFF) or 0xE6000000.toInt()); return }
        getLocationInWindow(loc)
        source.getLocationInWindow(sloc)
        c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), sat)
        c.translate((sloc[0] - loc[0]).toFloat(), (sloc[1] - loc[1]).toFloat())
        source.draw(c)
        c.restore()
    }
}

object Thumbs {
    lateinit var ctx: Context
    var onLoaded: (() -> Unit)? = null
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(f: File, px: Int, cb: (Bitmap?) -> Unit) {
        val e = f.extension.lowercase()
        val img = e in FileOps.IMG
        val vid = e in FileOps.VID
        val apk = e == "apk"
        if (!img && !vid && !apk) return
        val key = f.absolutePath + f.lastModified()
        val hit = cache.get(key)
        if (hit != null) { cb(hit); return }
        pool.execute {
            val b: Bitmap? = try {
                when {
                    img -> decode(f, px)
                    vid -> video(f, px)
                    else -> apkIcon(f, px)
                }
            } catch (t: Throwable) { null }
            if (b != null) cache.put(key, b)
            main.post { cb(b); onLoaded?.invoke() }
        }
    }

    fun decode(f: File, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        var s = 1
        while (minOf(o.outWidth, o.outHeight) / (s * 2) >= px) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        return BitmapFactory.decodeFile(f.absolutePath, o2)
    }

    private fun video(f: File, px: Int): Bitmap? {
        val m = MediaMetadataRetriever()
        try {
            m.setDataSource(f.absolutePath)
            val b = m.getFrameAtTime(0) ?: return null
            val sc = px.toFloat() / minOf(b.width, b.height)
            return if (sc < 1f) Bitmap.createScaledBitmap(b, (b.width * sc).toInt(), (b.height * sc).toInt(), true) else b
        } finally {
            try { m.release() } catch (_: Throwable) {}
        }
    }

    private fun apkIcon(f: File, px: Int): Bitmap? {
        val pm = ctx.packageManager
        val pi = pm.getPackageArchiveInfo(f.absolutePath, 0) ?: return null
        val ai = pi.applicationInfo ?: return null
        ai.sourceDir = f.absolutePath
        ai.publicSourceDir = f.absolutePath
        val d = ai.loadIcon(pm)
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, px, px)
        d.draw(c)
        return b
    }
}

/** Thumbnail: folder iOS, foto/video, atau halaman dokumen dengan label ekstensi. */
class FileThumbView(ctx: Context) : View(ctx) {
    var file: File? = null
    var bmp: Bitmap? = null
    var selState = 0   // 0 = tidak memilih, 1 = belum dipilih, 2 = dipilih
    private val folder = FolderDrawable()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    fun bind(f: File) {
        file = f
        bmp = null
        invalidate()
        if (f.isDirectory) return
        Thumbs.load(f, 256) { b -> if (file == f) { bmp = b; invalidate() } }
    }

    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val APKS = setOf("apk", "apks", "xapk", "apkm")
    private val ARCH = setOf("zip", "rar", "7z", "tar", "gz", "xz", "tgz")
    private val EXES = setOf("exe", "msi", "bat", "dll", "cmd")

    private fun fit(bm: Bitmap, w: Float, h: Float, fw: Float, fh: Float): RectF {
        val s = minOf(w * fw / bm.width, h * fh / bm.height)
        val dw = bm.width * s
        val dh = bm.height * s
        return RectF(w / 2 - dw / 2, h / 2 - dh / 2, w / 2 + dw / 2, h / 2 + dh / 2)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (selState == 0) return
        val d = resources.displayMetrics.density
        val r = 11f * d
        val cx = width - r - 3 * d
        val cy = height - r - 3 * d
        val pp = Paint(Paint.ANTI_ALIAS_FLAG)
        if (selState == 2) {
            pp.color = C.BLUE
            canvas.drawCircle(cx, cy, r, pp)
            val ck = Glyph.get(P.CHECK, Color.WHITE, true, 2.6f)
            ck.setBounds((cx - r * 0.62f).toInt(), (cy - r * 0.62f).toInt(), (cx + r * 0.62f).toInt(), (cy + r * 0.62f).toInt())
            ck.draw(canvas)
        } else {
            pp.color = 0xCCFFFFFF.toInt()
            canvas.drawCircle(cx, cy, r, pp)
            pp.style = Paint.Style.STROKE
            pp.strokeWidth = 1.6f * d
            pp.color = 0xFF8E8E93.toInt()
            canvas.drawCircle(cx, cy, r - d, pp)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val f = file ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        if (f.isDirectory) {
            val fci = FolderColors.idxOf(f.path)
            val fb = FolderColors.bitmap(fci)
            val r = fit(fb, w, h, 0.98f, 0.98f)
            canvas.drawBitmap(fb, null, r, bp)
            val bd = FolderBadges.map[f.path]
            val g = if (bd != null) null else folderGlyph(f.name)
            if (bd != null) {
                val cx = r.centerX()
                val cy = r.top + r.height() * 0.64f
                if (bd.startsWith("e:")) {
                    val s = r.height() * 0.40f
                    val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = s; typeface = Emoji.face ?: Typeface.DEFAULT }
                    canvas.drawText(bd.substring(2), cx, cy + s * 0.34f, ep)
                } else {
                    val ib = FolderBadges.appIcon(bd.substring(2))
                    if (ib != null) {
                        val s = r.height() * 0.42f
                        val dst = RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
                        canvas.save()
                        canvas.clipPath(Path().apply { addRoundRect(dst, s * 0.22f, s * 0.22f, Path.Direction.CW) })
                        canvas.drawBitmap(ib, null, dst, bp)
                        canvas.restore()
                    }
                }
            }
            if (g != null) {
                val s = r.height() * 0.34f
                val ic = Glyph.get(g, FolderColors.glyph(fci), true, 1.9f)
                val cx = r.centerX()
                val cy = r.top + r.height() * 0.64f
                ic.setBounds((cx - s / 2).toInt(), (cy - s / 2).toInt(), (cx + s / 2).toInt(), (cy + s / 2).toInt())
                ic.draw(canvas)
            }
            return
        }
        val e = f.extension.lowercase()
        val b = bmp
        // Foto / video: thumbnail apa adanya (rasio asli)
        if (b != null && e !in APKS) {
            val s = minOf(w / b.width, h / b.height)
            val dw = b.width * s
            val dh = b.height * s
            val r = RectF(w / 2 - dw / 2, h / 2 - dh / 2, w / 2 + dw / 2, h / 2 + dh / 2)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(r, 3 * d, 3 * d, Path.Direction.CW) })
            canvas.drawBitmap(b, null, r, bp)
            canvas.restore()
            return
        }
        // Dokumen: gambar halaman dari user + ikon tengah sesuai jenis file
        val db = Assets.doc
        val r = fit(db, w, h, 0.96f, 0.96f)
        canvas.drawBitmap(db, null, r, bp)
        drawBadge(canvas, e, r.left + r.width() * 0.49f, r.top + r.height() * 0.55f, r.width() * 0.52f, b, r)
    }

    private val labelFace: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun drawBadge(c: Canvas, e: String, cx: Float, cy: Float, s: Float, b: Bitmap?, page: RectF) {
        if (e in FileOps.AUD) {
            val fr = floatArrayOf(0.3f, 0.6f, 1f, 0.7f, 0.5f, 0.85f, 0.35f)
            val bw = page.width() * 0.055f
            val gap = page.width() * 0.085f
            val tot = fr.size * bw + (fr.size - 1) * gap
            val mh = page.height() * 0.34f
            p.style = Paint.Style.FILL
            p.shader = null
            p.color = C.BLUE
            for (i in fr.indices) {
                val x = cx - tot / 2 + i * (bw + gap)
                val bh = mh * fr[i]
                c.drawRoundRect(RectF(x, cy - bh / 2, x + bw, cy + bh / 2), bw / 2, bw / 2, p)
            }
            return
        }
        if (e == "zip" || e == "pdf" || e == "apk") {
            tp.color = 0xFF3B8EEA.toInt()
            tp.typeface = labelFace
            tp.textSize = page.width() * 0.40f
            c.drawText(e, cx, page.top + page.height() * 0.52f + tp.textSize * 0.34f, tp)
        }
    }
}

/**
 * EditText dengan kursor biru iOS yang bergerak halus (animasi geser) dan berkedip.
 * Kursor bawaan Android disembunyikan, kursor ini digambar sendiri.
 */
class IosEditText(ctx: Context) : EditText(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.BLUE }
    private var cx = 0f
    private var cy = 0f
    private var ch = 0f
    private var tx = Float.NaN
    private var ty = Float.NaN
    private var inited = false
    private var blink = true
    private var anim: ValueAnimator? = null
    private val blinker = object : Runnable {
        override fun run() {
            blink = !blink
            invalidate()
            postDelayed(this, 530)
        }
    }

    init {
        isCursorVisible = false
        background = null
        typeface = Fonts.regular
        highlightColor = 0x55007AFF
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (s != null) Emoji.spanInPlace(s) }
        })
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        removeCallbacks(blinker)
        inited = false
        blink = true
        if (focused) postDelayed(blinker, 530)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blinker)
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isFocused || selectionStart != selectionEnd || selectionStart < 0) return
        val l = layout ?: return
        val o = selectionEnd
        val line = l.getLineForOffset(o)
        val nx = l.getPrimaryHorizontal(o) + compoundPaddingLeft
        val top = (l.getLineTop(line) + extendedPaddingTop).toFloat()
        val nh = (l.getLineBottom(line) - l.getLineTop(line)).toFloat()
        if (!inited) {
            cx = nx; cy = top; ch = nh; tx = nx; ty = top; inited = true
        } else if (nx != tx || top != ty) {
            tx = nx; ty = top; ch = nh
            anim?.cancel()
            val sx = cx
            val sy = cy
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 150
                interpolator = IOS_EASE
                addUpdateListener {
                    val f = it.animatedFraction
                    cx = sx + (tx - sx) * f
                    cy = sy + (ty - sy) * f
                    invalidate()
                }
                start()
            }
        }
        val running = anim?.isRunning == true
        if (blink || running) {
            val w = resources.displayMetrics.density * 2.2f
            canvas.drawRoundRect(cx - w / 2, cy, cx + w / 2, cy + ch, w / 2, w / 2, paint)
        }
    }
}

/** Kanvas coret-coret untuk Markup. */
class MarkupView(ctx: Context) : View(ctx) {
    val strokes = ArrayList<Pair<Path, Int>>()
    var color = 0xFFFF3B30.toInt()
    private var cur: Path? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * ctx.resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        for ((p, col) in strokes) { paint.color = col; c.drawPath(p, paint) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val p = Path().apply { moveTo(e.x, e.y); lineTo(e.x + 0.1f, e.y + 0.1f) }
                cur = p
                strokes.add(Pair(p, color))
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> { cur?.lineTo(e.x, e.y); invalidate() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cur = null
        }
        return true
    }

    fun undo() { if (strokes.isNotEmpty()) { strokes.removeAt(strokes.size - 1); invalidate() } }

    fun render(src: Bitmap, vw: Int, vh: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val s = minOf(vw.toFloat() / out.width, vh.toFloat() / out.height)
        val ox = (vw - out.width * s) / 2f
        val oy = (vh - out.height * s) / 2f
        val c = Canvas(out)
        c.scale(1f / s, 1f / s)
        c.translate(-ox, -oy)
        for ((p, col) in strokes) { paint.color = col; c.drawPath(p, paint) }
        return out
    }
}

/** Ikon bar bawah ala iOS (terisi): 0 = jam, 1 = dua orang, 2 = folder. */
class TabIcon(private val kind: Int, col: Int) : Drawable() {
    private val paths = arrayOf(
        PathParser.createPathFromPathData("M12,2.5a9.5,9.5 0 1,0 0,19a9.5,9.5 0 1,0 0,-19z"),
        PathParser.createPathFromPathData("M9,5.5a3,3 0 1,0 0.01,0z M2.5,19c0,-3.6 2.8,-5.8 6.5,-5.8s6.5,2.2 6.5,5.8z M17,6.5a2.5,2.5 0 1,0 0.01,0z M16.3,13.4c3,0.2 5.2,1.9 5.2,5.1h-4.3c0,-2 -0.3,-3.6 -0.9,-5.1z"),
        PathParser.createPathFromPathData(P.FOLDER)
    )
    private val hands = PathParser.createPathFromPathData("M12,7v5.5h4")
    private val line = PathParser.createPathFromPathData("M3,10.5h18")
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = col }
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = b.width() / 24f
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        c.scale(s, s)
        c.drawPath(paths[kind], fill)
        if (kind == 0) { st.color = if (C.dark) 0xFF1C1C1E.toInt() else Color.WHITE; st.strokeWidth = 1.9f; c.drawPath(hands, st) }
        if (kind == 2) { st.color = 0x59FFFFFF; st.strokeWidth = 1.1f; c.drawPath(line, st) }
        c.restore()
    }

    override fun setAlpha(a: Int) { fill.alpha = a }
    override fun setColorFilter(f: ColorFilter?) { fill.colorFilter = f }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Ikon aplikasi di baris share: 0 = AirDrop, 1 = Messages, 2 = Mail. */
class ShareIconDrawable(private val kind: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pa = Path()

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        val rad = w * 0.225f
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.shader = when (kind) {
            0 -> null
            1 -> LinearGradient(0f, 0f, 0f, h, 0xFF67E86B.toInt(), 0xFF2CC24A.toInt(), Shader.TileMode.CLAMP)
            else -> LinearGradient(0f, 0f, 0f, h, 0xFF55B8FF.toInt(), 0xFF1B76F0.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(RectF(0f, 0f, w, h), rad, rad, p)
        p.shader = null
        when (kind) {
            0 -> {
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = w * 0.06f
                p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF4AA3FF.toInt(), 0xFF1E6FE8.toInt(), Shader.TileMode.CLAMP)
                val cx = w / 2
                val cy = h * 0.56f
                for (r in floatArrayOf(0.38f, 0.27f, 0.16f)) {
                    c.drawArc(RectF(cx - w * r, cy - w * r, cx + w * r, cy + w * r), 135f, 270f, false, p)
                }
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, w * 0.045f, p)
                p.shader = null
            }
            1 -> {
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                c.drawOval(RectF(w * 0.17f, h * 0.22f, w * 0.83f, h * 0.70f), p)
                pa.reset()
                pa.moveTo(w * 0.26f, h * 0.58f)
                pa.lineTo(w * 0.20f, h * 0.80f)
                pa.lineTo(w * 0.44f, h * 0.67f)
                pa.close()
                c.drawPath(pa, p)
            }
            else -> {
                p.style = Paint.Style.STROKE
                p.color = Color.WHITE
                p.strokeWidth = w * 0.05f
                p.strokeJoin = Paint.Join.ROUND
                p.strokeCap = Paint.Cap.ROUND
                c.drawRoundRect(RectF(w * 0.2f, h * 0.3f, w * 0.8f, h * 0.7f), w * 0.07f, w * 0.07f, p)
                pa.reset()
                pa.moveTo(w * 0.22f, h * 0.34f)
                pa.lineTo(w * 0.5f, h * 0.54f)
                pa.lineTo(w * 0.78f, h * 0.34f)
                c.drawPath(pa, p)
            }
        }
        c.restore()
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Saklar ala iOS (hijau saat aktif) dengan animasi geser. */
class IosSwitch(ctx: Context) : View(ctx) {
    var checked = false
        private set
    var onToggle: ((Boolean) -> Unit)? = null
    private var pos = 0f
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    init {
        setOnClickListener {
            val nv = !checked
            put(nv, true)
            onToggle?.invoke(nv)
        }
    }

    fun put(v: Boolean, animate: Boolean) {
        checked = v
        val t = if (v) 1f else 0f
        anim?.cancel()
        if (!animate) { pos = t; invalidate(); return }
        val s = pos
        anim = ValueAnimator.ofFloat(s, t).apply {
            duration = 220
            interpolator = IOS_EASE
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((51 * d).toInt(), (31 * d).toInt())
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val ar = a ushr 24; val rr = (a shr 16) and 255; val gg = (a shr 8) and 255; val bb = a and 255
        val br = b ushr 24; val r2 = (b shr 16) and 255; val g2 = (b shr 8) and 255; val b2 = b and 255
        return ((ar + (br - ar) * t).toInt() shl 24) or ((rr + (r2 - rr) * t).toInt() shl 16) or
            ((gg + (g2 - gg) * t).toInt() shl 8) or (bb + (b2 - bb) * t).toInt()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val off = if (C.dark) 0xFF39393D.toInt() else 0xFFE9E9EA.toInt()
        p.style = Paint.Style.FILL
        p.color = lerp(off, 0xFF34C759.toInt(), pos)
        c.drawRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, p)
        val kr = h / 2 - 2 * d
        val cx = 2 * d + kr + pos * (w - 4 * d - 2 * kr)
        p.color = 0x22000000
        c.drawCircle(cx, h / 2 + d, kr, p)
        p.color = Color.WHITE
        c.drawCircle(cx, h / 2, kr, p)
    }
}

/** Font SF Pro (dari aset yang diberikan user, sudah di-subset). */
object Fonts {
    private fun load(path: String, fallback: Typeface): Typeface =
        try { Typeface.createFromAsset(Thumbs.ctx.assets, path) } catch (e: Throwable) { fallback }
    val regular: Typeface by lazy { load("fonts/SF-Pro-Text-Regular.otf", Typeface.DEFAULT) }
    val semibold: Typeface by lazy { load("fonts/SF-Pro-Text-Semibold.otf", Typeface.DEFAULT_BOLD) }
    val display: Typeface by lazy { load("fonts/SF-Pro-Display-Bold.otf", Typeface.DEFAULT_BOLD) }
}

/** Ikon dari bitmap (mask alfa) yang diwarnai, dipakai untuk ikon Settings dari user. */
class MaskIconDrawable(private val res: Int, col: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN)
    }
    private val bm: Bitmap by lazy { BitmapFactory.decodeResource(Thumbs.ctx.resources, res) }
    override fun draw(c: Canvas) { c.drawBitmap(bm, null, bounds, p) }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Spinner iOS (12 jarum) dari im_loading.png, berputar per 30 derajat seperti aslinya. */
class SpinnerDrawable(private val tint: Int?, private val size: Int = -1) : Drawable(), android.graphics.drawable.Animatable {
    private var step = 0
    private var running = false
    private val h = Handler(Looper.getMainLooper())
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bm: Bitmap by lazy { BitmapFactory.decodeResource(Thumbs.ctx.resources, R.drawable.im_loading) }
    private val tick = object : Runnable {
        override fun run() {
            step = (step + 1) % 12
            invalidateSelf()
            if (running) h.postDelayed(this, 83)
        }
    }

    init { if (tint != null) p.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN) }

    override fun start() { if (running) return; running = true; h.postDelayed(tick, 83) }
    override fun stop() { running = false; h.removeCallbacks(tick) }
    override fun isRunning() = running
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size

    override fun draw(c: Canvas) {
        val b = bounds
        c.save()
        c.rotate(step * 30f, b.exactCenterX(), b.exactCenterY())
        c.drawBitmap(bm, null, b, p)
        c.restore()
    }

    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Scrubber pemutar ala iOS: jalur tipis dan kenop putih kecil. */
class ScrubView(ctx: Context) : View(ctx) {
    var max = 1
    var progress = 0
    var onSeek: ((Int) -> Unit)? = null
    var onStart: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dragging = false

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(View.MeasureSpec.getSize(w), (24 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val cy = height / 2f
        val th = 4 * d
        val kr = 6 * d
        val x0 = kr
        val x1 = width - kr
        val frac = if (max > 0) (progress.toFloat() / max).coerceIn(0f, 1f) else 0f
        val kx = x0 + (x1 - x0) * frac
        p.style = Paint.Style.FILL
        p.color = 0xFF48484A.toInt()
        c.drawRoundRect(RectF(x0, cy - th / 2, x1, cy + th / 2), th / 2, th / 2, p)
        p.color = 0xFFD1D1D6.toInt()
        c.drawRoundRect(RectF(x0, cy - th / 2, kx, cy + th / 2), th / 2, th / 2, p)
        p.color = Color.WHITE
        c.drawCircle(kx, cy, (if (dragging) 8 else 6) * d, p)
    }

    private fun upd(x: Float) {
        val kr = 6 * d
        val frac = ((x - kr) / (width - 2 * kr)).coerceIn(0f, 1f)
        progress = (frac * max).toInt()
        onSeek?.invoke(progress)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragging = true; parent?.requestDisallowInterceptTouchEvent(true); onStart?.invoke(); upd(e.x) }
            MotionEvent.ACTION_MOVE -> upd(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; onStop?.invoke(); invalidate() }
        }
        return true
    }
}

/** Ikon "mundur / maju 15 detik" ala iOS. */
class SkipDrawable(private val forward: Boolean, private val col: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(c: Canvas) {
        val b = bounds
        val s = b.width().toFloat()
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        val cx = s / 2
        val cy = s * 0.54f
        val r = s * 0.38f
        c.save()
        if (!forward) { c.scale(-1f, 1f); c.translate(-s, 0f) }
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.075f
        p.strokeCap = Paint.Cap.ROUND
        p.color = col
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -55f, 290f, false, p)
        val th = Math.toRadians(235.0)
        val px = cx + r * Math.cos(th).toFloat()
        val py = cy + r * Math.sin(th).toFloat()
        val dx = (-Math.sin(th)).toFloat()
        val dy = Math.cos(th).toFloat()
        val nx = Math.cos(th).toFloat()
        val ny = Math.sin(th).toFloat()
        val hl = s * 0.15f
        val hw = s * 0.1f
        val path = Path()
        path.moveTo(px + dx * hl, py + dy * hl)
        path.lineTo(px + nx * hw, py + ny * hw)
        path.lineTo(px - nx * hw, py - ny * hw)
        path.close()
        p.style = Paint.Style.FILL
        c.drawPath(path, p)
        c.restore()
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Fonts.semibold
        p.textSize = s * 0.33f
        c.drawText("15", cx, cy + s * 0.12f, p)
        c.restore()
    }

    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Slider 3 langkah (Default / Medium / Large) seperti layar Appearance iOS. */
class StepSlider(ctx: Context) : View(ctx) {
    var step = 0
        private set
    var onStep: ((Int) -> Unit)? = null
    private var pos = 0f
    private var anim: ValueAnimator? = null
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setStep(s: Int) { step = s; pos = s / 2f; invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(View.MeasureSpec.getSize(w), (44 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val cy = height / 2f
        val l = 16 * d
        val rt = width - 16 * d
        val kx = l + (rt - l) * pos
        p.style = Paint.Style.FILL
        p.color = if (C.dark) 0xFF3A3A3C.toInt() else 0xFFE5E5EA.toInt()
        c.drawRoundRect(RectF(l, cy - 1.5f * d, rt, cy + 1.5f * d), 2 * d, 2 * d, p)
        p.color = C.BLUE
        c.drawRoundRect(RectF(l, cy - 1.5f * d, kx, cy + 1.5f * d), 2 * d, 2 * d, p)
        p.color = 0x22000000
        c.drawCircle(kx, cy + 1.5f * d, 14 * d, p)
        p.color = Color.WHITE
        c.drawCircle(kx, cy, 14 * d, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val l = 16 * d
        val rt = width - 16 * d
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                anim?.cancel()
                pos = ((e.x - l) / (rt - l)).coerceIn(0f, 1f)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val ns = Math.round(pos * 2f)
                anim?.cancel()
                anim = ValueAnimator.ofFloat(pos, ns / 2f).apply {
                    duration = 200
                    interpolator = IOS_EASE
                    addUpdateListener { pos = it.animatedValue as Float; invalidate() }
                    start()
                }
                if (ns != step) { step = ns; onStep?.invoke(ns) }
            }
        }
        return true
    }
}

/** Span yang memaksa potongan teks emoji memakai font Apple Color Emoji. */
class EmojiSpan(private val tf: Typeface) : android.text.style.MetricAffectingSpan() {
    override fun updateDrawState(p: android.text.TextPaint) { p.typeface = tf }
    override fun updateMeasureState(p: android.text.TextPaint) { p.typeface = tf }
}

/**
 * Emoji selalu memakai Apple Color Emoji bawaan app (aset dari user). Tidak ada opsi untuk menggantinya.
 * Dipakai di semua teks (nama file/folder, judul) dan kolom ketik (pencarian, ganti nama).
 */
object Emoji {
    val face: Typeface? by lazy {
        try { Typeface.createFromAsset(Thumbs.ctx.assets, "fonts/AppleColorEmoji.ttf") } catch (e: Throwable) { null }
    }
    private val usable: Boolean by lazy {
        val f = face
        f != null && Paint().apply { typeface = f }.hasGlyph("\uD83D\uDE00")
    }

    private val bmpDefault = listOf(
        0x231A..0x231B, 0x23E9..0x23EC, 0x23F0..0x23F0, 0x23F3..0x23F3, 0x25FD..0x25FE, 0x2614..0x2615,
        0x2648..0x2653, 0x267F..0x267F, 0x2693..0x2693, 0x26A1..0x26A1, 0x26AA..0x26AB, 0x26BD..0x26BE,
        0x26C4..0x26C5, 0x26CE..0x26CE, 0x26D4..0x26D4, 0x26EA..0x26EA, 0x26F2..0x26F3, 0x26F5..0x26F5,
        0x26FA..0x26FA, 0x26FD..0x26FD, 0x2705..0x2705, 0x270A..0x270B, 0x2728..0x2728, 0x274C..0x274C,
        0x274E..0x274E, 0x2753..0x2755, 0x2757..0x2757, 0x2795..0x2797, 0x27B0..0x27B0, 0x27BF..0x27BF,
        0x2B1B..0x2B1C, 0x2B50..0x2B50, 0x2B55..0x2B55
    )

    private fun astral(cp: Int) = cp in 0x1F000..0x1FAFF
    private fun base(cp: Int) = astral(cp) || bmpDefault.any { cp in it }
    private fun joiner(cp: Int) = cp == 0x200D || cp == 0xFE0F || cp == 0x20E3 || cp in 0xE0020..0xE007F
    private fun keycapBase(cp: Int) = cp in 0x30..0x39 || cp == 0x23 || cp == 0x2A

    private fun start(cp: Int, next: Int): Boolean {
        if (base(cp)) return true
        if (next == 0x20E3) return keycapBase(cp)
        if (next == 0xFE0F) {
            return cp in 0x2190..0x2BFF || cp == 0xA9 || cp == 0xAE || cp == 0x203C || cp == 0x2049 ||
                cp == 0x2122 || cp == 0x2139 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299 || keycapBase(cp)
        }
        return false
    }

    private fun runs(s: CharSequence): List<Pair<Int, Int>> {
        val t = s.toString()
        val n = t.length
        val out = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < n) {
            val cp = t.codePointAt(i)
            val next = i + Character.charCount(cp)
            val nextCp = if (next < n) t.codePointAt(next) else -1
            if (start(cp, nextCp)) {
                var j = next
                var prevZwj = false
                while (j < n) {
                    val c2 = t.codePointAt(j)
                    val ok2 = joiner(c2) || base(c2) || (prevZwj && (c2 in 0x2190..0x2BFF))
                    if (!ok2) break
                    prevZwj = c2 == 0x200D
                    j += Character.charCount(c2)
                }
                out.add(Pair(i, j))
                i = j
            } else i = next
        }
        return out
    }

    fun apply(src: CharSequence?): CharSequence? {
        if (src == null || src.isEmpty()) return src
        val r = runs(src)
        if (r.isEmpty() || !usable) return src
        val sp = android.text.SpannableString(src)
        for (x in r) sp.setSpan(EmojiSpan(face!!), x.first, x.second, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
    }

    fun spanInPlace(e: Editable) {
        for (o in e.getSpans(0, e.length, EmojiSpan::class.java)) e.removeSpan(o)
        if (e.isEmpty()) return
        val r = runs(e)
        if (r.isEmpty() || !usable) return
        for (x in r) e.setSpan(EmojiSpan(face!!), x.first, x.second, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

class EmojiTextView(ctx: Context) : android.widget.TextView(ctx) {
    override fun setText(text: CharSequence?, type: android.widget.TextView.BufferType?) {
        super.setText(Emoji.apply(text), type)
    }
}

/** Warna folder (bisa diganti lewat popup): ubah hue gambar folder biru. */
object FolderColors {
    val names = arrayOf("Blue", "Purple", "Pink", "Red", "Orange", "Yellow", "Green", "Gray")
    val dots = intArrayOf(
        0xFF32ADE6.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D87.toInt(), 0xFFFF3B30.toInt(),
        0xFFFF9500.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(), 0xFF8E8E93.toInt()
    )
    private val hues = floatArrayOf(205f, 275f, 330f, 355f, 28f, 48f, 135f, -1f)
    val map = HashMap<String, Int>()
    private val cache = HashMap<Int, Bitmap>()

    fun idxOf(path: String): Int = map[path] ?: 0

    fun bitmap(idx: Int): Bitmap {
        if (idx <= 0 || idx >= hues.size) return Assets.folder
        return cache.getOrPut(idx) { recolor(Assets.folder, hues[idx]) }
    }

    fun glyph(idx: Int): Int {
        if (idx <= 0 || idx >= hues.size) return 0xB02A78AE.toInt()
        val h = hues[idx]
        return if (h < 0) 0xB0555559.toInt() else Color.HSVToColor(0xB0, floatArrayOf(h, 0.75f, 0.6f))
    }

    private fun recolor(src: Bitmap, hue: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 0) continue
            Color.colorToHSV(c, hsv)
            if (hsv[1] > 0.18f) {
                if (hue < 0) { hsv[1] *= 0.12f; hsv[2] *= 0.85f } else hsv[0] = hue
                px[i] = Color.HSVToColor(a, hsv)
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
