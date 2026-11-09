package com.andromeda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.andromeda.app.AppViewModel
import com.andromeda.app.Dialog
import com.andromeda.app.data.FileKind
import kotlinx.coroutines.delay

@Composable
fun Dialogs(vm: AppViewModel) {
    val close = { vm.dialog = null }
    when (val d = vm.dialog) {
        null -> {}
        is Dialog.NewProject -> NameDialog("New project", "", "Create", close) { vm.createProject(it); close() }
        is Dialog.NewFile -> NameDialog(
            if (d.kind == FileKind.ATLAS) "New atlas" else "New note", "", "Create", close
        ) { vm.create(d.dir, it, d.kind); close() }
        is Dialog.NewFolder -> NameDialog("New folder", "", "Create", close) { vm.create(d.dir, it, null); close() }
        is Dialog.Rename -> NameDialog("Rename", d.node.title, "Rename", close) { vm.rename(d.node, it); close() }
        is Dialog.Delete -> AlertDialog(
            onDismissRequest = close,
            containerColor = Ad.raised,
            title = { Text("Delete \"${d.node.title}\"?", color = Ad.text) },
            text = {
                Text(
                    if (d.node.isDir) "This folder and everything inside it will be deleted."
                    else "This file will be deleted.",
                    color = Ad.muted
                )
            },
            confirmButton = { TextButton(onClick = { vm.delete(d.node); close() }) { Text("Delete", color = Ad.danger) } },
            dismissButton = { TextButton(onClick = close) { Text("Cancel", color = Ad.muted) } }
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ad.raised,
        title = { Text(title, color = Ad.text) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Ad.text, unfocusedTextColor = Ad.text,
                    focusedBorderColor = Ad.accent, unfocusedBorderColor = Ad.border, cursorColor = Ad.accent
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onOk(text) }) { Text(confirm, color = Ad.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ad.muted) } }
    )
}

@Composable
fun ErrorToast(message: String, onDone: () -> Unit) {
    LaunchedEffect(message) { delay(3500); onDone() }
    Popup(alignment = Alignment.BottomCenter) {
        Text(
            message, color = Ad.text, fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp).clip(RoundedCornerShape(8.dp)).background(Ad.hover).padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}
