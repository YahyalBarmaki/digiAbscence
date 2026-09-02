package sn.uadb.gesabscence.ui.teacher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import sn.uadb.gesabscence.R
import sn.uadb.gesabscence.ble.BlePermissions
import sn.uadb.gesabscence.ui.PermissionGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherScreen(
    onChangeRole: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TeacherViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.teacher_title)) },
                actions = {
                    IconButton(onClick = onChangeRole) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(R.string.change_role),
                        )
                    }
                },
            )
        },
    ) { inner ->
        PermissionGate(
            permissions = BlePermissions.advertise,
            modifier = Modifier.padding(inner),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.teacher_session_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = state.sessionId?.takeIf { state.isAdvertising }
                                ?: stringResource(R.string.teacher_idle),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }

                Button(
                    onClick = {
                        if (state.isAdvertising) viewModel.stopSession() else viewModel.startSession()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (state.isAdvertising) R.string.teacher_stop else R.string.teacher_start
                        )
                    )
                }
            }
        }
    }
}
