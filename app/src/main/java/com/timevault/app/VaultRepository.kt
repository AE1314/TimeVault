package com.timevault.app

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AccesoSimpleDto(
    val uid: String = "",
    val estado: String = ""
)

class VaultRepository {
    private val client = SupabaseClient.client

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun getVaultState(): Result<VaultSettingsDto> = runCatching {
        client.postgrest.from("vault_settings")
            .select { filter { eq("id", 1) } }
            .decodeSingle<VaultSettingsDto>()
    }

    suspend fun getLastScannedUid(): String? = runCatching {
        val list = client.postgrest.from("accesos")
            .select {
                order("id", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(1)
            }
            .decodeList<AccesoSimpleDto>()
        list.firstOrNull()?.uid
    }.getOrNull()

    suspend fun updateLockStatus(lock: Boolean): Result<Unit> = runCatching {
        client.postgrest.from("vault_settings").update(
            {
                VaultSettingsDto::isLocked setTo lock
                if (!lock) VaultSettingsDto::lockUntil setTo null
            }
        ) {
            filter { eq("id", 1) }
        }
    }

    fun observeVaultChanges(): Flow<VaultSettingsDto> {
        val channel = client.realtime.channel("vault_realtime")
        return channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "vault_settings"
        }
            .onStart { channel.subscribe() }
            .map { action ->
                json.decodeFromJsonElement<VaultSettingsDto>(action.record)
            }
    }

    fun observeLastAccess(): Flow<String> {
        val channel = client.realtime.channel("accesos_realtime")
        return channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "accesos"
        }
            .onStart { channel.subscribe() }
            .map { action ->
                action.record.jsonObject["uid"]?.jsonPrimitive?.content.orEmpty()
            }
    }
}