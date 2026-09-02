package sn.uadb.gesabscence.ui.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sn.uadb.gesabscence.R
import sn.uadb.gesabscence.data.AppRole

@Composable
fun RoleSelectionScreen(
    onRoleChosen: (AppRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.role_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.role_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )

        RoleCard(
            title = stringResource(R.string.role_teacher),
            description = stringResource(R.string.role_teacher_desc),
            icon = Icons.Filled.Cast,
            onClick = { onRoleChosen(AppRole.TEACHER) },
        )
        RoleCard(
            title = stringResource(R.string.role_student),
            description = stringResource(R.string.role_student_desc),
            icon = Icons.Filled.HowToReg,
            onClick = { onRoleChosen(AppRole.STUDENT) },
        )
    }
}

@Composable
private fun RoleCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
