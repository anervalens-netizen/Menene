package ro.mehene.app.data

import ro.mehene.app.model.LibraryCatalog

internal data class CatalogValidationResult(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

internal object CatalogValidator {
    fun validate(catalog: LibraryCatalog): CatalogValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val seriesIds = mutableSetOf<String>()
        val episodeIds = mutableSetOf<String>()

        catalog.series.forEachIndexed { seriesIndex, series ->
            val seriesLabel = series.title.ifBlank { "serial ${seriesIndex + 1}" }
            if (series.id.isBlank()) errors += "$seriesLabel: ID serial gol"
            else if (!seriesIds.add(series.id)) errors += "$seriesLabel: ID serial duplicat ${series.id}"
            if (series.title.isBlank()) errors += "$seriesLabel: titlu serial gol"
            if (series.directoryUri.isBlank()) errors += "$seriesLabel: director URI gol"
            if (series.seasons.isEmpty()) errors += "$seriesLabel: fără sezoane"

            val seasonNumbers = mutableSetOf<Int>()
            series.seasons.forEach { season ->
                if (season.number <= 0) errors += "$seriesLabel: număr sezon invalid ${season.number}"
                if (!seasonNumbers.add(season.number)) {
                    errors += "$seriesLabel: sezon duplicat ${season.number}"
                }
                if (season.episodes.isEmpty()) warnings += "$seriesLabel / ${season.title}: fără episoade"

                val sortOrders = mutableSetOf<Int>()
                season.episodes.forEach { episode ->
                    val episodeLabel = "$seriesLabel / ${season.title} / ${episode.title}"
                    if (episode.id.isBlank()) errors += "$episodeLabel: ID episod gol"
                    else if (!episodeIds.add(episode.id)) errors += "$episodeLabel: ID episod duplicat ${episode.id}"
                    if (episode.seriesId != series.id) errors += "$episodeLabel: seriesId inconsistent"
                    if (episode.seasonNumber != season.number) errors += "$episodeLabel: seasonNumber inconsistent"
                    if (episode.mediaUri.isBlank()) errors += "$episodeLabel: media URI gol"
                    if (episode.title.isBlank()) errors += "$episodeLabel: titlu episod gol"
                    if (episode.number <= 0) errors += "$episodeLabel: număr episod invalid"
                    if (!sortOrders.add(episode.sortOrder)) {
                        warnings += "$episodeLabel: sortOrder duplicat ${episode.sortOrder}"
                    }
                }
            }
        }

        if (catalog.series.isNotEmpty() && episodeIds.isEmpty()) errors += "Catalogul nu conține episoade"
        return CatalogValidationResult(errors.distinct(), warnings.distinct())
    }

    fun requireValid(catalog: LibraryCatalog): CatalogValidationResult {
        val result = validate(catalog)
        require(result.isValid) { result.errors.joinToString(separator = "; ") }
        return result
    }
}
