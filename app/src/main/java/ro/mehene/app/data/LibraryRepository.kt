package ro.mehene.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.SeriesItem
import ro.mehene.app.util.NameFormatter
import ro.mehene.app.util.NaturalOrderComparator

class LibraryRepository(private val context: Context) {
    private val preferences = LibraryPreferences(context)

    fun scanSeries(): List<SeriesItem> {
        val root = rootDocument() ?: return emptyList()
        return root.listFiles()
            .asSequence()
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .mapNotNull { directory ->
                val files = safelyList(directory)
                val episodes = files.filter(::isVideo)
                if (episodes.isEmpty()) return@mapNotNull null

                SeriesItem(
                    title = NameFormatter.displayName(directory.name.orEmpty()),
                    directoryUri = directory.uri.toString(),
                    coverUri = findSeriesArtwork(files)?.uri?.toString(),
                    episodeCount = episodes.size,
                )
            }
            .sortedWith(compareBy(NaturalOrderComparator) { it.title })
            .toList()
    }

    fun scanEpisodes(seriesDirectoryUri: String): List<EpisodeItem> {
        val root = rootDocument() ?: return emptyList()
        val directory = safelyList(root)
            .firstOrNull { it.isDirectory && it.uri.toString() == seriesDirectoryUri }
            ?: return emptyList()
        val files = safelyList(directory)
        val imagesByBaseName = files
            .filter(::isImage)
            .associateBy { baseName(it.name.orEmpty()).lowercase() }

        return files
            .filter(::isVideo)
            .sortedWith(compareBy(NaturalOrderComparator) { it.name.orEmpty() })
            .mapIndexed { index, file ->
                EpisodeItem(
                    title = NameFormatter.displayName(file.name.orEmpty()),
                    mediaUri = file.uri.toString(),
                    artworkUri = imagesByBaseName[baseName(file.name.orEmpty()).lowercase()]?.uri?.toString(),
                    number = index + 1,
                )
            }
    }

    fun isConfigured(): Boolean = preferences.libraryUri != null

    fun isLibraryReadable(): Boolean = rootDocument()?.canRead() == true

    private fun rootDocument(): DocumentFile? {
        val uri = preferences.libraryUri?.let(Uri::parse) ?: return null
        return runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
    }

    private fun safelyList(documentFile: DocumentFile): List<DocumentFile> =
        runCatching { documentFile.listFiles().toList() }.getOrDefault(emptyList())

    private fun findSeriesArtwork(files: List<DocumentFile>): DocumentFile? {
        val images = files.filter(::isImage)
        val priorityNames = listOf("cover", "poster", "folder", "serial")
        return priorityNames.firstNotNullOfOrNull { priority ->
            images.firstOrNull { baseName(it.name.orEmpty()).equals(priority, ignoreCase = true) }
        } ?: images.firstOrNull()
    }

    private fun isVideo(file: DocumentFile): Boolean {
        if (!file.isFile) return false
        val extension = extension(file.name.orEmpty())
        return extension in VIDEO_EXTENSIONS || file.type?.startsWith("video/") == true
    }

    private fun isImage(file: DocumentFile): Boolean {
        if (!file.isFile) return false
        val extension = extension(file.name.orEmpty())
        return extension in IMAGE_EXTENSIONS || file.type?.startsWith("image/") == true
    }

    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase()
    private fun baseName(name: String) = name.substringBeforeLast('.', name)

    companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
