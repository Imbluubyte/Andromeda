package com.andromeda.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.andromeda.app.data.AtlasDoc
import com.andromeda.app.data.AtlasItem
import com.andromeda.app.data.Pt
import com.andromeda.app.data.Shape
import com.andromeda.app.data.Stroke
import com.andromeda.app.data.newId
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class Tool { SELECT, PEN, RECT, ELLIPSE, ERASER }

/**
 * All state of one open Atlas. World units are dp; screen px = world * density * zoom + pan.
 * Every persistent change bumps [version] (the editor autosaves on it).
 */
class AtlasState(initial: AtlasDoc) {
    val items = mutableStateListOf<AtlasItem>().apply { addAll(initial.items) }
    val strokes = mutableStateListOf<Stroke>().apply { addAll(initial.strokes) }
    val shapes = mutableStateListOf<Shape>().apply { addAll(initial.shapes) }

    var panX by mutableStateOf(initial.panX)
    var panY by mutableStateOf(initial.panY)
    var zoom by mutableStateOf(initial.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))

    var tool by mutableStateOf(Tool.SELECT)
    var color by mutableStateOf(Color(0xFFA78BFA))
    var width by mutableStateOf(3f)
    var selected by mutableStateOf<String?>(null)
    var draftStroke by mutableStateOf<Stroke?>(null)
    var draftShape by mutableStateOf<Shape?>(null)
    var version by mutableIntStateOf(0)

    var density = 1f
    var viewW = 0f
    var viewH = 0f

    private class Snap(val items: List<AtlasItem>, val strokes: List<Stroke>, val shapes: List<Shape>)
    private val history = ArrayList<Snap>()
    var canUndo by mutableStateOf(false); private set
    private var shapeStart = Offset.Zero

    fun toDoc() = AtlasDoc(items.toList(), strokes.toList(), shapes.toList(), panX, panY, zoom)

    private fun changed() { version++ }

    // ---- view ----
    fun toWorld(p: Offset) = Offset((p.x - panX) / (zoom * density), (p.y - panY) / (zoom * density))

    fun transform(centroid: Offset, pan: Offset, zoomChange: Float) {
        val nz = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val worldX = (centroid.x - panX) / zoom
        val worldY = (centroid.y - panY) / zoom
        panX = centroid.x + pan.x - worldX * nz
        panY = centroid.y + pan.y - worldY * nz
        zoom = nz
        changed()
    }

    fun viewCenterWorld() = toWorld(Offset(viewW / 2f, viewH / 2f))

    // ---- undo ----
    fun checkpoint() {
        history.add(Snap(items.toList(), strokes.toList(), shapes.toList()))
        if (history.size > 60) history.removeAt(0)
        canUndo = true
    }

    fun undo() {
        val s = history.removeLastOrNull() ?: return
        items.clear(); items.addAll(s.items)
        strokes.clear(); strokes.addAll(s.strokes)
        shapes.clear(); shapes.addAll(s.shapes)
        if (selected != null && items.none { it.id == selected }) selected = null
        canUndo = history.isNotEmpty()
        changed()
    }

    // ---- items ----
    fun addItem(make: (id: String, x: Float, y: Float) -> AtlasItem, w: Float, h: Float): AtlasItem {
        checkpoint()
        val c = viewCenterWorld()
        val item = make(newId(), c.x - w / 2f, c.y - h / 2f)
        items.add(item)
        selected = item.id
        changed()
        return item
    }

    fun replace(id: String, f: (AtlasItem) -> AtlasItem) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = f(items[i]); changed() }
    }

    fun move(id: String, dx: Float, dy: Float) = replace(id) { it.moved(it.x + dx, it.y + dy) }

    fun resize(id: String, dw: Float, dh: Float) =
        replace(id) { it.moved(it.x, it.y, max(60f, it.w + dw), max(40f, it.h + dh)) }

    fun bringToFront(id: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0 && i != items.lastIndex) { val it = items.removeAt(i); items.add(it); changed() }
    }

    fun deleteSelected() {
        val id = selected ?: return
        checkpoint()
        items.removeAll { it.id == id }
        selected = null
        changed()
    }

    // ---- drawing ----
    private fun argb() = color.toArgb().toLong() and 0xFFFFFFFFL

    fun beginDraw(p: Offset) {
        selected = null
        when (tool) {
            Tool.PEN -> draftStroke = Stroke(newId(), listOf(Pt(p.x, p.y)), argb(), width)
            Tool.RECT, Tool.ELLIPSE -> { shapeStart = p; draftShape = shapeBetween(p, p) }
            Tool.ERASER -> { checkpoint(); erase(p) }
            Tool.SELECT -> {}
        }
    }

    fun drawTo(p: Offset) {
        when (tool) {
            Tool.PEN -> draftStroke?.let { s ->
                val last = s.pts.last()
                if (hypot(p.x - last.x, p.y - last.y) > 0.6f) draftStroke = s.copy(pts = s.pts + Pt(p.x, p.y))
            }
            Tool.RECT, Tool.ELLIPSE -> draftShape = shapeBetween(shapeStart, p)
            Tool.ERASER -> erase(p)
            Tool.SELECT -> {}
        }
    }

    fun endDraw() {
        draftStroke?.let { s -> checkpoint(); strokes.add(s); changed() }
        draftShape?.let { s -> if (s.w > 4f || s.h > 4f) { checkpoint(); shapes.add(s); changed() } }
        draftStroke = null; draftShape = null
    }

    fun cancelDraw() { draftStroke = null; draftShape = null }

    private fun shapeBetween(a: Offset, b: Offset) = Shape(
        newId(), tool == Tool.ELLIPSE,
        min(a.x, b.x), min(a.y, b.y), abs(a.x - b.x), abs(a.y - b.y), argb(), width
    )

    private fun erase(p: Offset) {
        val r = 14f / zoom
        val sBefore = strokes.size; val hBefore = shapes.size
        strokes.removeAll { s -> nearStroke(s, p, r + s.width / 2f) }
        shapes.removeAll { s -> nearShape(s, p, r + s.width / 2f) }
        if (strokes.size != sBefore || shapes.size != hBefore) changed()
    }

    private fun nearStroke(s: Stroke, p: Offset, r: Float): Boolean {
        if (s.pts.size == 1) return hypot(p.x - s.pts[0].x, p.y - s.pts[0].y) <= r
        for (i in 0 until s.pts.size - 1) if (segDist(p, s.pts[i], s.pts[i + 1]) <= r) return true
        return false
    }

    private fun segDist(p: Offset, a: Pt, b: Pt): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    private fun nearShape(s: Shape, p: Offset, r: Float): Boolean {
        val hw = s.w / 2f; val hh = s.h / 2f
        val dx = abs(p.x - (s.x + hw)); val dy = abs(p.y - (s.y + hh))
        return if (s.ellipse) {
            val q = hypot(dx / max(hw, 1f), dy / max(hh, 1f))
            abs(q - 1f) * min(hw, hh).coerceAtLeast(1f) <= r
        } else {
            val outside = hypot(max(dx - hw, 0f), max(dy - hh, 0f))
            val d = if (dx <= hw && dy <= hh) min(hw - dx, hh - dy) else outside
            d <= r
        }
    }

    companion object {
        const val MIN_ZOOM = 0.15f
        const val MAX_ZOOM = 4f
    }
}
