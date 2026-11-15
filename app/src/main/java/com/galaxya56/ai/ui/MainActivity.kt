package com.galaxya56.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.galaxya56.ai.ui.theme.A56Theme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            A56Theme {
                val status by viewModel.status.collectAsStateWithLifecycle()
                AppRoot(viewModel = viewModel, globalStatus = status)
            }
        }
    }
}
