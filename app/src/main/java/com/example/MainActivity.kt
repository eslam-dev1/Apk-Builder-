package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.example.data.CloudAiConfig
import com.example.data.GeminiModels
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)
        GeminiModels.load(this)
        CloudAiConfig.load(this)

        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                AppNavGraph()
            }
        }
    }
}
