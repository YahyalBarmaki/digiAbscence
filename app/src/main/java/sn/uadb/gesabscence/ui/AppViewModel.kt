package sn.uadb.gesabscence.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sn.uadb.gesabscence.data.AppRole
import sn.uadb.gesabscence.data.RolePreferences

sealed interface RoleState {
    data object Loading : RoleState
    data object Unchosen : RoleState
    data class Chosen(val role: AppRole) : RoleState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = RolePreferences(app)

    val roleState: StateFlow<RoleState> = prefs.role
        .map { role -> if (role == null) RoleState.Unchosen else RoleState.Chosen(role) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoleState.Loading)

    fun chooseRole(role: AppRole) = viewModelScope.launch { prefs.setRole(role) }

    fun clearRole() = viewModelScope.launch { prefs.clearRole() }
}
