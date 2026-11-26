package app.monolauncher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Valódi alkalmazásikonok betöltése.
 *  - mono = true : a "témázott" (monochrome) ikonréteg fehérre festve = vonalas, fehér ikon.
 *                  Ha az app nem ad ilyet, az eredeti ikon szürkeárnyalatos változata jelenik meg.
 *  - mono = false: eredeti, színes ikon.
 */
object IconLoader {
    private const val SIZE = 96
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    private val totalNs = AtomicLong(0)
    private val loaded = AtomicLong(0)

    fun clear() = cache.clear()
    fun count(): Long = loaded.get()
    fun avgMs(): Double = if (loaded.get() == 0L) 0.0 else totalNs.get() / 1e6 / loaded.get()

    suspend fun load(ctx: Context, key: String, mono: Boolean): ImageBitmap? =
        withContext(Dispatchers.Default) {
            val cacheKey = (if (mono) "m:" else "o:") + key
            cache[cacheKey] ?: run {
                val t0 = System.nanoTime()
                val bmp = render(ctx, key, mono)
                if (bmp != null) {
                    totalNs.addAndGet(System.nanoTime() - t0)
                    loaded.incrementAndGet()
                    cache[cacheKey] = bmp
                }
                bmp
            }
        }

    private fun render(ctx: Context, key: String, mono: Boolean): ImageBitmap? {
        val cn = ComponentName.unflattenFromString(key) ?: return null
        val drawable = try {
            ctx.packageManager.getActivityIcon(cn)
        } catch (e: Exception) {
            return null
        }

        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        if (!mono) {
            drawable.setBounds(0, 0, SIZE, SIZE)
            drawable.draw(canvas)
            return out.asImageBitmap()
        }

        val monoLayer = (drawable as? AdaptiveIconDrawable)?.monochrome
        if (monoLayer != null) {
            val layer = monoLayer.mutate()
            // Az adaptív ikon 108dp-s vászon, a látható rész 72dp. Kicsit kisebbre vesszük,
            // hogy a jel ne töltse ki teljesen a dobozt.
            val pad = (SIZE * 0.14f).toInt()
            layer.setBounds(-pad, -pad, SIZE + pad, SIZE + pad)
            layer.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
            layer.draw(canvas)
            return out.asImageBitmap()
        }

        // Tartalék: szürkeárnyalatos eredeti ikon.
        val tmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, SIZE, SIZE)
        drawable.draw(Canvas(tmp))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(tmp, 0f, 0f, paint)
        return out.asImageBitmap()
    }
}
