package com.frameforge.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.frameforge.ai.ui.FrameForgeMainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Classic Clean Android Dark Theme (Material Design 3)
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF1976D2),         // Classic Material Blue
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFF0D47A1),// Deep Indigo Container
                    onPrimaryContainer = Color.White,
                    secondary = Color(0xFF0288D1),       // Light Material Blue
                    onSecondary = Color.White,
                    background = Color(0xFF121212),      // Classic Android Dark
                    onBackground = Color(0xFFE0E0E0),
                    surface = Color(0xFF1E1E1E),         // Standard Material Card
                    onSurface = Color(0xFFE0E0E0),
                    surfaceVariant = Color(0xFF2C2C2C),
                    onSurfaceVariant = Color(0xFFB0BEC5),
                    outline = Color(0xFF3E3E3E)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FrameForgeMainScreen()
                }
            }
        }
    }
}
