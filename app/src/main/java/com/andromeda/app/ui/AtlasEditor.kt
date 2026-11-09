package com.andromeda.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.andromeda.app.AppViewModel
import com.andromeda.app.data.AtlasItem
import com.andromeda.app.data.AtlasJson
import com.andromeda.app.data.Node
import com.andromeda.app.data.Shape
import com.andromeda.app.data.Stroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AtlasEditor(vm: AppViewModel, node: Node) {
    val ws = vm.ws ?: return
    val project = vm.project ?: return
    var state by remember { mutableStateOf<AtlasState?>(null) }

    LaunchedEffect(node) {
        val text = withContext(Dispatchers.IO) { runCatching { ws.read(node) }.getOrDefault("") }
        state = AtlasState(AtlasJson.decode(text))
    }
    val st = state ?: return
    st.density = LocalDensity.current.density
    val env = remember(node, project) { AtlasEnv(ws, project.doc) { vm.openByTitle(it) } }
    val scope = rememberCoroutineScope()

    // Debounced autosave on every persistent change.
    LaunchedEffect(st.version) {
        if (st.version == 0) return@LaunchedEffect
        delay(600)
        val json = AtlasJson.encode(st.toDoc())
        withContext(Dispatchers.IO) { runCatching { ws.write(node, json) } }
    }
    DisposableEffect(st) {
        onDispose {
            if (st.version > 0) {
                val json = AtlasJson.encode(st.toDoc())
                CoroutineScope(Dispatchers.IO).launch { runCatching { ws.write(node, json) } }
            }
        }
    }

    var dialog by remember { mutableStateOf<AtlasDialog?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) { ws.importImage(project.doc, uri) }
            if (name != null) st.addItem({ id, x, y -> AtlasItem.Image(id, x, y, file = name) }, 300f, 220f)
        }
    }

    Column(Modifier.fillMaxSize()) {
        AtlasToolbar(st, onImage = { imagePicker.launch("image/*") }, onDialog = { dialog = it })
        HorizontalDivider(color = Ad.border)
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(0.dp)).background(Ad.bg).onSizeChanged { st.viewW = it.width.toFloat(); st.viewH = it.height.toFloat() }) {
            Canvas(Modifier.fillMaxSize().canvasGestures(st, drawing = false)) { dotGrid(st) }
            st.items.forEach { item -> androidx.compose.runtime.key(item.id) { ItemView(st, item, env) } }
            Canvas(Modifier.fillMaxSize()) { ink(st) }
            if (st.tool != Tool.SELECT) Box(Modifier.fillMaxSize().canvasGestures(st, drawing = true))
        }
    }

    when (val d = dialog) {
        null -> {}
        AtlasDialog.Url -> UrlDialog({ dialog = null }) { url ->
            st.addItem({ id, x, y -> AtlasItem.Embed(id, x, y, url = url) }, 320f, 240f); dialog = null
        }
        AtlasDialog.File -> FilePickerDialog(vm, { dialog = null }) { title ->
            st.addItem({ id, x, y -> AtlasItem.FileLink(id, x, y, title = title) }, 220f, 64f); dialog = null
        }
    }
}

private enum class AtlasDialog { Url, File }

private fun Modifier.canvasGestures(st: AtlasState, drawing: Boolean): Modifier = pointerInput(st.tool, drawing) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var multi = false
        val tool = st.tool
        if (!drawing) st.selected = null
        if (drawing) st.beginDraw(st.toWorld(down.position))
        do {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                if (!multi) { multi = true; st.cancelDraw() }
                st.transform(event.calculateCentroid(), event.calculatePan(), event.calculateZoom())
                event.changes.forEach { it.consume() }
            } else if (!multi) {
                val c = pressed.firstOrNull() ?: break
                if (drawing) st.drawTo(st.toWorld(c.position))
                else st.transform(c.position, c.positionChange(), 1f)
                c.consume()
            }
        } while (event.changes.any { it.pressed })
        if (drawing && !multi && tool != Tool.SELECT) st.endDraw()
    }
}

private fun DrawScope.dotGrid(st: AtlasState) {
    val step = 32f * st.density * st.zoom
    if (step < 10f) return
    val ox = ((st.panX % step) + step) % step
    val oy = ((st.panY % step) + step) % step
    var x = ox
    while (x < size.width) {
        var y = oy
        while (y < size.height) { drawCircle(Ad.border, 1.6f * st.density, Offset(x, y)); y += step }
        x += step
    }
}

