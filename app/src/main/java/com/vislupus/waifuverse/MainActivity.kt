package com.vislupus.waifuverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.vislupus.waifuverse.config.AppConfig
import com.vislupus.waifuverse.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot(viewModel)
        }
    }

    override fun onStop() {
        super.onStop()
        if (AppConfig.STOP_SESSION_IN_BACKGROUND) viewModel.stopSession()
    }
}
