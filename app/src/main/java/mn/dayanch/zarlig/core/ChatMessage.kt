package mn.dayanch.zarlig.core

/** Чат дээрх нэг мөр мессеж. */
data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val success: Boolean = true
)
