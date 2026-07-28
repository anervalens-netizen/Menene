package ro.mehene.app.data

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
        var missingSeriesArtwork = 0
        var missingEpisodeArtwork = 0
        var ignoredEpisodes = 0

        val series = root.optJSONArray("series").toObjectList { seriesJson ->
            val id = seriesJson.getString("id")
            val seriesPath = seriesJson.optString("path")
            val directory = resolveRelative(rootDocument, seriesPath) ?: rootDocument
            val cover = resolveRelative(rootDocument, seriesJson.optNullableString("cover"))
            if (cover == null) missingSeriesArtwork += 1

            val seasons = seriesJson.optJSONArray("seasons").toObjectList { seasonJson ->
                val seasonNumber = seasonJson.optInt("number", 1)
                val seasonTitle = seasonJson.optString("title", "Sezonul $seasonNumber")
                val episodes = seasonJson.optJSONArray("episodes").toObjectListNotNull { episodeJson ->
                    val media = resolveRelative(rootDocument, episodeJson.optString("media"))
                    if (media == null || !media.isFile) {
                        ignoredEpisodes += 1
                        return@toObjectListNotNull null
                    }
                    val artwork = resolveRelative(rootDocument, episodeJson.optNullableString("artwork"))
                    if (artwork == null) missingEpisodeArtwork += 1
                    val subtitle = resolveRelative(rootDocument, episodeJson.optNullableString("subtitle"))
                    EpisodeItem(
                        id = episodeJson.getString("id"),
                        seriesId = id,
                        seasonNumber = seasonNumber,
                        seasonTitle = seasonTitle,
                        number = episodeJson.optInt("number", 1),
                        sortOrder = episodeJson.optInt("sortOrder", episodeJson.optInt("number", 1)),
                        title = episodeJson.optString("title", "Episodul ${episodeJson.optInt("number", 1)}"),
                        mediaUri = media.uri.toString(),
                        subtitleUri = subtitle?.uri?.toString(),
                        artworkUri = artwork?.uri?.toString(),
                        artworkVersion = artwork?.lastModified() ?: 0L,
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
                title = seriesJson.optString("title", directory.name.orEmpty()),
                directoryUri = directory.uri.toString(),
                coverUri = cover?.uri?.toString(),
                coverVersion = cover?.lastModified() ?: 0L,
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
            generatedAtEpochMs = root.optLong("generatedAtEpochMs", System.currentTimeMillis()),
            source = CatalogSource.GENERATED_CATALOG,
        )
    }

    private fun resolveRelative(root: DocumentFile, relativePath: String?): DocumentFile? {
        if (relativePath.isNullOrBlank() || relativePath == ".") return root
        var current: DocumentFile = root
        relativePath.replace('\\', '/').split('/').filter(String::isNotBlank).forEach { segment ->
            current = current.findFile(segment) ?: return null
        }
        return current
    }
}

internal inline fun <T : Any> org.json.JSONArray?.toObjectListNotNull(
    mapper: (JSONObject) -> T?,
): List<T> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) mapper(getJSONObject(index))?.let(::add)
    }
}
