package com.timevault.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VaultSettingsDto(
    @SerialName("id") val id: Long = 1,
    @SerialName("is_locked") val isLocked: Boolean = true,
    @SerialName("last_scanned_uid") val lastScannedUid: String? = null,
    @SerialName("device_status") val deviceStatus: String = "OFFLINE",
    @SerialName("lock_until") val lockUntil: String? = null
)

data class VaultUiState(
    val isLocked: Boolean = true,
    val lastScannedUid: String? = null,
    val deviceStatus: String = "OFFLINE",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)