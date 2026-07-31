package ro.menene.app.util

import android.content.Context

fun Context.meneneGridColumns(
    minimumColumnWidthDp: Int = 230,
    maximumColumns: Int = 3,
): Int {
    val widthDp = resources.configuration.screenWidthDp.takeIf { it > 0 } ?: 800
    return (widthDp / minimumColumnWidthDp).coerceIn(2, maximumColumns)
}
