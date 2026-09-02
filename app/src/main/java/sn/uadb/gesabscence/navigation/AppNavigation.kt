package sn.uadb.gesabscence.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import sn.uadb.gesabscence.data.AppRole
import sn.uadb.gesabscence.ui.AppViewModel
import sn.uadb.gesabscence.ui.RoleState
import sn.uadb.gesabscence.ui.role.RoleSelectionScreen
import sn.uadb.gesabscence.ui.student.StudentScreen
import sn.uadb.gesabscence.ui.teacher.TeacherScreen

/**
 * Top-level routing: the persisted [AppRole] decides which mode is shown.
 * Choosing / clearing the role is the only navigation in Module 1;
 * per-module screens (dashboard, history…) are added to their modes later.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    appViewModel: AppViewModel = viewModel(),
) {
    val roleState by appViewModel.roleState.collectAsStateWithLifecycle()

    when (val s = roleState) {
        RoleState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        RoleState.Unchosen -> RoleSelectionScreen(
            onRoleChosen = appViewModel::chooseRole,
            modifier = modifier,
        )

        is RoleState.Chosen -> when (s.role) {
            AppRole.TEACHER -> TeacherScreen(
                onChangeRole = appViewModel::clearRole,
                modifier = modifier,
            )
            AppRole.STUDENT -> StudentScreen(
                onChangeRole = appViewModel::clearRole,
                modifier = modifier,
            )
        }
    }
}
