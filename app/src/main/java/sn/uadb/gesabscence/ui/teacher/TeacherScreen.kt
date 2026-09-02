package sn.uadb.gesabscence.ui.teacher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.teacher_session_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = when {
                                state.isAdvertising && state.sessionId != null -> state.sessionId!!
                                state.isStarting -> stringResource(R.string.teacher_starting)
                                else -> stringResource(R.string.teacher_idle)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        if (state.isAdvertising) {
                            Text(
                                text = stringResource(R.string.teacher_advertising_hint),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                state.errorMessage?.let { message ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(onClick = viewModel::dismissError) {
                                Text(stringResource(R.string.dismiss))
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (state.isAdvertising || state.isStarting) viewModel.stopSession()
                        else viewModel.startSession()
                    },
                    enabled = !state.isStarting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            stringResource(
                                if (state.isAdvertising) R.string.teacher_stop
                                else R.string.teacher_start
                            )
                        )
                    }
                }
            }
        }
    }
}
