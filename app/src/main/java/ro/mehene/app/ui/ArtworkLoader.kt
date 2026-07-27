package ro.mehene.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import ro.mehene.app.R
import java.util.concurrent.Executors

object ArtworkLoader {
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(
        context: Context,
        uriString: String,
        imageView: ImageView,
        onFailure: () -> Unit = {},
    ) {
        imageView.setTag(R.id.artwork_uri_tag, uriString)
        cache.get(uriString)?.let { cached ->
            imageView.setImageBitmap(cached)
            return
        }

        val appContext = context.applicationContext
        executor.execute {
            val bitmap = decodeSampledBitmap(appContext, Uri.parse(uriString), 720, 420)
            if (bitmap != null) cache.put(uriString, bitmap)
            mainHandler.post {
                if (imageView.getTag(R.id.artwork_uri_tag) != uriString) return@post
                if (bitmap != null) imageView.setImageBitmap(bitmap) else onFailure()
            }
        }
    }

    private fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, requestedWidth, requestedHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Int {
        var sampleSize = 1
        var width = options.outWidth
        var height = options.outHeight
        while (width / 2 >= requestedWidth && height / 2 >= requestedHeight) {
            width /= 2
            height /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun cacheSizeKb(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        return (maxMemoryKb / 12).coerceAtLeast(4 * 1024)
    }
}
