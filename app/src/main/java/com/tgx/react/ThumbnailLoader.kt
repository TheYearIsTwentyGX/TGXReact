package com.tgx.react

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/** Decodes downsampled thumbnails off the main thread. Animated images (GIF/WebP) play. */
object ThumbnailLoader {
    private val executor = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val bitmaps = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun load(view: ImageView, file: File, sizePx: Int) {
        val key = "${file.path}@$sizePx"
        if (view.tag == key) return
        view.tag = key
        (view.drawable as? AnimatedImageDrawable)?.stop()
        bitmaps.get(key)?.let { view.setImageBitmap(it); return }
        view.setImageDrawable(null)
        executor.execute {
            val d = decode(file, sizePx) ?: return@execute
            if (d is BitmapDrawable) bitmaps.put(key, d.bitmap)
            main.post {
                if (view.tag != key) return@post
                view.setImageDrawable(d)
                (d as? AnimatedImageDrawable)?.start()
            }
        }
    }

    private fun decode(file: File, sizePx: Int): Drawable? = runCatching {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longest / (sample * 2) >= sizePx) sample *= 2
            decoder.setTargetSampleSize(sample)
        }
    }.getOrNull()
}
