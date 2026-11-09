package com.andromeda.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.andromeda.app.data.AtlasItem
import com.andromeda.app.data.ImageCache
import com.andromeda.app.data.LinkMeta
import com.andromeda.app.data.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** What items need from the app: file access for images and a way to open linked files. */
class AtlasEnv(val ws: Workspace, val project: DocumentFile, val openTitle: (String) -> Unit)

@Composable
fun ItemView(state: AtlasState, item: AtlasItem, env: AtlasEnv) {
    when (item) {
        is AtlasItem.Text -> TextCard(state, item)
        is AtlasItem.Image -> ImageCard(state, item, env)
        is AtlasItem.FileLink -> FileLinkCard(state, item, env)
        is AtlasItem.Embed -> EmbedCard(state, item)
    }
}

/** Positions an item on the canvas (scaled by zoom) and handles select / drag / resize. */
@Composable
private fun ItemFrame(
    state: AtlasState,
    item: AtlasItem,
    background: Color = Ad.raised,
    onOpen: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val sel = state.selected == item.id
    val d = state.density
    Box(
        Modifier
            .offset { IntOffset((item.x * d * state.zoom + state.panX).roundToInt(), (item.y * d * state.zoom + state.panY).roundToInt()) }
            .graphicsLayer { scaleX = state.zoom; scaleY = state.zoom; transformOrigin = TransformOrigin(0f, 0f) }
            .size(item.w.dp, item.h.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(if (sel) 2.dp else 1.dp, if (sel) Ad.accent else Ad.border, RoundedCornerShape(8.dp))
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { state.selected = item.id; state.bringToFront(item.id) },
                    onDoubleTap = { onOpen?.invoke() }
                )
            }
            .pointerInput(item.id) {
                detectDragGestures(onDragStart = { state.selected = item.id; state.checkpoint() }) { change, drag ->
                    change.consume()
                    state.move(item.id, drag.x / d, drag.y / d)
                }
            }
    ) {
        content()
        if (sel) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(30.dp).pointerInput(item.id) {
                    detectDragGestures(onDragStart = { state.checkpoint() }) { change, drag ->
                        change.consume()
                        state.resize(item.id, drag.x / d, drag.y / d)
                    }
                },
                contentAlignment = Alignment.BottomEnd
            ) {
                Box(Modifier.padding(5.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(Ad.accent))
            }
        }
    }
}

@Composable
private fun TextCard(state: AtlasState, item: AtlasItem.Text) {
    val bg = Color(item.color)
    val fg = if (bg.luminance() < 0.5f) Ad.text else Color(0xFF1E1E1E)
    ItemFrame(state, item, background = bg) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(16.dp).background(fg.copy(alpha = 0.08f)))
            Box(Modifier.padding(10.dp)) {
                if (state.selected == item.id) {
                    BasicTextField(
                        value = item.text,
                        onValueChange = { v -> state.replace(item.id) { (it as AtlasItem.Text).copy(text = v) } },
                        textStyle = TextStyle(color = fg, fontSize = 15.sp, lineHeight = 21.sp),
                        cursorBrush = SolidColor(Ad.accent),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        item.text.ifBlank { "Tap to select, then type" },
                        color = if (item.text.isBlank()) fg.copy(alpha = 0.4f) else fg,
                        fontSize = 15.sp, lineHeight = 21.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageCard(state: AtlasState, item: AtlasItem.Image, env: AtlasEnv) {
    val bmp by produceState<ImageBitmap?>(null, item.file) {
        value = withContext(Dispatchers.IO) {
            ImageCache.get(item.file) ?: env.ws.attachment(env.project, item.file)
                ?.let { env.ws.readBytes(it) }?.let { ImageCache.decode(item.file, it) }
        }
    }
    ItemFrame(state, item, background = Ad.bg) {
        bmp?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            ?: Text("Loading image", color = Ad.faint, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun FileLinkCard(state: AtlasState, item: AtlasItem.FileLink, env: AtlasEnv) {
    ItemFrame(state, item, onOpen = { env.openTitle(item.title) }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, null, tint = Ad.accent, modifier = Modifier.size(20.dp))
            Text(
                item.title, color = Ad.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun EmbedCard(state: AtlasState, item: AtlasItem.Embed) {
    val uri = LocalUriHandler.current
    // Fetch title + thumbnail once if the card has none yet.
    LaunchedEffect(item.id, item.title, item.thumb) {
        if (item.title.isBlank() && item.thumb.isBlank()) {
            val m = withContext(Dispatchers.IO) { LinkMeta.fetch(item.url) }
            if (m.title.isNotBlank() || m.thumb.isNotBlank())
                state.replace(item.id) { (it as AtlasItem.Embed).copy(title = m.title, thumb = m.thumb) }
        }
    }
    val thumb by produceState<ImageBitmap?>(null, item.thumb) {
        value = if (item.thumb.isBlank()) null else withContext(Dispatchers.IO) { ImageCache.loadUrl(item.thumb) }
    }
    ItemFrame(state, item, onOpen = { runCatching { uri.openUri(item.url) } }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Ad.bg), contentAlignment = Alignment.Center) {
                thumb?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    ?: Icon(Icons.Filled.Link, null, tint = Ad.faint, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text(
                    item.title.ifBlank { item.url }, color = Ad.text, fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.url.substringAfter("://").substringBefore('/').removePrefix("www."),
                    color = Ad.faint, fontSize = 12.sp, maxLines = 1
                )
            }
        }
    }
}
