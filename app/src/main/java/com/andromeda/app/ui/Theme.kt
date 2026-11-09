package com.andromeda.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Obsidian-like dark palette. Use these instead of hard-coded colours. */
object Ad {
    val bg = Color(0xFF1E1E1E)
    val sidebar = Color(0xFF262626)
    val raised = Color(0xFF2F2F2F)
    val hover = Color(0xFF363636)
    val border = Color(0xFF3A3A3A)
    val text = Color(0xFFDADADA)
    val muted = Color(0xFF9A9A9A)
    val faint = Color(0xFF6B6B6B)
    val accent = Color(0xFFA78BFA)
    val accentDim = Color(0xFF3B3159)
    val danger = Color(0xFFE5645F)
    val link = Color(0xFF8AB4F8)
}

@Composable
fun AndromedaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ad.accent,
            onPrimary = Color(0xFF1E1E1E),
            background = Ad.bg,
            onBackground = Ad.text,
            surface = Ad.sidebar,
            onSurface = Ad.text,
            surfaceVariant = Ad.raised,
            onSurfaceVariant = Ad.muted,
            outline = Ad.border,
            error = Ad.danger
        ),
        content = content
    )
}
