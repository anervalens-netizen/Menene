package ro.menene.app.util

object NameFormatter {
    fun displayName(rawName: String): String {
        val withoutExtension = rawName.substringBeforeLast('.', rawName)
        return withoutExtension
            .replace('_', ' ')
            .replace(Regex("\\s*[-–—]\\s*"), " – ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Fără titlu" }
    }
}
