package ro.mehene.app.data

object LibraryFileRules {
    private val supportedVideoExtensions = setOf("mp4", "m4v")
    private val knownVideoExtensions = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
    val coverPriorityNames = listOf("cover", "poster", "folder", "serial")

    fun isSupportedVideoName(name: String): Boolean = extension(name) in supportedVideoExtensions
    fun isKnownVideoName(name: String): Boolean = extension(name) in knownVideoExtensions
    fun isImageName(name: String): Boolean = extension(name) in imageExtensions
    fun baseName(name: String): String = name.substringBeforeLast('.', name)
    private fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()
}
