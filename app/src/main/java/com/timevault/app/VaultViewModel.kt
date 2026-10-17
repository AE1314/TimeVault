package com.timevault.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VaultViewModel(
    private val repository: VaultRepository = VaultRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState(isLoading = true))
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
        observeChanges()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            val lastUid = repository.getLastScannedUid()
            repository.getVaultState()
                .onSuccess { dto ->
                    _uiState.update {
                        it.copy(
                            isLocked = dto.isLocked,
                            lastScannedUid = lastUid ?: dto.lastScannedUid,
                            deviceStatus = "ONLINE",
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            deviceStatus = "OFFLINE",
                            errorMessage = e.message
                        )
                    }
                }
        }
    }

    private fun observeChanges() {
        viewModelScope.launch {
            repository.observeVaultChanges()
                .catch { }
                .collect { dto ->
                    _uiState.update {
                        it.copy(
                            isLocked = dto.isLocked,
                            deviceStatus = "ONLINE",
                            isLoading = false
                        )
                    }
                }
        }

        viewModelScope.launch {
            repository.observeLastAccess()
                .catch { }
                .collect { uid ->
                    if (uid.isNotBlank()) {
                        _uiState.update { it.copy(lastScannedUid = uid) }
                    }
                }
        }
    }

    fun toggleLock() {
        val currentLock = _uiState.value.isLocked
        val newLock = !currentLock

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.updateLockStatus(newLock)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLocked = newLock,
                            isLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message
                        )
                    }
                }
        }
    }
}