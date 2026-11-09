package com.andromeda.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

val PALETTE = listOf(
    0xFFFFFFFF, 0xFFE5645F, 0xFFF2994A, 0xFFF2C94C, 0xFF6FCF97, 0xFF56CCF2, 0xFF8AB4F8, 0xFFA78BFA, 0xFFEB6FAE, 0xFF2B2B2B
).map { Color(it) }

private fun hsv(c: Color): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }
private fun fromHsv(h: Float, s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

/** Colour wheel (hue around, saturation outwards) + brightness slider + quick palette. */
@Composable
fun ColorWheel(color: Color, onChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    val (h, s, v) = hsv(color).let { Triple(it[0], it[1], it[2]) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(180.dp)
                .pointerInput(v) {
                    fun pick(p: Offset) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = min(size.width, size.height) / 2f
                        val dx = p.x - c.x; val dy = p.y - c.y
                        var ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        if (ang < 0) ang += 360f
                        onChange(fromHsv(ang, (hypot(dx, dy) / r).coerceIn(0f, 1f), if (v < 0.05f) 1f else v))
                    }
                    detectTapGestures { pick(it) }
                }
                .pointerInput(v) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = min(size.width, size.height) / 2f
                        val dx = change.position.x - c.x; val dy = change.position.y - c.y
                        var ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        if (ang < 0) ang += 360f
                        onChange(fromHsv(ang, (hypot(dx, dy) / r).coerceIn(0f, 1f), if (v < 0.05f) 1f else v))
                    }
                }
        ) {
            val r = size.minDimension / 2f
            val hues = (0..360 step 30).map { fromHsv(it.toFloat() % 360f, 1f, 1f) }
            drawCircle(Brush.sweepGradient(hues, center), radius = r)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, r), radius = r)
            drawCircle(Color.Black.copy(alpha = 1f - v), radius = r)
            val a = Math.toRadians(h.toDouble())
            val marker = Offset(center.x + (kotlin.math.cos(a) * s * r).toFloat(), center.y + (kotlin.math.sin(a) * s * r).toFloat())
            drawCircle(Color.White, 9.dp.toPx(), marker, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        Slider(
            value = v, onValueChange = { onChange(fromHsv(h, s, it)) },
            colors = SliderDefaults.colors(thumbColor = Ad.accent, activeTrackColor = Ad.accent, inactiveTrackColor = Ad.border),
            modifier = Modifier.fillMaxWidth(0.9f)
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PALETTE.forEach { p ->
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(p)
                        .border(1.dp, if (p == color) Ad.accent else Ad.border, CircleShape)
                        .clickable { onChange(p) }
                )
            }
        }
    }
}
