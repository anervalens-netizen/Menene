package ro.mehene.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.SeriesItem
import ro.mehene.app.util.NameFormatter
import ro.mehene.app.util.NaturalOrderComparator
import java.io.FileNotFoundException

class LibraryRepository(private val context: Context) {
    private val preferences = LibraryPreferences(context)

    fun persistLibraryUri(uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val previousUri = preferences.libraryUri
        preferences.libraryUri = uri.toString()
        if (previousUri != null && previousUri != uri.toString()) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(previousUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    fun scanSeries(): LibraryResult<List<SeriesItem>> = withReadableRoot { root ->
        val directories = root.listFiles()
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }

        var ignoredVideoCount = 0
        val series = directories.mapNotNull { directory ->
            val files = directory.listFiles().toList()
            ignoredVideoCount += files.count { looksLikeVideo(it) && !isSupportedVideo(it) }
            val episodes = files.filter(::isSupportedVideo)
            if (episodes.isEmpty()) return@mapNotNull null

            val cover = findSeriesArtwork(files)
            SeriesItem(
                title = NameFormatter.displayName(directory.name.orEmpty()),
                directoryUri = directory.uri.toString(),
                coverUri = cover?.uri?.toString(),
                coverVersion = cover?.lastModified() ?: 0L,
                episodeCount = episodes.size,
            )
        }.sortedWith(compareBy(NaturalOrderComparator) { it.title })

        LibraryResult.Success(series, ignoredVideoCount)
    }

    fun scanEpisodes(seriesDirectoryUri: String): LibraryResult<List<EpisodeItem>> = withReadableRoot { root ->
        val directory = root.listFiles()
            .firstOrNull { it.isDirectory && it.uri.toString() == seriesDirectoryUri }
            ?: return@withReadableRoot LibraryResult.StorageUnavailable

        val files = directory.listFiles().toList()
        val imagesByBaseName = files
            .filter(::isImage)
            .associateBy { LibraryFileRules.baseName(it.name.orEmpty()).lowercase() }
        val ignoredVideoCount = files.count { looksLikeVideo(it) && !isSupportedVideo(it) }

        val episodes = files
            .filter(::isSupportedVideo)
            .sortedWith(compareBy(NaturalOrderComparator) { it.name.orEmpty() })
            .mapIndexed { index, file ->
                val artwork = imagesByBaseName[LibraryFileRules.baseName(file.name.orEmpty()).lowercase()]
                EpisodeItem(
                    title = NameFormatter.displayName(file.name.orEmpty()),
                    mediaUri = file.uri.toString(),
                    playbackKey = buildPlaybackKey(file),
                    artworkUri = artwork?.uri?.toString(),
                    artworkVersion = artwork?.lastModified() ?: 0L,
                    number = index + 1,
                )
            }

        LibraryResult.Success(episodes, ignoredVideoCount)
    }

    fun isConfigured(): Boolean = preferences.libraryUri != null

    fun isLibraryReadable(): Boolean = resolveRoot() is LibraryResult.Success

    private fun <T> withReadableRoot(
        block: (DocumentFile) -> LibraryResult<T>,
    ): LibraryResult<T> {
        val rootResult = resolveRoot()
        val root = when (rootResult) {
            is LibraryResult.Success -> rootResult.value
            LibraryResult.NotConfigured -> return LibraryResult.NotConfigured
            LibraryResult.PermissionLost -> return LibraryResult.PermissionLost
            LibraryResult.StorageUnavailable -> return LibraryResult.StorageUnavailable
            is LibraryResult.Failure -> return rootResult
        }

        return try {
            block(root)
        } catch (error: SecurityException) {
            LibraryResult.PermissionLost
        } catch (error: FileNotFoundException) {
            LibraryResult.StorageUnavailable
        } catch (error: Throwable) {
            LibraryResult.Failure(error)
        }
    }

    private fun resolveRoot(): LibraryResult<DocumentFile> {
        val uriString = preferences.libraryUri ?: return LibraryResult.NotConfigured
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return LibraryResult.PermissionLost
        val hasPermission = context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }
        if (!hasPermission) return LibraryResult.PermissionLost

        val root = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
            ?: return LibraryResult.StorageUnavailable
        return if (root.canRead()) {
            LibraryResult.Success(root)
        } else {
            LibraryResult.StorageUnavailable
        }
    }

    private fun findSeriesArtwork(files: List<DocumentFile>): DocumentFile? {
        val images = files.filter(::isImage)
        return LibraryFileRules.coverPriorityNames.firstNotNullOfOrNull { priority ->
            images.firstOrNull { LibraryFileRules.baseName(it.name.orEmpty()).equals(priority, ignoreCase = true) }
        }
    }

    private fun isSupportedVideo(file: DocumentFile): Boolean {
        if (!file.isFile) return false
        return LibraryFileRules.isSupportedVideoName(file.name.orEmpty()) || file.type in SUPPORTED_VIDEO_MIME_TYPES
    }

    private fun looksLikeVideo(file: DocumentFile): Boolean {
        if (!file.isFile) return false
        return LibraryFileRules.isKnownVideoName(file.name.orEmpty()) ||
            file.type?.startsWith("video/") == true
    }

    private fun isImage(file: DocumentFile): Boolean {
        if (!file.isFile) return false
        return LibraryFileRules.isImageName(file.name.orEmpty()) || file.type?.startsWith("image/") == true
    }

    private fun buildPlaybackKey(file: DocumentFile): String = buildString {
        append(file.uri)
        append('|')
        append(file.length())
        append('|')
        append(file.lastModified())
    }

    companion object {
        private val SUPPORTED_VIDEO_MIME_TYPES = setOf("video/mp4", "video/x-m4v")
    }
}
