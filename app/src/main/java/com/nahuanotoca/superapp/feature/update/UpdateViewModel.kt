package com.nahuanotoca.superapp.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.update.UpdateCheck
import com.nahuanotoca.superapp.core.update.UpdateRelease
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int? = null,
    val release: UpdateRelease? = null,
    val upToDate: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class UpdateViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(
        UpdateUiState(currentVersion = container.updates.currentVersionName()),
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun check() {
        if (_state.value.checking || _state.value.downloading) return
        viewModelScope.launch {
            _state.update { it.copy(checking = true, error = null, message = null, upToDate = false) }
            when (val result = container.updates.check()) {
                is UpdateCheck.Available -> _state.update {
                    it.copy(checking = false, release = result.release)
                }

                UpdateCheck.UpToDate -> _state.update {
                    it.copy(checking = false, release = null, upToDate = true)
                }

                is UpdateCheck.Failed -> _state.update {
                    it.copy(checking = false, error = result.message)
                }
            }
        }
    }

    fun downloadAndInstall() {
        val release = _state.value.release ?: return
        if (_state.value.downloading) return

        if (!container.apkInstaller.canInstallPackages()) {
            _state.update {
                it.copy(
                    message = "Activa «Instalar apps desconocidas» para Nahua y vuelve a intentar.",
                )
            }
            container.apkInstaller.openInstallPermissionSettings()
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(downloading = true, progress = null, error = null, message = null)
            }

            runCatching {
                container.updates.download(release) { percent ->
                    _state.update { state -> state.copy(progress = percent) }
                }
            }.onSuccess { apk ->
                val started = container.apkInstaller.install(apk)
                _state.update {
                    it.copy(
                        downloading = false,
                        message = if (started) {
                            "Sigue los pasos del instalador de Android para terminar."
                        } else {
                            "No pudimos abrir el instalador del sistema."
                        },
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloading = false,
                        error = error.message ?: "No pudimos descargar la actualización.",
                    )
                }
            }
        }
    }

    fun openInstallSettings() = container.apkInstaller.openInstallPermissionSettings()

    fun dismissError() = _state.update { it.copy(error = null) }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
