package com.timevault.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timevault.app.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

@Serializable
data class PersonaDto(
    val id: String,
    val nombre: String,
    val empresa_id: String
)

class TimeVaultViewModel : ViewModel() {

    private val _employees = MutableStateFlow<List<PersonaDto>>(emptyList())
    val employees: StateFlow<List<PersonaDto>> = _employees

    init {
        fetchEmployees()
    }

    fun fetchEmployees() {
        viewModelScope.launch {
            try {
                val response = SupabaseClient.client
                    .from("personas")
                    .select()
                    .decodeList<PersonaDto>()

                _employees.value = response
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}