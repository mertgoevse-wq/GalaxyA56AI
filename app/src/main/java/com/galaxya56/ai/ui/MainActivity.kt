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
        intent?.getStringExtra("token_test")?.let { value ->
            viewModel.updateSettings { it.copy(hfToken = value) }
        }
        intent?.getStringExtra("token_clear")?.let {
            viewModel.updateSettings { it.copy(hfToken = "") }
        }
        intent?.getStringExtra("music_test")?.let { prompt ->
            intent.getStringExtra("music_minutes")?.toFloatOrNull()?.let { minutes ->
                viewModel.updateSettings { it.copy(musicMinutes = minutes) }
            }
            viewModel.generateMusic(prompt)
        }
        intent?.getStringExtra("control_test")?.let { prompt ->
            intent.getStringExtra("control_net")?.let { net ->
                intent.getStringExtra("control_image")?.let { control ->
                    viewModel.updateSettings {
                        it.copy(
                            imageModelPath = intent.getStringExtra("image_model") ?: it.imageModelPath,
                            imageControlNetPath = net,
                            imageControlImagePath = control,
                            imageControlType = "canny",
                            imageControlCanny = true,
                            imageControlStrength = 0.9f,
                            imageSteps = 4,
                            imageCfg = 1.5f,
                            imageSampler = "lcm",
                            imageWidth = 256,
                            imageHeight = 256,
                            imageSeed = 424242L
                        )
                    }
                    viewModel.generateLocalImage(prompt, "blurry, low quality")
                }
            }
        }
    }
}
