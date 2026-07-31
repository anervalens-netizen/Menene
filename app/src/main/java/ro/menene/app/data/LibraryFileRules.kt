package ro.menene.app.data

object LibraryFileRules {
    val supportedVideoExtensions = setOf("mp4", "m4v")
    val knownVideoExtensions = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov")
    val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
    val subtitleExtensions = setOf("srt", "vtt")
    val coverPriorityNames = listOf("cover", "poster", "folder", "serial")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()
    fun baseName(name: String): String = name.substringBeforeLast('.', name)
    fun isSupportedVideoName(name: String): Boolean = extension(name) in supportedVideoExtensions
    fun isKnownVideoName(name: String): Boolean = extension(name) in knownVideoExtensions
    fun isImageName(name: String): Boolean = extension(name) in imageExtensions
    fun isSubtitleName(name: String): Boolean = extension(name) in subtitleExtensions

    fun episodeNumber(name: String, fallback: Int): Int {
        val normalized = baseName(name).lowercase()
        val patterns = listOf(
            Regex("s\\s*0*\\d{1,3}[\\s._-]*e\\s*0*(\\d{1,4})"),
            Regex("(?:episode|episod(?:ul)?)\\s*0*(\\d{1,4})"),
            Regex("(?:^|[^0-9])e\\s*0*(\\d{1,4})"),
            Regex("(?:^|[^0-9])0*(\\d{1,4})(?:[^0-9]|$)"),
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
        } ?: fallback
    }

    fun seasonNumber(name: String): Int? {
        val normalized = name.lowercase().trim()
        return Regex("(?:season|sezon(?:ul)?|s)\\s*0*(\\d{1,3})")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }
}
