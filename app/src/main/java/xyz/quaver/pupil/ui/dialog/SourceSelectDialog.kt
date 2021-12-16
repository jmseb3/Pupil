/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.ui.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.kodein.di.compose.rememberInstance
import xyz.quaver.pupil.sources.Source
import xyz.quaver.pupil.sources.SourceEntries

@Composable
fun SourceSelectDialogItem(source: Source) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(source.iconResID),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )

        Text(
            source.name,
            modifier = Modifier.weight(1f)
        )

        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
        )

        Button(onClick = { /*TODO*/ }) {
            Text("GO")
        }

    }
}

@Preview
@Composable
fun SourceSelectDialog(onDismissRequest: () -> Unit = { }) {
    val sourceEntries: SourceEntries by rememberInstance()

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            elevation = 8.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column() {
                sourceEntries.forEach { SourceSelectDialogItem(it.second) }
            }
        }
    }
}