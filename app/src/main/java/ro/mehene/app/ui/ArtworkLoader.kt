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
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

object ArtworkLoader {
    private data class PendingRequest(
        val imageView: WeakReference<ImageView>,
        val cacheKey: String,
        val onFailure: () -> Unit,
    )

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = mutableMapOf<String, MutableList<PendingRequest>>()
    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(
        context: Context,
        uriString: String,
        version: Long,
        imageView: ImageView,
        requestedWidth: Int = 480,
        requestedHeight: Int = 300,
        onFailure: () -> Unit = {},
    ) {
        val cacheKey = "$uriString#$version@${requestedWidth}x$requestedHeight"
        imageView.setTag(R.id.artwork_uri_tag, cacheKey)
        cache.get(cacheKey)?.let { cached ->
            imageView.setImageBitmap(cached)
            return
        }

        val request = PendingRequest(WeakReference(imageView), cacheKey, onFailure)
        val shouldStartDecode = synchronized(pending) {
            val requests = pending[cacheKey]
            if (requests == null) {
                pending[cacheKey] = mutableListOf(request)
                true
            } else {
                requests += request
                false
            }
        }
        if (!shouldStartDecode) return

        val appContext = context.applicationContext
        executor.execute {
            val bitmap = decodeSampledBitmap(appContext, Uri.parse(uriString), requestedWidth, requestedHeight)
            if (bitmap != null) cache.put(cacheKey, bitmap)
            val requests = synchronized(pending) { pending.remove(cacheKey).orEmpty() }
            mainHandler.post {
                requests.forEach { pendingRequest ->
                    val target = pendingRequest.imageView.get() ?: return@forEach
                    if (target.getTag(R.id.artwork_uri_tag) != pendingRequest.cacheKey) return@forEach
                    if (bitmap != null) {
                        target.setImageBitmap(bitmap)
                    } else {
                        pendingRequest.onFailure()
                    }
                }
            }
        }
    }

    fun cancel(imageView: ImageView) {
        imageView.setTag(R.id.artwork_uri_tag, null)
        imageView.setImageDrawable(null)
    }

    fun clearCache() {
        cache.evictAll()
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
        return (maxMemoryKb / 12).coerceAtLeast(2 * 1024)
    }
}
