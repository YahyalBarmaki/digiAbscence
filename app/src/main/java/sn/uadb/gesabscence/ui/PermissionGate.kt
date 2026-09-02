package sn.uadb.gesabscence.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import sn.uadb.gesabscence.R

/**
 * Wraps [content] behind the given runtime [permissions]. Shows a rationale
 * + request button until everything is granted. Shared by Teacher and
 * Student modes.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGate(
    permissions: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberMultiplePermissionsState(permissions)

    if (state.allPermissionsGranted) {
        content()
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.perm_rationale_title),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.perm_rationale_body),
            textAlign = TextAlign.Center,
        )
        Button(onClick = { state.launchMultiplePermissionRequest() }) {
            Text(stringResource(R.string.perm_grant))
        }
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
        }) {
            Text(stringResource(R.string.perm_open_settings))
        }
    }
}
