package org.seven_cgpalabs.shruti.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.seven_cgpalabs.shruti.core.ShrutiAudioEngine
import org.seven_cgpalabs.shruti.core.ShrutiVaultManager
import org.seven_cgpalabs.shruti.ui.ShrutiMainActivity
import java.util.UUID

/**
 * Passive In-Call Listener Service for S.H.R.U.T.I.
 * Strictly adheres to the Passive Listener Invariant:
 * - NEVER intercepts or screens phone calls live.
 * - NEVER speaks or plays audio into the phone call uplink.
 * - Ingests call audio in 30-second sliding windows, extracts 512-d trajectory vectors on mobile Vulkan GPU,
 *   wipes volatile PCM memory, and saves encrypted session vectors to SQLCipher upon call termination.
 */
class ShrutiCallListenerService : Service() {

    companion object {
        private const val TAG = "ShrutiCallListener"
        private const val NOTIFICATION_ID = 4001
        private const val DEBRIEF_NOTIFICATION_ID = 4002
        private const val CHANNEL_ID = "shruti_call_listener_channel"
        private const val DEBRIEF_CHANNEL_ID = "shruti_debrief_channel"

        const val ACTION_START_LISTENING = "org.seven_cgpalabs.shruti.action.START_LISTENING"
        const val ACTION_STOP_LISTENING = "org.seven_cgpalabs.shruti.action.STOP_LISTENING"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"

        fun startListening(context: Context, callerNumber: String = "Unknown") {
            val intent = Intent(context, ShrutiCallListenerService::class.java).apply {
                action = ACTION_START_LISTENING
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
            }
            context.startForegroundService(intent)
        }

        fun stopListening(context: Context) {
            val intent = Intent(context, ShrutiCallListenerService::class.java).apply {
                action = ACTION_STOP_LISTENING
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var listeningJob: Job? = null

    private lateinit var audioEngine: ShrutiAudioEngine
    private lateinit var vaultManager: ShrutiVaultManager

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val sessionTrajectory = mutableListOf<FloatArray>()
    private var sessionStartTime = 0L
    private var currentCallerNumber = "Unknown"
    private var currentSessionId = ""

    override fun onCreate() {
        super.onCreate()
        audioEngine = ShrutiAudioEngine()
        audioEngine.initialize()
        vaultManager = ShrutiVaultManager(applicationContext)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LISTENING -> {
                currentCallerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: "Unknown"
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
                startPassiveRecording()
            }
            ACTION_STOP_LISTENING -> {
                stopPassiveRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startPassiveRecording() {
        if (isRecording) return
        isRecording = true
        sessionStartTime = System.currentTimeMillis()
        currentSessionId = UUID.randomUUID().toString()
        sessionTrajectory.clear()

        val sampleRate = ShrutiAudioEngine.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
            sampleRate // 1 second buffer
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord?.startRecording()
        } catch (e: SecurityException) {
            Log.e(TAG, "Audio recording permission denied: ${e.message}")
            isRecording = false
            return
        }

        listeningJob = serviceScope.launch {
            // Read 500ms chunks (8,000 samples)
            val chunkBuffer = ShortArray(8000)
            var chunkAccumulator = 0

            while (isActive && isRecording) {
                val read = audioRecord?.read(chunkBuffer, 0, chunkBuffer.size) ?: 0
                if (read > 0) {
                    audioEngine.pushAudioFrame(chunkBuffer, read)
                    chunkAccumulator += read

                    // Every 500 ms, run Vulkan GPU vectorizer
                    if (chunkAccumulator >= 8000) {
                        val vectors = audioEngine.extractDualVectors(chunkBuffer)
                        if (vectors != null) {
                            // Expand to 512-d intent representation and append to session trajectory
                            val intentVec = FloatArray(ShrutiAudioEngine.INTENT_DIM)
                            System.arraycopy(vectors.first, 0, intentVec, 0, minOf(vectors.first.size, ShrutiAudioEngine.INTENT_DIM))
                            sessionTrajectory.add(intentVec)
                        }
                        chunkAccumulator = 0
                    }
                }
            }
        }
        Log.i(TAG, "Passive in-call listener active for session $currentSessionId.")
    }

    private fun stopPassiveRecording() {
        if (!isRecording) return
        isRecording = false
        listeningJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        val durationSec = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()

        // Zero-Persistence Invariant: Store only encrypted mathematical vector trajectory
        if (sessionTrajectory.isNotEmpty()) {
            val callType = classifyCallType(currentCallerNumber)
            vaultManager.storeCallSession(
                sessionId = currentSessionId,
                timestamp = sessionStartTime,
                callType = callType,
                callerLabel = currentCallerNumber,
                actionType = if (callType == "delivery") "gate_drop" else "call_debrief",
                durationSeconds = durationSec,
                trajectoryMatrix = sessionTrajectory
            )
            // Trigger Post-Call Debrief Notification
            postDebriefNotification(currentSessionId, callType, currentCallerNumber)
        }

        // Scrub volatile RAM
        audioEngine.wipeMemory()
        Log.i(TAG, "Passive listening concluded. Memory scrubbed for session $currentSessionId.")
    }

    private fun classifyCallType(caller: String): String {
        return if (caller.contains("delivery", ignoreCase = true) ||
            caller.contains("swiggy", ignoreCase = true) ||
            caller.contains("zomato", ignoreCase = true) ||
            caller.contains("amazon", ignoreCase = true)) {
            "delivery"
        } else if (caller.contains("bank", ignoreCase = true) ||
            caller.contains("loan", ignoreCase = true)) {
            "telemarketing"
        } else {
            "personal"
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val listenerChannel = NotificationChannel(
            CHANNEL_ID,
            "S.H.R.U.T.I. Passive Listener",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active while passively vectorizing telephone call"
        }

        val debriefChannel = NotificationChannel(
            DEBRIEF_CHANNEL_ID,
            "S.H.R.U.T.I. Spoken Debriefs",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Post-call voice debrief notifications"
        }

        manager.createNotificationChannel(listenerChannel)
        manager.createNotificationChannel(debriefChannel)
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, ShrutiMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("S.H.R.U.T.I. Active")
            .setContentText("Passively vectorizing call • Zero transcripts • Vulkan GPU")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun postDebriefNotification(sessionId: String, callType: String, caller: String) {
        val openIntent = Intent(this, ShrutiMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PLAY_DEBRIEF_SESSION_ID", sessionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            sessionId.hashCode(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (callType == "delivery") "Package Delivery Debrief Ready" else "Call Debrief Ready"
        val subtitle = "1-Tap to listen to spoken debrief • $caller"

        val notification = NotificationCompat.Builder(this, DEBRIEF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Play Spoken Debrief", pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(DEBRIEF_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopPassiveRecording()
        audioEngine.teardown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
