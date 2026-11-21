package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.AetherCockpitScreen
import com.example.ui.theme.Aether3DTheme
import com.example.ui.viewmodel.JarvisViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or ViewModel creation
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val jarvisViewModel: JarvisViewModel = viewModel()
            val uiState by jarvisViewModel.uiState.collectAsStateWithLifecycle()

            Aether3DTheme(persona = uiState.preferences.persona) {
                AetherCockpitScreen(viewModel = jarvisViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}
