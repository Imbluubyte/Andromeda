package com.andromeda.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First-launch screen: the user picks the folder that will hold all project folders. */
@Composable
fun Welcome(onPick: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Andromeda", color = Ad.text, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("A second brain for game design", color = Ad.muted, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))
        Text(
            "Choose a folder to keep your projects in. Each project is a folder inside it.",
            color = Ad.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp)
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onPick,
            colors = ButtonDefaults.buttonColors(containerColor = Ad.accent, contentColor = Ad.bg)
        ) { Text("Choose folder") }
    }
}
