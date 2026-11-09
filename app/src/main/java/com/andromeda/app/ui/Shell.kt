package com.andromeda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andromeda.app.AppViewModel
import com.andromeda.app.data.FileKind
import com.andromeda.app.data.Node

/** Tablet layout: ribbon | sidebar | (tab bar + editor). */
@Composable
fun Shell(vm: AppViewModel, onChangeFolder: () -> Unit) {
    Row(Modifier.fillMaxSize().background(Ad.bg)) {
        Ribbon(vm)
        VerticalLine()
        if (vm.sidebarOpen) {
            Sidebar(vm, onChangeFolder, Modifier.width(280.dp))
            VerticalLine()
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TabBar(vm)
            HorizontalDivider(color = Ad.border)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val node = vm.active
                if (node == null) EmptyState() else key(node.uri) {
                    when (node.kind) {
                        FileKind.MARKDOWN -> MarkdownEditor(vm, node)
                        FileKind.ATLAS -> AtlasEditor(vm, node)
                        else -> EmptyState()
                    }
                }
            }
        }
    }
    Dialogs(vm)
    vm.error?.let { msg -> ErrorToast(msg) { vm.error = null } }
}

@Composable
private fun VerticalLine() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Ad.border))
}

@Composable
private fun Ribbon(vm: AppViewModel) {
    Column(
        Modifier.width(48.dp).fillMaxHeight().background(Ad.sidebar).padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBtn(Icons.Filled.Menu, "Toggle sidebar", { vm.sidebarOpen = !vm.sidebarOpen }, selected = vm.sidebarOpen)
    }
}

@Composable
private fun TabBar(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(Ad.sidebar).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        vm.tabs.forEach { n -> Tab(n, n == vm.active, { vm.open(n) }, { vm.close(n) }) }
    }
}

@Composable
private fun Tab(n: Node, active: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxHeight()
            .background(if (active) Ad.bg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (n.kind == FileKind.ATLAS) Icons.Filled.Dashboard else Icons.Filled.Description,
            null, tint = if (active) Ad.accent else Ad.faint, modifier = Modifier.size(14.dp)
        )
        Text(n.title, color = if (active) Ad.text else Ad.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
        IconBtn(Icons.Filled.Close, "Close tab", onClose, size = 28)
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Open a note or an atlas from the sidebar", color = Ad.faint, fontSize = 15.sp)
    }
}
