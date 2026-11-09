package com.andromeda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Compact square icon button used across the ribbon, toolbars and tree. */
@Composable
fun IconBtn(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ad.muted,
    selected: Boolean = false,
    size: Int = 36
) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Ad.accentDim else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (selected) Ad.accent else tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}
