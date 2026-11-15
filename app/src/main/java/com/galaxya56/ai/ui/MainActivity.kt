package com.galaxya56.ai.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.galaxya56.ai.BuildConfig
import com.galaxya56.ai.ui.theme.A56Theme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) handleDebugIntent(intent)
        setContent {
            A56Theme {
                val status by viewModel.status.collectAsStateWithLifecycle()
                AppRoot(viewModel = viewModel, globalStatus = status)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (BuildConfig.DEBUG) handleDebugIntent(intent)
    }

    private fun handleDebugIntent(intent: Intent?) {
        intent?.getStringExtra("studio_image")?.let(viewModel::setStudioImage)
        intent?.getStringExtra("enn_model_path")?.let(viewModel::setDebugEnnModelPath)
    }
}
