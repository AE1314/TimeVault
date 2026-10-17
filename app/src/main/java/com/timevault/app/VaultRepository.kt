package com.timevault.app

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.decodeFromJsonElement

class VaultRepository {
    private val client = SupabaseClient.client
    private val table = "vault_settings"

    /**
     * Obtiene el estado actual de la cerradura.
     */
    suspend fun getVaultState(): VaultState? {
        return try {
            client.postgrest[table]
                .select()
                .decodeSingleOrNull<VaultState>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Escucha cambios en tiempo real desde Supabase.
     */
    fun observeVaultState(): Flow<VaultState?> {
        val channel = client.realtime.channel("vault_changes")
        return channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = this@VaultRepository.table
        }.map { action ->
            client.postgrest.decodeJson<VaultState>(action.record)
        }
    }

    /**
     * Actualiza el estado de bloqueo de la cerradura.
     */
    suspend fun updateLockStatus(isLocked: Boolean, lockUntil: String? = null) {
        try {
            client.postgrest[table].update(
                {
                    VaultState::isLocked setTo isLocked
                    VaultState::lockUntil setTo lockUntil
                    VaultState::updatedAt setTo "now()" // Supabase handle via RPC or DB default is better
                }
            ) {
                filter {
                    // Asumimos un ID único para la cerradura o manejamos por configuración
                    VaultState::id eq 1 
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Autoriza un UID específico para apertura temporal.
     */
    suspend fun authorizeUid(uid: String?) {
        try {
            client.postgrest[table].update(
                {
                    VaultState::authorizedUid setTo uid
                }
            ) {
                filter { VaultState::id eq 1 }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
