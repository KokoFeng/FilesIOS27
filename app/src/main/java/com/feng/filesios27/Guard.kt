package com.feng.filesios27

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.File
import java.security.MessageDigest

/**
 * Pemeriksaan keaslian app (anti-modifikasi). Mengembalikan alasan kalau app terdeteksi
 * dimodifikasi: tanda tangan berbeda, paket diganti, atau ada alat hook / debugger.
 */
object Guard {
    // SHA-256 sertifikat penandatangan resmi (app/feng-files.jks)
    private const val SIG = "dbf118757e39376f9c6734d063d58b42fb8fa95771d2393b24625ed5e7c95602"
    private const val PKG = "com.feng.filesios27"

    fun check(ctx: Context): String? {
        if (ctx.packageName != PKG) return "package"
        if (!sigOk(ctx)) return "signature"
        if (hooked()) return "hook"
        return null
    }

    @Suppress("DEPRECATION")
    private fun sigOk(ctx: Context): Boolean {
        return try {
            val pm = ctx.packageManager
            val sigs = if (Build.VERSION.SDK_INT >= 28)
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            else pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
            if (sigs == null || sigs.isEmpty()) false
            else sigs.any { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) == SIG }
        } catch (e: Throwable) {
            false
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun hooked(): Boolean {
        try { if (Debug.isDebuggerConnected()) return true } catch (e: Throwable) {}
        try { Class.forName("de.robv.android.xposed.XposedBridge"); return true } catch (e: Throwable) {}
        try {
            for (l in File("/proc/self/status").readLines()) {
                if (l.startsWith("TracerPid:") && l.substringAfter(':').trim() != "0") return true
            }
        } catch (e: Throwable) {}
        try {
            val bad = listOf("frida", "lsposed", "libxposed", "substrate")
            val hit = File("/proc/self/maps").useLines { seq ->
                seq.any { line -> val x = line.lowercase(); bad.any { x.contains(it) } }
            }
            if (hit) return true
        } catch (e: Throwable) {}
        return false
    }
}
