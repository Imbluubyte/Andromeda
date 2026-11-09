package com.andromeda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import com.andromeda.app.ui.AndromedaTheme
import com.andromeda.app.ui.Ad
import com.andromeda.app.ui.Shell
import com.andromeda.app.ui.Welcome

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndromedaTheme {
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) vm.setRoot(uri)
                }
                val pick = { picker.launch(null) }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Ad.bg).statusBarsPadding()) {
                    if (vm.ws == null) Welcome(onPick = pick) else Shell(vm, onChangeFolder = pick)
                }
            }
        }
    }
}
