package ro.mehene.app.data

import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import ro.mehene.app.model.CatalogSource
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.LibraryCatalog
import ro.mehene.app.model.LibraryDiagnostics
import ro.mehene.app.model.SeasonItem
import ro.mehene.app.model.SeriesItem
import org.json.JSONObject

object CatalogJsonCodec {
    const val FILE_NAME = "catalog.json"
    const val SCHEMA_VERSION = 1

    fun generatedAtEpochMs(jsonText: String): Long? = runCatching {
        JSONObject(jsonText).optLong("generatedAtEpochMs").takeIf { it > 0L }
    }.getOrNull()

    fun decode(
        rootDocument: DocumentFile,
        rootUri: String,
        jsonText: String,
        scanDurationMs: Long,
    ): LibraryCatalog {
        val root = JSONObject(jsonText)
        require(root.optInt("schemaVersion", 0) == SCHEMA_VERSION) {
            "Unsupported catalog schema"
        }
        val generatedAtEpochMs = root.optLong("generatedAtEpochMs", System.currentTimeMillis())
        val pathResolver = CatalogPathResolver(rootDocument)
        var missingSeriesArtwork = 0
        var missingEpisodeArtwork = 0
        var ignoredEpisodes = 0

        val series = root.optJSONArray("series").toObjectList { seriesJson ->
            val id = seriesJson.getString("id")
            val seriesPath = seriesJson.optString("path")
            val directoryUri = pathResolver.resolve(seriesPath)
                ?: error("Directorul serialului lipsește: $seriesPath")
            val coverUri = pathResolver.resolve(seriesJson.optNullableString("cover"))
            if (coverUri == null) missingSeriesArtwork += 1

            val seasons = seriesJson.optJSONArray("seasons").toObjectList { seasonJson ->
                val seasonNumber = seasonJson.optInt("number", 1)
                val seasonTitle = seasonJson.optString("title", "Sezonul $seasonNumber")
                val episodes = seasonJson.optJSONArray("episodes").toObjectListNotNull { episodeJson ->
                    val mediaUri = pathResolver.resolve(episodeJson.optString("media"))
                    if (mediaUri == null) {
                        ignoredEpisodes += 1
                        return@toObjectListNotNull null
                    }
                    val artworkUri = pathResolver.resolve(episodeJson.optNullableString("artwork"))
                    if (artworkUri == null) missingEpisodeArtwork += 1
                    val subtitleUri = pathResolver.resolve(episodeJson.optNullableString("subtitle"))
                    EpisodeItem(
                        id = episodeJson.getString("id"),
                        seriesId = id,
                        seasonNumber = seasonNumber,
                        seasonTitle = seasonTitle,
                        number = episodeJson.optInt("number", 1),
                        sortOrder = episodeJson.optInt("sortOrder", episodeJson.optInt("number", 1)),
                        title = episodeJson.optString("title", "Episodul ${episodeJson.optInt("number", 1)}"),
                        mediaUri = mediaUri.toString(),
                        subtitleUri = subtitleUri?.toString(),
                        artworkUri = artworkUri?.toString(),
                        artworkVersion = generatedAtEpochMs.takeIf { artworkUri != null } ?: 0L,
                        durationMs = episodeJson.optLong("durationMs", 0L),
                    )
                }
                SeasonItem(
                    number = seasonNumber,
                    title = seasonTitle,
                    episodes = episodes.sortedWith(compareBy(EpisodeItem::sortOrder, EpisodeItem::number)),
                )
            }.filter { it.episodes.isNotEmpty() }.sortedBy { it.number }

            SeriesItem(
                id = id,
                title = seriesJson.optString("title", catalogPathName(seriesPath)),
                directoryUri = directoryUri.toString(),
                coverUri = coverUri?.toString(),
                coverVersion = generatedAtEpochMs.takeIf { coverUri != null } ?: 0L,
                seasons = seasons,
            )
        }.filter { it.episodeCount > 0 }

        return LibraryCatalog(
            rootUri = rootUri,
            series = series,
            diagnostics = LibraryDiagnostics(
                ignoredVideoCount = ignoredEpisodes,
                missingSeriesArtworkCount = missingSeriesArtwork,
                missingEpisodeArtworkCount = missingEpisodeArtwork,
                seriesCount = series.size,
                episodeCount = series.sumOf { it.episodeCount },
                scanDurationMs = scanDurationMs,
            ),
            generatedAtEpochMs = generatedAtEpochMs,
            source = CatalogSource.GENERATED_CATALOG,
        )
    }

    private class CatalogPathResolver(private val root: DocumentFile) {
        private val rootUri = root.uri
        private val externalStorageRootId = rootUri
            .takeIf { it.authority == EXTERNAL_STORAGE_AUTHORITY }
            ?.let { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() }
        private val childrenByDirectory = mutableMapOf<String, Map<String, DocumentFile>>()

        fun resolve(relativePath: String?): Uri? {
            val segments = normalizedCatalogSegments(relativePath) ?: return null
            if (segments.isEmpty()) return rootUri

            externalStorageRootId?.let { rootId ->
                val documentId = "$rootId/${segments.joinToString("/")}"
                return DocumentsContract.buildDocumentUriUsingTree(rootUri, documentId)
            }

            var current = root
            var currentPath = ""
            segments.forEach { segment ->
                val children = childrenByDirectory.getOrPut(currentPath) {
                    current.listFiles().associateBy { it.name.orEmpty() }
                }
                current = children[segment] ?: return null
                currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            }
            return current.uri
        }

        companion object {
            private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        }
    }
}

internal fun normalizedCatalogSegments(relativePath: String?): List<String>? {
    if (relativePath.isNullOrBlank()) return null
    if (relativePath == ".") return emptyList()
    val segments = relativePath.replace('\\', '/').split('/').filter(String::isNotBlank)
    if (segments.isEmpty() || segments.any { it == "." || it == ".." || it.indexOf('\u0000') >= 0 }) return null
    return segments
}

private fun catalogPathName(relativePath: String): String =
    normalizedCatalogSegments(relativePath)?.lastOrNull().orEmpty()

internal inline fun <T : Any> org.json.JSONArray?.toObjectListNotNull(
    mapper: (JSONObject) -> T?,
): List<T> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) mapper(getJSONObject(index))?.let(::add)
    }
}
