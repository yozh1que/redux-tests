package dfilyustin.translate.utils

sealed class RequestState {
    object Idle : RequestState()
    object Running : RequestState()
    data class Failed(val e: Throwable) : RequestState()
}