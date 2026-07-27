package ro.mehene.app.util

object NaturalOrderComparator : Comparator<String> {
    private val tokenPattern = Regex("\\d+|\\D+")

    override fun compare(left: String, right: String): Int {
        val leftTokens = tokenPattern.findAll(left.lowercase()).map { it.value }.toList()
        val rightTokens = tokenPattern.findAll(right.lowercase()).map { it.value }.toList()
        val commonSize = minOf(leftTokens.size, rightTokens.size)

        for (index in 0 until commonSize) {
            val leftToken = leftTokens[index]
            val rightToken = rightTokens[index]
            val comparison = compareToken(leftToken, rightToken)
            if (comparison != 0) return comparison
        }

        return leftTokens.size.compareTo(rightTokens.size).takeIf { it != 0 }
            ?: left.compareTo(right, ignoreCase = true)
    }

    private fun compareToken(left: String, right: String): Int {
        val leftIsNumber = left.all(Char::isDigit)
        val rightIsNumber = right.all(Char::isDigit)
        if (!leftIsNumber || !rightIsNumber) return left.compareTo(right)

        val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
        val normalizedRight = right.trimStart('0').ifEmpty { "0" }
        return normalizedLeft.length.compareTo(normalizedRight.length).takeIf { it != 0 }
            ?: normalizedLeft.compareTo(normalizedRight).takeIf { it != 0 }
            ?: left.length.compareTo(right.length)
    }
}
