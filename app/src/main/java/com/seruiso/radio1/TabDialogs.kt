package com.seruiso.radio1

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Діалоги вкладок: pick tab для станції, create, edit/rename/delete, delete station.
 */
@Composable
fun PickStationTabDialog(
    pickStation: Station?,
    targetTabs: List<String>,
    onPickTabForStation: (String) -> Unit,
    onCancelPick: () -> Unit,
    muted: Color,
    text: Color,
    card: Color,
) {
    if (pickStation == null) return
    AlertDialog(
        containerColor = card,
        onDismissRequest = onCancelPick,
        title = { Text(LocalContext.current.getString(R.string.select_tab), color = text) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                targetTabs.forEach { tab ->
                    Text(
                        tabLabel(LocalContext.current, tab),
                        color = text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Palette.panel, RoundedCornerShape(12.dp))
                            .clickable { onPickTabForStation(tab) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancelPick) {
                Text(LocalContext.current.getString(R.string.cancel), color = muted)
            }
        }
    )
}

@Composable
fun NewTabDialog(
    open: Boolean,
    newTabName: String,
    onNewTabName: (String) -> Unit,
    onCreateTab: () -> Unit,
    onCancelNewTab: () -> Unit,
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
) {
    if (!open) return
    AlertDialog(
        containerColor = card,
        onDismissRequest = onCancelNewTab,
        title = { Text(LocalContext.current.getString(R.string.create_new_tab), color = text) },
        text = {
            Column {
                OutlinedTextField(
                    value = newTabName,
                    onValueChange = onNewTabName,
                    singleLine = true,
                    label = { Text(LocalContext.current.getString(R.string.name_label)) },
                    supportingText = {
                        Text(LocalContext.current.getString(R.string.hint_tab_name))
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCreateTab,
                colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = Color(0xFF0A0A0C))
            ) { Text(LocalContext.current.getString(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onCancelNewTab) {
                Text(LocalContext.current.getString(R.string.cancel), color = muted)
            }
        }
    )
}

@Composable
fun EditTabDialog(
    editTab: String?,
    editName: String,
    onEditName: (String) -> Unit,
    onRenameTab: () -> Unit,
    onDeleteTab: () -> Unit,
    onCancelEdit: () -> Unit,
    deleteArmed: Boolean,
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
) {
    if (editTab == null) return
    AlertDialog(
        containerColor = card,
        onDismissRequest = onCancelEdit,
        title = { Text(LocalContext.current.getString(R.string.tab_edit, editTab), color = text) },
        text = {
            Column {
                OutlinedTextField(value = editName, onValueChange = onEditName, singleLine = true)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRenameTab,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = Color(0xFF0A0A0C))
                ) { Text(LocalContext.current.getString(R.string.rename)) }
            }
        },
        confirmButton = {
            if (deleteArmed)
                Button(
                    onClick = onDeleteTab,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White)
                ) { Text(LocalContext.current.getString(R.string.delete_confirm)) }
            else
                TextButton(onClick = onDeleteTab) {
                    Text(LocalContext.current.getString(R.string.delete), color = Color(0xFFE53935))
                }
        },
        dismissButton = {
            TextButton(onClick = onCancelEdit) {
                Text(LocalContext.current.getString(R.string.cancel), color = muted)
            }
        }
    )
}

@Composable
fun DeleteStationDialog(
    pendingDelete: Station?,
    onDeleteStation: (Station) -> Unit,
    onCancelDelete: () -> Unit,
    muted: Color,
    text: Color,
    card: Color,
) {
    if (pendingDelete == null) return
    AlertDialog(
        containerColor = card,
        onDismissRequest = onCancelDelete,
        title = { Text(LocalContext.current.getString(R.string.delete_station_q), color = text) },
        text = { Text(pendingDelete.name, color = muted) },
        confirmButton = {
            Button(
                onClick = {
                    onDeleteStation(pendingDelete)
                    onCancelDelete()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White)
            ) { Text(LocalContext.current.getString(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onCancelDelete) {
                Text(LocalContext.current.getString(R.string.cancel), color = muted)
            }
        }
    )
}
