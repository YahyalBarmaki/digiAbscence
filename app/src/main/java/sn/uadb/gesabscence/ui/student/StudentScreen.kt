package sn.uadb.gesabscence.ui.student

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import sn.uadb.gesabscence.R
import sn.uadb.gesabscence.ble.BlePermissions
import sn.uadb.gesabscence.ui.PermissionGate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(
    onChangeRole: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.student_title)) },
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
            permissions = BlePermissions.scan,
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
                        val detected = state.detectedSessionId
                        Text(
                            text = when {
                                state.presenceConfirmed -> stringResource(R.string.student_confirmed)
                                detected != null -> detected
                                state.isScanning -> stringResource(R.string.student_scanning)
                                else -> stringResource(R.string.student_idle)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        state.lastRssi?.let { rssi ->
                            Text(
                                text = "RSSI $rssi dBm  (seuil ${state.rssiThreshold})",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                ThresholdControl(
                    threshold = state.rssiThreshold,
                    onThresholdChange = viewModel::setRssiThreshold,
                )

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
                    onClick = { if (state.isScanning) viewModel.stopScan() else viewModel.startScan() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (state.isScanning) R.string.student_scan_stop
                            else R.string.student_scan_start
                        )
                    )
                }

                OutlinedButton(
                    onClick = viewModel::confirmPresence,
                    enabled = state.detectedSessionId != null &&
                        !state.presenceConfirmed &&
                        !state.confirmInFlight,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.student_confirm))
                }
            }
        }
    }
}

@Composable
private fun ThresholdControl(
    threshold: Int,
    onThresholdChange: (Int) -> Unit,
) {
    // Local drag value so the slider stays smooth; commit on release.
    var dragValue by remember(threshold) { mutableFloatStateOf(threshold.toFloat()) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.student_threshold_label, dragValue.roundToInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = dragValue,
                onValueChange = { dragValue = it },
                onValueChangeFinished = { onThresholdChange(dragValue.roundToInt()) },
                valueRange = StudentViewModel.MIN_THRESHOLD.toFloat()..StudentViewModel.MAX_THRESHOLD.toFloat(),
            )
            Text(
                text = stringResource(R.string.student_threshold_help),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
