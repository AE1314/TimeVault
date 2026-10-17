package com.timevault.app.data

data class Acceso(
    val id: Long,
    val puertaCodigo: String,
    val uid: String,
    val nombrePersona: String = "",
    val estado: String,
    val direccion: String,
    val accion: String,
    val creadoEn: String?
) {
    val autorizado: Boolean get() = estado.equals("AUTHORIZED", ignoreCase = true)
    val rechazo: Boolean
        get() = estado.equals("DENIED", ignoreCase = true) ||
            estado.equals("REJECTED", ignoreCase = true) ||
            estado.equals("UNAUTHORIZED", ignoreCase = true)

    val entro: Boolean get() = direccion.equals("IN", ignoreCase = true)
    val salio: Boolean get() = direccion.equals("OUT", ignoreCase = true)

    val titulo: String
        get() = when {
            rechazo -> "Tarjeta desconocida"
            else -> uid.ifBlank { "Sin UID" }
        }

    val iniciales: String
        get() {
            val parts = uid.split(":", "-", " ").filter { it.isNotBlank() }
            return when {
                rechazo -> "?"
                parts.size >= 2 -> parts.take(2).joinToString("") { it.take(1) }.uppercase()
                uid.length >= 2 -> uid.take(2).uppercase()
                else -> "TV"
            }
        }
}
