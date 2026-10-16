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
        get() = BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun fetchAccesos(): List<Acceso> {
        if (!configured) {
            error("Falta SUPABASE_ANON_KEY en local.properties")
        }
        val url = BuildConfig.SUPABASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("rest")
            .addPathSegment("v1")
            .addPathSegment("accesos")
            .addQueryParameter("select", "*")
            .addQueryParameter("order", "id.desc")
            .addQueryParameter("limit", "200")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Supabase ${response.code}: $body")
            }
            return parseAccesos(body)
        }
    }

    private fun parseAccesos(json: String): List<Acceso> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    Acceso(
                        id = obj.optLong("id"),
                        puertaCodigo = obj.optString("puerta_codigo"),
                        uid = obj.optString("uid"),
                        estado = obj.optString("estado"),
                        direccion = obj.optString("direccion"),
                        accion = obj.optString("accion"),
                        creadoEn = obj.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }
}
