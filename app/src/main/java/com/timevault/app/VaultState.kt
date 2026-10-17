package com.timevault.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class VaultState(
    @SerialName("id")
    val id: Int? = null,
    @SerialName("is_locked")
    val isLocked: Boolean,
    @SerialName("lock_until")
    val lockUntil: String? = null, // Using String for ISO 8601 Timestamp
    @SerialName("authorized_uid")
    val authorizedUid: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)
