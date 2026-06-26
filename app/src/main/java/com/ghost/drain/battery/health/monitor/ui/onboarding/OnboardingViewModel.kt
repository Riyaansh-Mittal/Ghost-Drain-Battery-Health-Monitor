package com.ghost.drain.battery.health.monitor.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.drain.battery.health.monitor.data.UserIdentity
import com.ghost.drain.battery.health.monitor.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val selectedIdentity: UserIdentity? = null,
    val isCompleting: Boolean = false
)

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPreferences(app)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun selectIdentity(identity: UserIdentity) {
        _uiState.value = _uiState.value.copy(selectedIdentity = identity)
    }

    /** Saves selection and signals completion. Call this on "Get started" tap. */
    fun confirmSelection(identity: UserIdentity, onDone: () -> Unit) {
        _uiState.value = _uiState.value.copy(
            selectedIdentity = identity,
            isCompleting = true
        )
        viewModelScope.launch {
            prefs.completeOnboarding(identity.key)
            onDone()
        }
    }
}