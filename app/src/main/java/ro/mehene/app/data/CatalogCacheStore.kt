package ro.mehene.app.data

import android.content.Context
import ro.mehene.app.model.CatalogSource
import ro.mehene.app.model.EpisodeItem
import ro.mehene.app.model.LibraryCatalog
import ro.mehene.app.model.LibraryDiagnostics
import ro.mehene.app.model.SeasonItem
import ro.mehene.app.model.SeriesItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CatalogCacheStore(context: Context) {
    private val cacheFile = File(context.filesDir, "mehene-catalog-cache.json")

    fun load(expectedRootUri: String): LibraryCatalog? = runCatching {
        if (!cacheFile.isFile) return null
        val root = JSONObject(cacheFile.readText())
        if (root.optString("rootUri") != expectedRootUri) return null
        decode(root).copy(source = CatalogSource.INTERNAL_CACHE)
    }.getOrNull()

    fun save(catalog: LibraryCatalog) {
        runCatching {
            val temporary = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            temporary.writeText(encode(catalog).toString())
            if (!temporary.renameTo(cacheFile)) {
                cacheFile.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    fun clear() {
        cacheFile.delete()
    }

    private fun encode(catalog: LibraryCatalog): JSONObject = JSONObject().apply {
        put("rootUri", catalog.rootUri)
        put("generatedAtEpochMs", catalog.generatedAtEpochMs)
        put("source", catalog.source.name)
        put("diagnostics", JSONObject().apply {
            put("ignoredVideoCount", catalog.diagnostics.ignoredVideoCount)
            put("missingSeriesArtworkCount", catalog.diagnostics.missingSeriesArtworkCount)
            put("missingEpisodeArtworkCount", catalog.diagnostics.missingEpisodeArtworkCount)
            put("seriesCount", catalog.diagnostics.seriesCount)
            put("episodeCount", catalog.diagnostics.episodeCount)
            put("scanDurationMs", catalog.diagnostics.scanDurationMs)
            put("lastError", catalog.diagnostics.lastError)
        })
        put("series", JSONArray().apply {
            catalog.series.forEach { seriesItem ->
                put(JSONObject().apply {
                    put("id", seriesItem.id)
                    put("title", seriesItem.title)
                    put("directoryUri", seriesItem.directoryUri)
                    put("coverUri", seriesItem.coverUri)
                    put("coverVersion", seriesItem.coverVersion)
                    put("seasons", JSONArray().apply {
                        seriesItem.seasons.forEach { season ->
                            put(JSONObject().apply {
                                put("number", season.number)
                                put("title", season.title)
                                put("episodes", JSONArray().apply {
                                    season.episodes.forEach { episode ->
                                        put(JSONObject().apply {
                                            put("id", episode.id)
                                            put("seriesId", episode.seriesId)
                                            put("seasonNumber", episode.seasonNumber)
                                            put("seasonTitle", episode.seasonTitle)
                                            put("number", episode.number)
                                            put("sortOrder", episode.sortOrder)
                                            put("title", episode.title)
                                            put("mediaUri", episode.mediaUri)
                                            put("subtitleUri", episode.subtitleUri)
                                            put("artworkUri", episode.artworkUri)
                                            put("artworkVersion", episode.artworkVersion)
                                            put("durationMs", episode.durationMs)
                                        })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        })
    }

    private fun decode(root: JSONObject): LibraryCatalog {
        val diagnosticsJson = root.optJSONObject("diagnostics") ?: JSONObject()
        val series = root.optJSONArray("series").toObjectList { seriesJson ->
            val seriesId = seriesJson.getString("id")
            SeriesItem(
                id = seriesId,
                title = seriesJson.getString("title"),
                directoryUri = seriesJson.optString("directoryUri"),
                coverUri = seriesJson.optNullableString("coverUri"),
                coverVersion = seriesJson.optLong("coverVersion"),
                seasons = seriesJson.optJSONArray("seasons").toObjectList { seasonJson ->
                    val seasonNumber = seasonJson.optInt("number", 1)
                    val seasonTitle = seasonJson.optString("title", "Sezonul $seasonNumber")
                    SeasonItem(
                        number = seasonNumber,
                        title = seasonTitle,
                        episodes = seasonJson.optJSONArray("episodes").toObjectList { episodeJson ->
                            EpisodeItem(
                                id = episodeJson.getString("id"),
                                seriesId = episodeJson.optString("seriesId", seriesId),
                                seasonNumber = episodeJson.optInt("seasonNumber", seasonNumber),
                                seasonTitle = episodeJson.optString("seasonTitle", seasonTitle),
                                number = episodeJson.optInt("number", 1),
                                sortOrder = episodeJson.optInt("sortOrder", episodeJson.optInt("number", 1)),
                                title = episodeJson.getString("title"),
                                mediaUri = episodeJson.getString("mediaUri"),
                                subtitleUri = episodeJson.optNullableString("subtitleUri"),
                                artworkUri = episodeJson.optNullableString("artworkUri"),
                                artworkVersion = episodeJson.optLong("artworkVersion"),
                                durationMs = episodeJson.optLong("durationMs"),
                            )
                        },
                    )
                },
            )
        }
        return LibraryCatalog(
            rootUri = root.getString("rootUri"),
            series = series,
            diagnostics = LibraryDiagnostics(
                ignoredVideoCount = diagnosticsJson.optInt("ignoredVideoCount"),
                missingSeriesArtworkCount = diagnosticsJson.optInt("missingSeriesArtworkCount"),
                missingEpisodeArtworkCount = diagnosticsJson.optInt("missingEpisodeArtworkCount"),
                seriesCount = diagnosticsJson.optInt("seriesCount", series.size),
                episodeCount = diagnosticsJson.optInt("episodeCount", series.sumOf { it.episodeCount }),
                scanDurationMs = diagnosticsJson.optLong("scanDurationMs"),
                lastError = diagnosticsJson.optNullableString("lastError"),
            ),
            generatedAtEpochMs = root.optLong("generatedAtEpochMs"),
            source = root.optString("source")
                .let { runCatching { CatalogSource.valueOf(it) }.getOrDefault(CatalogSource.INTERNAL_CACHE) },
        )
    }
}

internal inline fun <T> JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) add(mapper(getJSONObject(index)))
    }
}

internal fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)
