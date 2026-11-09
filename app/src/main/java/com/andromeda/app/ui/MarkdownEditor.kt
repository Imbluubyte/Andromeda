package com.andromeda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andromeda.app.AppViewModel
import com.andromeda.app.data.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MarkdownEditor(vm: AppViewModel, node: Node) {
    val ws = vm.ws ?: return
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }

    LaunchedEffect(node) {
        val t = withContext(Dispatchers.IO) { runCatching { ws.read(node) }.getOrDefault("") }
        text = t; saved = t; loaded = true
        preview = t.isNotBlank()
    }

    // Debounced autosave while typing.
    LaunchedEffect(text, loaded) {
        if (!loaded || text == saved) return@LaunchedEffect
        delay(500)
        val snapshot = text
        withContext(Dispatchers.IO) { runCatching { ws.write(node, snapshot) } }
        saved = snapshot
    }

    // Flush on close / tab switch.
    DisposableEffect(node) {
        onDispose {
            if (loaded && text != saved) {
                val snapshot = text
                CoroutineScope(Dispatchers.IO).launch { runCatching { ws.write(node, snapshot) } }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Ad.bg)) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(node.title, color = Ad.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            val words = remember(text) { text.split(Regex("\\s+")).count { it.isNotBlank() } }
            Text("$words words", color = Ad.faint, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            IconBtn(
                if (preview) Icons.Filled.Edit else Icons.Filled.MenuBook,
                if (preview) "Edit" else "Reading view",
                { preview = !preview }
            )
        }
        if (!loaded) return@Column
        if (preview) {
            MarkdownPreview(text, onWiki = { vm.openByTitle(it) }, modifier = Modifier.weight(1f))
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val minH = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(color = Ad.text, fontSize = 16.sp, lineHeight = 24.sp),
                        cursorBrush = SolidColor(Ad.accent),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = minH).padding(horizontal = 32.dp, vertical = 20.dp)
                    )
                }
            }
        }
    }
}
