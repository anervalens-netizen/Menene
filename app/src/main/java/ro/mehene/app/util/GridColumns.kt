package ro.mehene.app.util

import android.content.Context

fun Context.meheneGridColumns(
    minimumColumnWidthDp: Int = 220,
    maximumColumns: Int = 5,
): Int {
    val widthDp = resources.configuration.screenWidthDp.takeIf { it > 0 } ?: 800
    return (widthDp / minimumColumnWidthDp).coerceIn(2, maximumColumns)
}