private fun DrawScope.ink(st: AtlasState) {
    val k = st.density * st.zoom
    fun sx(x: Float) = st.panX + x * k
    fun sy(y: Float) = st.panY + y * k
    fun stroke(s: Stroke) {
        val col = Color(s.color)
        val w = s.width * k
        if (s.pts.size == 1) { drawCircle(col, w / 2f, Offset(sx(s.pts[0].x), sy(s.pts[0].y))); return }
        val path = Path()
        path.moveTo(sx(s.pts[0].x), sy(s.pts[0].y))
        for (i in 1 until s.pts.size) {
            val p0 = s.pts[i - 1]; val p1 = s.pts[i]
            path.quadraticTo(sx(p0.x), sy(p0.y), sx((p0.x + p1.x) / 2f), sy((p0.y + p1.y) / 2f))
        }
        path.lineTo(sx(s.pts.last().x), sy(s.pts.last().y))
        drawPath(path, col, style = DrawStroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    fun shape(s: Shape) {
        val col = Color(s.color)
        val tl = Offset(sx(s.x), sy(s.y)); val sz = Size(s.w * k, s.h * k)
        val style = DrawStroke(s.width * k, join = StrokeJoin.Round)
        if (s.ellipse) drawOval(col, tl, sz, style = style) else drawRect(col, tl, sz, style = style)
    }
    st.strokes.forEach(::stroke)
    st.shapes.forEach(::shape)
    st.draftStroke?.let(::stroke)
    st.draftShape?.let(::shape)
}

@Composable
private fun AtlasToolbar(st: AtlasState, onImage: () -> Unit, onDialog: (AtlasDialog) -> Unit) {
    var colorOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(Ad.sidebar).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBtn(Icons.Filled.NearMe, "Select / move", { st.tool = Tool.SELECT }, selected = st.tool == Tool.SELECT)
        IconBtn(Icons.Filled.Brush, "Pen", { st.tool = Tool.PEN }, selected = st.tool == Tool.PEN)
        IconBtn(Icons.Filled.CheckBoxOutlineBlank, "Rectangle", { st.tool = Tool.RECT }, selected = st.tool == Tool.RECT)
        IconBtn(Icons.Filled.Circle, "Ellipse", { st.tool = Tool.ELLIPSE }, selected = st.tool == Tool.ELLIPSE)
        IconBtn(Icons.Filled.AutoFixNormal, "Eraser", { st.tool = Tool.ERASER }, selected = st.tool == Tool.ERASER)
        Box {
            Box(
                Modifier.padding(horizontal = 8.dp).size(26.dp).clip(CircleShape).background(st.color)
                    .clickable { colorOpen = !colorOpen }
            )
            if (colorOpen) Popup(onDismissRequest = { colorOpen = false }, offset = androidx.compose.ui.unit.IntOffset(0, 120)) {
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Ad.raised).padding(14.dp).width(220.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ColorWheel(st.color, {
                        st.color = it
                        val sel = st.items.firstOrNull { i -> i.id == st.selected }
                        if (sel is AtlasItem.Text) st.replace(sel.id) { t -> (t as AtlasItem.Text).copy(color = it.toArgb().toLong() and 0xFFFFFFFFL) }
                    })
                    Text("Brush size", color = Ad.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Slider(
                        value = st.width, onValueChange = { st.width = it }, valueRange = 1f..24f,
                        colors = SliderDefaults.colors(thumbColor = Ad.accent, activeTrackColor = Ad.accent, inactiveTrackColor = Ad.border)
                    )
                }
            }
        }
        Box(Modifier.width(1.dp).height(22.dp).background(Ad.border))
        IconBtn(Icons.Filled.TextFields, "Add text box", {
            st.tool = Tool.SELECT
            st.addItem({ id, x, y -> AtlasItem.Text(id, x, y) }, 260f, 120f)
        }, modifier = Modifier.padding(start = 6.dp))
        IconBtn(Icons.Filled.AddPhotoAlternate, "Add picture", onImage)
        IconBtn(Icons.Filled.Description, "Link to file", { st.tool = Tool.SELECT; onDialog(AtlasDialog.File) })
        IconBtn(Icons.Filled.Link, "Embed web link", { st.tool = Tool.SELECT; onDialog(AtlasDialog.Url) })
        Box(Modifier.weight(1f))
        if (st.selected != null) IconBtn(Icons.Filled.Delete, "Delete selected", { st.deleteSelected() }, tint = Ad.danger)
        IconBtn(Icons.Filled.Undo, "Undo", { st.undo() }, tint = if (st.canUndo) Ad.text else Ad.faint)
    }
}

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Ad.raised,
        title = { Text("Embed web link", color = Ad.text) },
        text = {
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true, placeholder = { Text("https://", color = Ad.faint) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Ad.text, unfocusedTextColor = Ad.text,
                    focusedBorderColor = Ad.accent, unfocusedBorderColor = Ad.border, cursorColor = Ad.accent
                )
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val u = url.trim()
                if (u.isNotEmpty()) onOk(if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u")
            }) { Text("Add", color = Ad.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ad.muted) } }
    )
}

@Composable
private fun FilePickerDialog(vm: AppViewModel, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ws = vm.ws
    val project = vm.project
    var files by remember { mutableStateOf<List<Node>?>(null) }
    LaunchedEffect(project) {
        files = if (ws != null && project != null) withContext(Dispatchers.IO) { ws.allFiles(project.doc) } else emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Ad.raised,
        title = { Text("Link to file", color = Ad.text) },
        text = {
            val list = files
            when {
                list == null -> Text("Loading", color = Ad.muted)
                list.isEmpty() -> Text("No notes or atlases in this project yet.", color = Ad.muted)
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(list) { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(f.title) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (f.kind == com.andromeda.app.data.FileKind.ATLAS) Icons.Filled.Dashboard else Icons.Filled.Description,
                                null, tint = Ad.muted, modifier = Modifier.size(16.dp)
                            )
                            Text(f.title, color = Ad.text, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ad.muted) } }
    )
}
