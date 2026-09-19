package org.seven_cgpalabs.shruti.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.seven_cgpalabs.shruti.core.ShrutiAudioEngine

class ShrutiMainActivity : ComponentActivity() {

    private val audioEngine = ShrutiAudioEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        audioEngine.initialize()

        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.teardown()
    }
}

@Composable
fun MainScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F14)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "S.H.R.U.T.I. Engine Active",
            color = Color(0xFF00E5FF)
        )
    }
}
