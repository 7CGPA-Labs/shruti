package org.seven_cgpalabs.shruti.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.seven_cgpalabs.shruti.core.CallSessionSummary
import org.seven_cgpalabs.shruti.core.ShrutiAudioEngine
import org.seven_cgpalabs.shruti.core.ShrutiVaultManager
import org.seven_cgpalabs.shruti.service.ShrutiCallListenerService
import org.seven_cgpalabs.shruti.ui.components.AcousticOrbitVisualizer
import org.seven_cgpalabs.shruti.ui.components.DebriefCard
import org.seven_cgpalabs.shruti.ui.components.VisualizerState
import org.seven_cgpalabs.shruti.ui.components.VoicePresetSelector

class ShrutiMainActivity : ComponentActivity() {

    private val audioEngine = ShrutiAudioEngine()
    private lateinit var vaultManager: ShrutiVaultManager
    private var isVulkanSupported by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioEngine.initialize()
        vaultManager = ShrutiVaultManager(this)
        isVulkanSupported = audioEngine.isVulkanSupported()

        // Populate mock delivery session if vault is fresh
        seedInitialDemoData()

        setContent {
            MaterialTheme {
                MainScreen(
                    isVulkanSupported = isVulkanSupported,
                    audioEngine = audioEngine,
                    vaultManager = vaultManager
                )
            }
        }
    }

    private fun seedInitialDemoData() {
        val existing = vaultManager.getAllSessions()
        if (existing.isEmpty()) {
            val dummyTrajectory = List(6) { FloatArray(ShrutiAudioEngine.INTENT_DIM) { 0.1f } }
            vaultManager.storeCallSession(
                sessionId = "demo-delivery-001",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                callType = "delivery",
                callerLabel = "Swiggy Courier (Gate 2)",
                actionType = "gate_drop",
                durationSeconds = 42,
                trajectoryMatrix = dummyTrajectory
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.teardown()
    }
}

@Composable
fun MainScreen(
    isVulkanSupported: Boolean,
    audioEngine: ShrutiAudioEngine,
    vaultManager: ShrutiVaultManager
) {
    val coroutineScope = rememberCoroutineScope()
    var visualizerState by remember { mutableStateOf(VisualizerState.IDLE) }
    var isPassiveListening by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf(ShrutiAudioEngine.SpeakerPreset.ADITI) }
    var playingSessionId by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf<List<CallSessionSummary>>(emptyList()) }

    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) {
            vaultManager.getAllSessions()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F14))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title & Badges
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "S.H.R.U.T.I.",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Hardware & Privacy Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hardware Badge
                        StatusPill(
                            icon = Icons.Default.Speed,
                            text = if (isVulkanSupported) "Vulkan 1.1+ GPU" else "ARM NEON CPU",
                            color = if (isVulkanSupported) Color(0xFF1DE9B6) else Color(0xFFFF9100)
                        )

                        // Privacy Badge
                        StatusPill(
                            icon = Icons.Default.Security,
                            text = "Zero-Persistence / AES-256",
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }

            // Hero Section: Harmonic Acoustic Orbit Visualizer
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AcousticOrbitVisualizer(state = visualizerState)
                    Spacer(modifier = Modifier.height(12.dp))

                    val statusText = when (visualizerState) {
                        VisualizerState.IDLE -> "Passive In-Call Listener Ready"
                        VisualizerState.PASSIVE_LISTENING -> "Passively Vectorizing Live Audio..."
                        VisualizerState.VULKAN_DEBRIEF_SYNTHESIS -> "Synthesizing Debrief on Vulkan GPU..."
                    }
                    val statusColor = when (visualizerState) {
                        VisualizerState.IDLE -> Color(0xFF8B949E)
                        VisualizerState.PASSIVE_LISTENING -> Color(0xFF7C4DFF)
                        VisualizerState.VULKAN_DEBRIEF_SYNTHESIS -> Color(0xFFFF9100)
                    }

                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Passive Listener Control Button
            item {
                Button(
                    onClick = {
                        isPassiveListening = !isPassiveListening
                        visualizerState = if (isPassiveListening) {
                            VisualizerState.PASSIVE_LISTENING
                        } else {
                            VisualizerState.IDLE
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPassiveListening) Color(0xFFFF5252) else Color(0xFF161A22)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isPassiveListening) Color(0xFFFF5252) else Color(0xFF282E3A), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = if (isPassiveListening) Icons.Default.MicOff else Icons.Default.Hearing,
                        contentDescription = null,
                        tint = if (isPassiveListening) Color.White else Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPassiveListening) "Stop Passive Listening" else "Simulate Passive In-Call Listener",
                        color = if (isPassiveListening) Color.White else Color(0xFFC9D1D9),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Voice Preset Selector
            item {
                VoicePresetSelector(
                    selectedPreset = selectedPreset,
                    onSelectPreset = { preset ->
                        selectedPreset = preset
                        val mockVec = FloatArray(ShrutiAudioEngine.INTENT_DIM) { 0.05f }
                        audioEngine.setSpeakerPreset(mockVec)
                    }
                )
            }

            // Recent Spoken Debriefs Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT SPOKEN DEBRIEFS",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${sessions.size} sessions",
                        color = Color(0xFF58A6FF),
                        fontSize = 11.sp
                    )
                }
            }

            // Debrief Cards
            items(sessions) { session ->
                val isPlaying = playingSessionId == session.sessionId

                DebriefCard(
                    session = session,
                    isPlaying = isPlaying,
                    onPlayDebrief = {
                        if (isPlaying) {
                            playingSessionId = null
                            visualizerState = VisualizerState.IDLE
                        } else {
                            playingSessionId = session.sessionId
                            visualizerState = VisualizerState.VULKAN_DEBRIEF_SYNTHESIS

                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    val trajectory = vaultManager.loadCallTrajectory(session.sessionId)
                                    if (trajectory != null && trajectory.isNotEmpty()) {
                                        val pcmAudio = audioEngine.synthesizeDebrief(trajectory)
                                        if (pcmAudio.isNotEmpty()) {
                                            playPcmAudio(pcmAudio)
                                        }
                                    }
                                }
                                playingSessionId = null
                                visualizerState = VisualizerState.IDLE
                            }
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun StatusPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161A22))
            .border(1.dp, Color(0xFF282E3A), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            color = Color(0xFFC9D1D9),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Streams synthesized 16 kHz 16-bit PCM audio directly to the speaker using AudioTrack.
 */
private fun playPcmAudio(pcmData: ShortArray) {
    val sampleRate = ShrutiAudioEngine.SAMPLE_RATE
    val minBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(maxOf(minBufferSize, pcmData.size * 2))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    try {
        audioTrack.play()
        audioTrack.write(pcmData, 0, pcmData.size)
        // Let playback drain
        Thread.sleep((pcmData.size.toLong() * 1000L) / sampleRate.toLong())
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        audioTrack.stop()
        audioTrack.release()
    }
}
