package ro.mehene.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ro.mehene.app.model.CatalogSource
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.LibraryCatalog
import ro.mehene.app.model.LibraryDiagnostics
import ro.mehene.app.model.SeasonItem
import ro.mehene.app.model.SeriesItem
import ro.mehene.app.util.NameFormatter
import ro.mehene.app.util.NaturalOrderComparator
import kotlin.system.measureTimeMillis

class LibraryRepository(
    private val context: Context,
    private val cacheStore: CatalogCacheStore,
) {
    private val preferences = LibraryPreferences(context)
    private val scanMutex = Mutex()

    @Volatile
    private var memoryCatalog: LibraryCatalog? = null

    @Volatile
    private var memorySourceFingerprint: String? = null

    @Volatile
    private var memoryCachedAtEpochMs: Long = 0L

    fun isConfigured(): Boolean = preferences.libraryUri != null

    fun cachedCatalog(): LibraryCatalog? = memoryCatalog

    suspend fun loadCatalog(forceRefresh: Boolean = false): LibraryResult<LibraryCatalog> =
        withContext(Dispatchers.IO) {
            scanMutex.withLock {
                val rootResult = resolveRoot(preferences.libraryUri)
                val root = when (rootResult) {
                    is LibraryResult.Success -> rootResult.value
                    LibraryResult.NotConfigured -> return@withLock LibraryResult.NotConfigured
                    LibraryResult.PermissionLost -> return@withLock LibraryResult.PermissionLost
                    LibraryResult.StorageUnavailable -> return@withLock LibraryResult.StorageUnavailable
                    is LibraryResult.Failure -> return@withLock rootResult
                }
                val rootUri = preferences.libraryUri ?: return@withLock LibraryResult.NotConfigured
                val generatedCatalogText = readGeneratedCatalog(root)
                val sourceFingerprint = generatedCatalogText?.let(::sha256)
                val now = System.currentTimeMillis()

                if (!forceRefresh) {
                    memoryCatalog?.takeIf { catalog ->
                        catalog.rootUri == rootUri && when (catalog.source) {
                            CatalogSource.GENERATED_CATALOG ->
                                sourceFingerprint != null && memorySourceFingerprint == sourceFingerprint
                            CatalogSource.FOLDER_SCAN, CatalogSource.INTERNAL_CACHE ->
                                now - memoryCachedAtEpochMs <= MEMORY_FOLDER_SCAN_TTL_MS
                        }
                    }?.let { return@withLock LibraryResult.Success(it) }

                    cacheStore.load(
                        expectedRootUri = rootUri,
                        expectedSourceFingerprint = sourceFingerprint,
                        maxAgeMs = DISK_FOLDER_SCAN_TTL_MS,
                        nowEpochMs = now,
                    )?.let { cached ->
                        memoryCatalog = cached
                        memorySourceFingerprint = sourceFingerprint
                        memoryCachedAtEpochMs = now
                        return@withLock LibraryResult.Success(cached)
                    }
                }

                scanRoot(root, rootUri, generatedCatalogText, sourceFingerprint).also { result ->
                    if (result is LibraryResult.Success) {
                        val cacheFingerprint = sourceFingerprint.takeIf {
                            result.value.source == CatalogSource.GENERATED_CATALOG
                        }
                        remember(result.value, cacheFingerprint)
                        runCatching { cacheStore.save(result.value, cacheFingerprint) }
                    }
                }
            }
        }

    suspend fun persistLibraryUri(uri: Uri): LibraryResult<LibraryCatalog> = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            val oldUri = preferences.libraryUri
            val uriString = uri.toString()
            val permissionTaken = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (!permissionTaken) return@withLock LibraryResult.PermissionLost

            val newRoot = when (val resolved = resolveRoot(uriString, requirePersistedPermission = true)) {
                is LibraryResult.Success -> resolved.value
                LibraryResult.NotConfigured -> {
                    releasePermission(uriString, except = oldUri)
                    return@withLock LibraryResult.NotConfigured
                }
                LibraryResult.PermissionLost -> {
                    releasePermission(uriString, except = oldUri)
                    return@withLock LibraryResult.PermissionLost
                }
                LibraryResult.StorageUnavailable -> {
                    releasePermission(uriString, except = oldUri)
                    return@withLock LibraryResult.StorageUnavailable
                }
                is LibraryResult.Failure -> {
                    releasePermission(uriString, except = oldUri)
                    return@withLock resolved
                }
            }

            val generatedCatalogText = readGeneratedCatalog(newRoot)
            val sourceFingerprint = generatedCatalogText?.let(::sha256)
            val scanned = scanRoot(newRoot, uriString, generatedCatalogText, sourceFingerprint)
            if (scanned !is LibraryResult.Success) {
                releasePermission(uriString, except = oldUri)
                return@withLock scanned
            }

            preferences.libraryUri = uriString
            cacheStore.clear()
            val cacheFingerprint = sourceFingerprint.takeIf {
                scanned.value.source == CatalogSource.GENERATED_CATALOG
            }
            remember(scanned.value, cacheFingerprint)
            runCatching { cacheStore.save(scanned.value, cacheFingerprint) }
            if (oldUri != null && oldUri != uriString) releasePermission(oldUri)
            scanned
        }
    }

    suspend fun findSeries(seriesId: String): SeriesItem? =
        when (val result = loadCatalog()) {
            is LibraryResult.Success -> result.value.findSeries(seriesId)
            else -> null
        }

    suspend fun findEpisode(episodeId: String): EpisodeItem? =
        when (val result = loadCatalog()) {
            is LibraryResult.Success -> result.value.findEpisode(episodeId)
            else -> null
        }

    suspend fun diagnostics(forceRefresh: Boolean = false): LibraryResult<LibraryDiagnostics> =
        when (val result = loadCatalog(forceRefresh)) {
            is LibraryResult.Success -> LibraryResult.Success(result.value.diagnostics)
            LibraryResult.NotConfigured -> LibraryResult.NotConfigured
            LibraryResult.PermissionLost -> LibraryResult.PermissionLost
            LibraryResult.StorageUnavailable -> LibraryResult.StorageUnavailable
            is LibraryResult.Failure -> result
        }

    private fun remember(catalog: LibraryCatalog, sourceFingerprint: String?) {
        memoryCatalog = catalog
        memorySourceFingerprint = sourceFingerprint
        memoryCachedAtEpochMs = System.currentTimeMillis()
    }

    private suspend fun scanRoot(
        root: DocumentFile,
        rootUri: String,
        generatedCatalogText: String?,
        sourceFingerprint: String?,
    ): LibraryResult<LibraryCatalog> {
        return try {
            var catalog: LibraryCatalog? = null
            var generatedCatalogFailure: String? = null
            val duration = measureTimeMillis {
                currentCoroutineContext().ensureActive()
                if (!generatedCatalogText.isNullOrBlank()) {
                    catalog = try {
                        CatalogJsonCodec.decode(root, rootUri, generatedCatalogText, 0L).also {
                            CatalogValidator.requireValid(it)
                        }
                    } catch (error: Throwable) {
                        generatedCatalogFailure = "catalog.json invalid; scanare foldere: ${error.message.orEmpty()}"
                        null
                    }
                }
                if (catalog == null) catalog = scanFolders(root, rootUri)
            }
            val finalCatalog = requireNotNull(catalog).let { scanned ->
                val validation = CatalogValidator.requireValid(scanned)
                val warningText = buildList {
                    generatedCatalogFailure?.let(::add)
                    addAll(validation.warnings)
                }.takeIf(List<String>::isNotEmpty)?.joinToString("; ")
                scanned.copy(
                    diagnostics = scanned.diagnostics.copy(
                        scanDurationMs = duration,
                        lastError = warningText ?: scanned.diagnostics.lastError,
                    ),
                    source = if (sourceFingerprint != null && generatedCatalogFailure == null) {
                        CatalogSource.GENERATED_CATALOG
                    } else {
                        CatalogSource.FOLDER_SCAN
                    },
                )
            }
            LibraryResult.Success(finalCatalog)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            LibraryResult.PermissionLost
        } catch (error: FileNotFoundException) {
            LibraryResult.StorageUnavailable
        } catch (error: Throwable) {
            LibraryResult.Failure(error)
        }
    }

    private suspend fun scanFolders(root: DocumentFile, rootUri: String): LibraryCatalog {
        var ignoredVideoCount = 0
        var missingSeriesArtworkCount = 0
        var missingEpisodeArtworkCount = 0
        val seriesItems = mutableListOf<SeriesItem>()

        val seriesDirectories = root.listFiles()
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .sortedWith(compareBy(NaturalOrderComparator) { it.name.orEmpty() })

        for (seriesDirectory in seriesDirectories) {
            currentCoroutineContext().ensureActive()
            val seriesId = stableId(seriesDirectory.uri.toString())
            val directFiles = seriesDirectory.listFiles().toList()
            val seasonDirectories = directFiles
                .filter(DocumentFile::isDirectory)
                .mapNotNull { directory ->
                    LibraryFileRules.seasonNumber(directory.name.orEmpty())?.let { number -> number to directory }
                }
                .sortedBy { it.first }

            val seasonSources = if (seasonDirectories.isEmpty()) {
                listOf(1 to seriesDirectory)
            } else {
                seasonDirectories
            }
            val seasons = mutableListOf<SeasonItem>()

            for ((seasonNumber, seasonDirectory) in seasonSources) {
                currentCoroutineContext().ensureActive()
                val files = seasonDirectory.listFiles().toList()
                ignoredVideoCount += files.count { looksLikeVideo(it) && !isSupportedVideo(it) }
                val imagesByBase = files.filter(::isImage)
                    .associateBy { LibraryFileRules.baseName(it.name.orEmpty()).lowercase() }
                val subtitlesByBase = files.filter(::isSubtitle)
                    .associateBy { LibraryFileRules.baseName(it.name.orEmpty()).lowercase() }
                val videos = files.filter(::isSupportedVideo)
                    .sortedWith(compareBy(NaturalOrderComparator) { it.name.orEmpty() })
                if (videos.isEmpty()) continue

                val seasonTitle = seasonDirectory.name
                    ?.takeIf { seasonDirectories.isNotEmpty() }
                    ?.let(NameFormatter::displayName)
                    ?: "Sezonul $seasonNumber"
                val episodes = videos.mapIndexed { index, file ->
                    val baseName = LibraryFileRules.baseName(file.name.orEmpty()).lowercase()
                    val artwork = imagesByBase[baseName]
                    if (artwork == null) missingEpisodeArtworkCount += 1
                    val number = LibraryFileRules.episodeNumber(file.name.orEmpty(), index + 1)
                    EpisodeItem(
                        id = stableId("${seriesDirectory.uri}|${file.uri}|${file.length()}|${file.lastModified()}"),
                        seriesId = seriesId,
                        seasonNumber = seasonNumber,
                        seasonTitle = seasonTitle,
                        number = number,
                        sortOrder = index + 1,
                        title = NameFormatter.displayName(file.name.orEmpty()),
                        mediaUri = file.uri.toString(),
                        subtitleUri = subtitlesByBase[baseName]?.uri?.toString(),
                        artworkUri = artwork?.uri?.toString(),
                        artworkVersion = artwork?.lastModified() ?: 0L,
                    )
                }
                seasons += SeasonItem(seasonNumber, seasonTitle, episodes)
            }
            if (seasons.isEmpty()) continue

            val cover = findSeriesArtwork(directFiles)
            if (cover == null) missingSeriesArtworkCount += 1
            seriesItems += SeriesItem(
                id = seriesId,
                title = NameFormatter.displayName(seriesDirectory.name.orEmpty()),
                directoryUri = seriesDirectory.uri.toString(),
                coverUri = cover?.uri?.toString(),
                coverVersion = cover?.lastModified() ?: 0L,
                seasons = seasons,
            )
        }

        val sortedSeries = seriesItems.sortedWith(compareBy(NaturalOrderComparator) { it.title })
        return LibraryCatalog(
            rootUri = rootUri,
            series = sortedSeries,
            diagnostics = LibraryDiagnostics(
                ignoredVideoCount = ignoredVideoCount,
                missingSeriesArtworkCount = missingSeriesArtworkCount,
                missingEpisodeArtworkCount = missingEpisodeArtworkCount,
                seriesCount = sortedSeries.size,
                episodeCount = sortedSeries.sumOf { it.episodeCount },
            ),
            generatedAtEpochMs = System.currentTimeMillis(),
            source = CatalogSource.FOLDER_SCAN,
        )
    }

    private fun readGeneratedCatalog(root: DocumentFile): String? = runCatching {
        val catalogFile = root.findFile(CatalogJsonCodec.FILE_NAME)?.takeIf(DocumentFile::isFile)
            ?: return@runCatching null
        if (catalogFile.length() > MAX_GENERATED_CATALOG_BYTES) return@runCatching null
        context.contentResolver.openInputStream(catalogFile.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun resolveRoot(
        uriString: String?,
        requirePersistedPermission: Boolean = true,
    ): LibraryResult<DocumentFile> {
        if (uriString == null) return LibraryResult.NotConfigured
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return LibraryResult.PermissionLost
        if (requirePersistedPermission) {
            val hasPermission = context.contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == uri && permission.isReadPermission
            }
            if (!hasPermission) return LibraryResult.PermissionLost
        }
        val root = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
            ?: return LibraryResult.StorageUnavailable
        return if (root.canRead()) LibraryResult.Success(root) else LibraryResult.StorageUnavailable
    }

    private fun releasePermission(uriString: String, except: String? = null) {
        if (uriString == except) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun findSeriesArtwork(files: List<DocumentFile>): DocumentFile? {
        val images = files.filter(::isImage)
        return LibraryFileRules.coverPriorityNames.firstNotNullOfOrNull { priority ->
            images.firstOrNull {
                LibraryFileRules.baseName(it.name.orEmpty()).equals(priority, ignoreCase = true)
            }
        }
    }

    private fun isSupportedVideo(file: DocumentFile): Boolean =
        file.isFile && (LibraryFileRules.isSupportedVideoName(file.name.orEmpty()) || file.type in SUPPORTED_VIDEO_MIME_TYPES)

    private fun looksLikeVideo(file: DocumentFile): Boolean =
        file.isFile && (LibraryFileRules.isKnownVideoName(file.name.orEmpty()) || file.type?.startsWith("video/") == true)

    private fun isImage(file: DocumentFile): Boolean =
        file.isFile && (LibraryFileRules.isImageName(file.name.orEmpty()) || file.type?.startsWith("image/") == true)

    private fun isSubtitle(file: DocumentFile): Boolean =
        file.isFile && LibraryFileRules.isSubtitleName(file.name.orEmpty())

    private fun stableId(value: String): String = sha256(value).take(24)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it) }

    companion object {
        private val SUPPORTED_VIDEO_MIME_TYPES = setOf("video/mp4", "video/x-m4v")
        private const val MEMORY_FOLDER_SCAN_TTL_MS = 2 * 60 * 1000L
        private const val DISK_FOLDER_SCAN_TTL_MS = 15 * 60 * 1000L
        private const val MAX_GENERATED_CATALOG_BYTES = 8 * 1024 * 1024L
    }
}
