package com.timevault.app.data

import com.timevault.app.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class SupabaseRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    val configured: Boolean
        get() = BuildConfig.SUPABASE_KEY.isNotBlank()

    fun fetchAccesos(): List<Acceso> {
        if (!configured) {
            error("Falta SUPABASE_KEY en BuildConfig")
        }

        val uidToNameMap = fetchUidToPersonNameMap()

        val url = BuildConfig.SUPABASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("rest")
            .addPathSegment("v1")
            .addPathSegment("accesos")
            .addQueryParameter("select", "*")
            .addQueryParameter("order", "id.desc")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("apikey", BuildConfig.SUPABASE_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Supabase ${response.code}: $body")
            }
            return parseAccesos(body, uidToNameMap)
        }
    }

    private fun fetchUidToPersonNameMap(): Map<String, String> {
        val url = BuildConfig.SUPABASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("rest")
            .addPathSegment("v1")
            .addPathSegment("personas")
            .addQueryParameter("select", "id,nombre,tarjetas(uid)")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("apikey", BuildConfig.SUPABASE_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            .header("Accept", "application/json")
            .build()

        val map = mutableMapOf<String, String>()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyMap()
                val body = response.body?.string().orEmpty()
                val array = JSONArray(body)

                for (i in 0 until array.length()) {
                    val personaObj = array.getJSONObject(i)
                    val nombre = personaObj.optString("nombre")
                    val tarjetasArray = personaObj.optJSONArray("tarjetas") ?: JSONArray()

                    for (j in 0 until tarjetasArray.length()) {
                        val tarjetaObj = tarjetasArray.getJSONObject(j)
                        val uid = tarjetaObj.optString("uid")
                        if (uid.isNotBlank() && nombre.isNotBlank()) {
                            map[uid.uppercase()] = nombre
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Mantiene mapa vacío si ocurre un fallo de red
        }

        return map
    }

    private fun parseAccesos(json: String, nameMap: Map<String, String>): List<Acceso> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawUid = obj.optString("uid")
                val accion = obj.optString("accion")

                val isRemote = rawUid.equals("APP_BUTTON", true) || accion.contains("REMOTE", true)
                val resolvedName = when {
                    isRemote -> "Apertura desde App"
                    nameMap.containsKey(rawUid.uppercase()) -> nameMap[rawUid.uppercase()].orEmpty()
                    else -> "Tarjeta sin asignar"
                }

                add(
                    Acceso(
                        id = obj.optLong("id"),
                        puertaCodigo = obj.optString("puerta_codigo"),
                        uid = rawUid,
                        nombrePersona = resolvedName,
                        estado = obj.optString("estado"),
                        direccion = obj.optString("direccion"),
                        accion = accion,
                        creadoEn = obj.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }
}