package com.zwz.offlineledger.ui.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.UUID

/**
 * 第 82 批：账户自定义图标的**本机**导入与加载。
 *
 * 与第 64 批 26 个预置账户 PNG **同构**：统一 128×128、白圆底、图形居中内缩
 * （`SIZE * 0.56`），这样导入图标与预制图标放进同一个 `AccountIconBox` 时
 * 尺寸 / 圆底 / 留白完全一致，MUST NOT 出现「一张彩色 logo、一张方图」的拼盘感。
 * 导入图形统一染成中性深灰单色剪影（对应 Tabler 单色 tint 的观感），
 * 不是把它变成 Tabler 线性笔画 —— 位图无法在零依赖下描摹，如实登记。
 *
 * 只收**透明底 PNG**：无 alpha 通道的普通照片会被拒绝并给出提示，
 * MUST NOT 做自动抠图（零依赖做不到，也不假装 1:1）。
 * 全程只用 `BitmapFactory` / `Canvas` / 文件流：
 * 零新增 Gradle 依赖、零网络、零运行时权限、零 Coil/Glide、零 SVG 解析库。
 *
 * 文件落在应用私有 `filesDir/account-icons/`，**不进备份、不随 APK 分发**。
 * 授权口径与 §7.46「随包分发需逐项审计」不同：这是使用者自己选的本机文件，
 * `assets/NOTICE` 另设段落说明「仅本机使用、许可由使用者自行确认」。
 */
object AccountCustomIcons {

    private const val DIR = "account-icons"
    private const val SIZE = 128
    private const val GLYPH_R = 0x4A
    private const val GLYPH_G = 0x50
    private const val GLYPH_B = 0x5C

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun file(context: Context, uuid: String): File =
        File(dir(context), "$uuid.png")

    /** 返回 `custom:<uuid>`；失败时把可读原因放进 exception.message。 */
    fun import(context: Context, uri: Uri): Result<String> = runCatching {
        val resolver = context.contentResolver

        // 先取边界，超大图按 2 的幂降采样，避免 decode 全图 OOM。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("读不到所选文件，请重新选择")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "不是有效的 PNG 图片" }
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        require(maxSide <= 8192) { "图片过大（最长边 $maxSide），请用 1024 以内的图标 PNG" }
        var sample = 1
        while (maxSide / sample > 2048) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("读不到所选文件，请重新选择")
        val src = decoded.copy(Bitmap.Config.ARGB_8888, false)
        if (src !== decoded) decoded.recycle()

        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        // ① 校验透明底：必须存在足够多的半透明 / 全透明像素，
        //    否则就是普通照片，自动抠图不在本批能力范围。
        var transparent = 0
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = AColor.alpha(px[y * w + x])
                if (a < 250) transparent++
                if (a > 8) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        require(transparent > 0) {
            "请使用透明底 PNG（当前图片没有透明区域），App 不做自动抠图"
        }
        require(maxX >= minX && maxY >= minY) { "图片里没有可见图形" }

        // ② 按非透明 bbox 裁成正方形（居中扩展），避免图形被拉伸变形。
        val side = maxOf(maxX - minX + 1, maxY - minY + 1)
        val cx = (minX + maxX) / 2
        val cy = (minY + maxY) / 2
        val left = (cx - side / 2).coerceIn(0, (w - side).coerceAtLeast(0))
        val top = (cy - side / 2).coerceIn(0, (h - side).coerceAtLeast(0))
        val cropSide = minOf(side, w - left, h - top)
        val glyph = Bitmap.createBitmap(src, left, top, cropSide, cropSide)
        src.recycle()

        // ③ 单色剪影：保留 alpha，RGB 统一换成中性深灰。
        val gp = IntArray(cropSide * cropSide)
        glyph.getPixels(gp, 0, cropSide, 0, 0, cropSide, cropSide)
        for (i in gp.indices) {
            val a = AColor.alpha(gp[i])
            gp[i] = if (a > 0) AColor.argb(a, GLYPH_R, GLYPH_G, GLYPH_B) else 0
        }
        glyph.setPixels(gp, 0, cropSide, 0, 0, cropSide, cropSide)

        // ④ 与第 64 批预置资源同构：128×128 白圆底 + 图形 56% 内缩。
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AColor.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, bg)
        val inner = (SIZE * 0.56f).toInt()
        val pad = (SIZE - inner) / 2
        canvas.drawBitmap(
            glyph, null,
            Rect(pad, pad, SIZE - pad, SIZE - pad),
            Paint(Paint.ANTI_ALIAS_FLAG)
        )
        glyph.recycle()

        val uuid = UUID.randomUUID().toString()
        file(context, uuid).outputStream().use {
            if (!out.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                error("图标写入失败")
            }
        }
        out.recycle()
        "custom:$uuid"
    }

    /** 供 Composable `remember(key) { ... }` 包住，避免每次重组都 decode。 */
    fun load(context: Context, ref: String): ImageBitmap? {
        if (!ref.startsWith("custom:")) return null
        val f = file(context, ref.removePrefix("custom:"))
        if (!f.isFile) return null
        return runCatching {
            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }

    fun delete(context: Context, ref: String) {
        if (!ref.startsWith("custom:")) return
        runCatching { file(context, ref.removePrefix("custom:")).delete() }
    }
}
