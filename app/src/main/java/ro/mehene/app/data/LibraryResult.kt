package ro.mehene.app.data

sealed interface LibraryResult<out T> {
    data class Success<T>(
        val value: T,
        val ignoredVideoCount: Int = 0,
    ) : LibraryResult<T>

    data object NotConfigured : LibraryResult<Nothing>
    data object PermissionLost : LibraryResult<Nothing>
    data object StorageUnavailable : LibraryResult<Nothing>
    data class Failure(val cause: Throwable) : LibraryResult<Nothing>
}
