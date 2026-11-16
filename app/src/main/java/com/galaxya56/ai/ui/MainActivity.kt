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
                            imageSeed = 424242L,
                            npuAutoUpscale = intent.getBooleanExtra("auto_upscale", it.npuAutoUpscale)
                        )
                    }
                    viewModel.generateLocalImage(prompt, "blurry, low quality")
                }
            }
        }
        intent?.getStringExtra("video_test")?.let { prompt ->
            viewModel.updateSettings {
                it.copy(
                    imageModelPath = intent.getStringExtra("image_model") ?: it.imageModelPath,
                    videoMotionPath = intent.getStringExtra("video_motion") ?: it.videoMotionPath,
                    videoFrames = intent.getIntExtra("video_frames", 8).coerceIn(8, 16),
                    videoFps = intent.getIntExtra("video_fps", 8),
                    videoWidth = intent.getIntExtra("video_width", 256),
                    videoHeight = intent.getIntExtra("video_height", 256),
                    videoSteps = intent.getIntExtra("video_steps", 8),
                    videoCfg = intent.getFloatExtra("video_cfg", 1.5f),
                    videoSampler = intent.getStringExtra("video_sampler") ?: "lcm",
                    videoSeed = intent.getLongExtra("video_seed", 424242L),
                    imageLoraPath = intent.getStringExtra("video_lora") ?: it.imageLoraPath
                )
            }
            viewModel.generateLocalVideo(prompt, "blurry, low quality, watermark")
        }
        if (intent?.getBooleanExtra("benchmark_test", false) == true) {
            viewModel.runGpuLayerBenchmark()
        }
        if (intent?.getBooleanExtra("enn_benchmark", false) == true) {
            viewModel.runEnnBenchmark()
        }
        intent?.getStringExtra("chat_test")?.let { prompt ->
            viewModel.updateSettings {
                it.copy(gpuLayers = intent.getIntExtra("gpu_layers", it.gpuLayers))
            }
            if (intent.getBooleanExtra("force_gpu_failure", false)) {
                viewModel.forceNextVulkanFailure()
            }
            if (intent.getBooleanExtra("fail_cpu_reload", false)) {
                viewModel.failNextCpuReload()
            }
            viewModel.send(prompt)
        }
    }
}
