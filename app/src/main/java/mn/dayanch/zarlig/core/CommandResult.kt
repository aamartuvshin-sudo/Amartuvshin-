package mn.dayanch.zarlig.core

/**
 * Командын гүйцэтгэлийн үр дүн.
 * [message] — хэрэглэгчид чат дээр харуулах хариу.
 * [success] — амжилттай эсэх (өнгө ялгахад).
 */
data class CommandResult(
    val message: String,
    val success: Boolean = true
)
