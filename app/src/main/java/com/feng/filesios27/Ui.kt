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
    val BG = 0xFFF2F2F7.toInt()
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
    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(0x1F000000))
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
    const val MIC = "M12,3a3,3 0 0,0 -3,3v5a3,3 0 0,0 6,0V6a3,3 0 0,0 -3,-3z M6,11a6,6 0 0,0 12,0 M12,17v4"
    const val MORE = "M6,12L6.01,12M12,12L12.01,12M18,12L18.01,12"
    const val COPY = "M8,8h11v12h-11z M5,16V4h11"
    const val FOLDER = "M3,7a2,2 0 0,1 2,-2h4l2,2h8a2,2 0 0,1 2,2v8a2,2 0 0,1 -2,2h-14a2,2 0 0,1 -2,-2z"
    const val NEWFOLDER = "M3,7a2,2 0 0,1 2,-2h4l2,2h8a2,2 0 0,1 2,2v8a2,2 0 0,1 -2,2h-14a2,2 0 0,1 -2,-2z M12,10v5 M9.5,12.5h5"
    const val SHARE = "M12,15V4 M8,8l4,-4l4,4 M6,11H5v9h14v-9h-1"
    const val TRASH = "M5,7h14 M10,7V4h4v3 M7,7l1,13h8l1,-13"
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
    const val PHONE = "M8,3h8a1.5,1.5 0 0,1 1.5,1.5v15a1.5,1.5 0 0,1 -1.5,1.5h-8a1.5,1.5 0 0,1 -1.5,-1.5v-15a1.5,1.5 0 0,1 1.5,-1.5z M11,18h2"
    const val CHECK = "M5,12.5L10,17.5L19,7"
    const val GRID = "M4,4h6v6h-6z M14,4h6v6h-6z M4,14h6v6h-6z M14,14h6v6h-6z"
    const val LIST = "M4,6h16 M4,12h16 M4,18h16"
    const val PLAY = "M7,4L20,12L7,20z"
    const val PAUSE = "M6,4h4v16h-4z M14,4h4v16h-4z"
    const val SHAREDFOLDER = "M3,7a2,2 0 0,1 2,-2h4l2,2h8a2,2 0 0,1 2,2v8a2,2 0 0,1 -2,2h-14a2,2 0 0,1 -2,-2z M12,9.5a1.5,1.5 0 1,0 0.01,0 M9.5,16c0,-2 1.2,-3 2.5,-3s2.5,1 2.5,3"
    const val CLOUD = "M7,18a4,4 0 0,1 -0.5,-8a5.5,5.5 0 0,1 10.7,-1.2a4.2,4.2 0 0,1 0.8,9.2z"
    const val MARKUP = "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18 M8.5,16l3.5,-9l3.5,9 M9.8,13h4.4"
    const val DESKTOP = "M4,5h16a1,1 0 0,1 1,1v10a1,1 0 0,1 -1,1h-16a1,1 0 0,1 -1,-1v-10a1,1 0 0,1 1,-1z M7,13.5h10"
    const val DOCGLYPH = "M7,3h7l4,4v13a1,1 0 0,1 -1,1h-10a1,1 0 0,1 -1,-1v-16a1,1 0 0,1 1,-1z M14,3v4h4"
    const val DLCIRCLE = "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18 M12,8v8 M8.5,12.5l3.5,3.5l3.5,-3.5"
    const val PHOTO = "M4,5h16v14h-16z M4,16l5,-5l4,4l3,-3l4,4 M15.5,9.5a1,1 0 1,0 0.01,0"
    const val FILM = "M4,5h16v14h-16z M10,9l5,3l-5,3z"
    const val CAMERA = "M4,8h3l2,-3h6l2,3h3v11h-16z M12,10a3.5,3.5 0 1,0 0.01,0"
    const val PACKAGE = "M12,3l8,4v10l-8,4l-8,-4v-10z M4,7l8,4l8,-4 M12,11v10"
    const val APPA = "M12,4l-6,14 M12,4l6,14 M8,13.5h8"
    const val CLOSE = "M6,6L18,18 M18,6L6,18"
    const val OPENIN = "M14,4h6v6 M20,4l-9,9 M18,14v4a2,2 0 0,1 -2,2h-9a2,2 0 0,1 -2,-2v-9a2,2 0 0,1 2,-2h4"
    const val NOTE = "M9,18V5l12,-2v13 M9,18a3,3 0 1,1 -6,0a3,3 0 0,1 6,0 M21,16a3,3 0 1,1 -6,0a3,3 0 0,1 6,0"
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
        setImageDrawable(IconDrawable(path, col, stroke, sw))
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

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val rd = rad()
        r.set(b)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, top, bottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rd, rd, p)
        p.shader = LinearGradient(
            0f, r.top, 0f, r.top + r.height() * 0.5f,
            if (dark) 0x26FFFFFF else 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, rd, rd, p)
        val sw = 1.3f * d
        r.set(b.left + sw / 2, b.top + sw / 2, b.right - sw / 2, b.bottom - sw / 2)
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        val rim = if (dark) intArrayOf(0x99FFFFFF.toInt(), 0x14FFFFFF, 0x14FFFFFF, 0x66FFFFFF)
        else intArrayOf(0xF2FFFFFF.toInt(), 0x30FFFFFF, 0x30FFFFFF, 0xCCFFFFFF.toInt())
        p.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            rim, floatArrayOf(0f, 0.32f, 0.68f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, maxOf(0f, rd - sw / 2), maxOf(0f, rd - sw / 2), p)
        val s2 = 3f * d
        r.set(b.left + sw + s2 / 2, b.top + sw + s2 / 2, b.right - sw - s2 / 2, b.bottom - sw - s2 / 2)
        p.strokeWidth = s2
        p.shader = LinearGradient(
            0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
            if (dark) 0x14FFFFFF else 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        val ir = maxOf(0f, rd - sw - s2 / 2)
        c.drawRoundRect(r, ir, ir, p)
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
    var refraction: Float = 0.5f,     // 0..1 seberapa jauh latar dibelokkan (Android 13+)
    var curve: Float = 0.5f,          // 0..1 kelengkungan lensa pusat vs tepi (Android 13+)
    var edge: Float = 0.12f,          // kilau pinggiran
    var tintColor: Int = 0x2EFFFFFF,
    var saturation: Float = 1.5f,
    var dispersion: Float = 0.25f,    // aberasi kromatik di tepi (Android 13+)
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
    private val params: LiquidParams = LiquidParams()
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
            float bevel = min(viewSize.x, viewSize.y) * 0.5 * (0.35 + 0.65 * curve);
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
        if (Build.VERSION.SDK_INT < 31) { c.drawColor(0xE6F2F2F7.toInt()); return }
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

    override fun onDraw(canvas: Canvas) {
        val f = file ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        if (f.isDirectory) {
            val fb = Assets.folder
            val r = fit(fb, w, h, 0.98f, 0.98f)
            canvas.drawBitmap(fb, null, r, bp)
            val g = folderGlyph(f.name)
            if (g != null) {
                val s = r.height() * 0.34f
                val ic = Glyph.get(g, 0xB02A78AE.toInt(), true, 1.9f)
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
        highlightColor = 0x55007AFF
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
        if (kind == 0) { st.color = Color.WHITE; st.strokeWidth = 1.9f; c.drawPath(hands, st) }
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
