package com.andromeda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andromeda.app.AppViewModel
import com.andromeda.app.Dialog
import com.andromeda.app.TreeRow
import com.andromeda.app.data.FileKind

@Composable
fun Sidebar(vm: AppViewModel, onChangeFolder: () -> Unit, modifier: Modifier = Modifier) {
    val project = vm.project
    Column(modifier.fillMaxHeight().background(Ad.sidebar)) {
        ProjectHeader(vm, onChangeFolder)
        if (project != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconBtn(Icons.Filled.Description, "New note", { vm.dialog = Dialog.NewFile(project, FileKind.MARKDOWN) })
                IconBtn(Icons.Filled.Dashboard, "New atlas", { vm.dialog = Dialog.NewFile(project, FileKind.ATLAS) })
                IconBtn(Icons.Filled.CreateNewFolder, "New folder", { vm.dialog = Dialog.NewFolder(project) })
            }
            HorizontalDivider(color = Ad.border)
            val rows = vm.rows()
            if (rows.isEmpty()) {
                Text("Empty project. Create a note or an atlas above.", color = Ad.faint, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(rows, key = { it.node.uri.toString() }) { TreeItem(vm, it) }
            }
        } else {
            Text("Create a project to get started.", color = Ad.faint, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            Box(Modifier.weight(1f))
        }
        HorizontalDivider(color = Ad.border)
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(Icons.Filled.Settings, "Change folder", onChangeFolder)
            Text("Change workspace folder", color = Ad.faint, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun ProjectHeader(vm: AppViewModel, onChangeFolder: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clickable { menu = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.FolderOpen, null, tint = Ad.accent, modifier = Modifier.size(18.dp))
            Text(
                vm.project?.name ?: "No project", color = Ad.text, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Icon(Icons.Filled.ArrowDropDown, null, tint = Ad.muted)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Ad.raised) {
            vm.projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, color = if (p == vm.project) Ad.accent else Ad.text) },
                    onClick = { menu = false; vm.selectProject(p) }
                )
            }
            if (vm.projects.isNotEmpty()) HorizontalDivider(color = Ad.border)
            DropdownMenuItem(
                text = { Text("New project", color = Ad.text) },
                leadingIcon = { Icon(Icons.Filled.Add, null, tint = Ad.muted) },
                onClick = { menu = false; vm.dialog = Dialog.NewProject }
            )
            vm.project?.let { p ->
                DropdownMenuItem(text = { Text("Rename project", color = Ad.text) }, onClick = { menu = false; vm.dialog = Dialog.Rename(p) })
                DropdownMenuItem(text = { Text("Delete project", color = Ad.danger) }, onClick = { menu = false; vm.dialog = Dialog.Delete(p) })
            }
        }
    }
    HorizontalDivider(color = Ad.border)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeItem(vm: AppViewModel, row: TreeRow) {
    val n = row.node
    var menu by remember { mutableStateOf(false) }
    val selected = vm.active == n
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(if (selected) Ad.accentDim else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(
                    onClick = { if (n.isDir) vm.toggle(n) else vm.open(n) },
                    onLongClick = { menu = true }
                )
                .padding(start = (10 + row.depth * 16).dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when {
                n.isDir && vm.isExpanded(n) -> Icons.Filled.FolderOpen
                n.isDir -> Icons.Filled.Folder
                n.kind == FileKind.ATLAS -> Icons.Filled.Dashboard
                else -> Icons.Filled.Description
            }
            Icon(icon, null, tint = if (selected) Ad.accent else Ad.muted, modifier = Modifier.size(16.dp))
            Text(
                n.title, color = if (selected) Ad.text else Ad.text.copy(alpha = 0.85f), fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp)
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Ad.raised) {
            if (n.isDir) {
                DropdownMenuItem(text = { Text("New note", color = Ad.text) }, onClick = { menu = false; vm.dialog = Dialog.NewFile(n, FileKind.MARKDOWN) })
                DropdownMenuItem(text = { Text("New atlas", color = Ad.text) }, onClick = { menu = false; vm.dialog = Dialog.NewFile(n, FileKind.ATLAS) })
                DropdownMenuItem(text = { Text("New folder", color = Ad.text) }, onClick = { menu = false; vm.dialog = Dialog.NewFolder(n) })
            }
            DropdownMenuItem(text = { Text("Rename", color = Ad.text) }, onClick = { menu = false; vm.dialog = Dialog.Rename(n) })
            DropdownMenuItem(text = { Text("Delete", color = Ad.danger) }, onClick = { menu = false; vm.dialog = Dialog.Delete(n) })
        }
    }
}
