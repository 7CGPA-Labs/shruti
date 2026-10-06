package org.seven_cgpalabs.shruti.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.sqrt

/**
 * Data model for a stored call session debrief in the encrypted vault.
 */
data class CallSessionSummary(
    val sessionId: String,
    val timestamp: Long,
    val callType: String,
    val vectorCount: Int,
    val durationSeconds: Int,
    val callerLabel: String,
    val actionType: String,
    val similarityScore: Float = 0.0f
)

/**
 * Encrypted Vector Vault Manager for S.H.R.U.T.I.
 * Enforces Zero-Persistence: Raw audio frames and written text transcripts are NEVER stored.
 * Only hardware-encrypted AES-256-GCM mathematical vector matrices (M_session ∈ R^(Kx512))
 * persist in the local database.
 */
class ShrutiVaultManager(context: Context) {

    companion object {
        private const val TAG = "ShrutiVaultManager"
        private const val KEY_ALIAS = "ShrutiVaultMasterKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        private const val DB_NAME = "shruti_vault.db"
        private const val DB_VERSION = 1
        private const val TABLE_SESSIONS = "vector_sessions"
    }

    private val dbHelper = VaultDbHelper(context)

    init {
        getOrCreateMasterKey()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        val key = keyGenerator.generateKey()
        Log.i(TAG, "Initialized new 256-bit AES-GCM Master Key in Android Keystore.")
        return key
    }

    /**
     * Encrypts trajectory matrix (K x 512 floats) using AES-256-GCM.
     * Layout: 12-byte IV + Ciphertext (with 16-byte GCM Tag).
     */
    fun encryptTrajectoryMatrix(matrix: List<FloatArray>): ByteArray {
        val vectorCount = matrix.size
        val totalFloats = vectorCount * ShrutiAudioEngine.INTENT_DIM
        val byteBuffer = ByteBuffer.allocate(totalFloats * 4).order(ByteOrder.LITTLE_ENDIAN)

        for (vec in matrix) {
            for (f in vec) {
                byteBuffer.putFloat(f)
            }
        }
        val rawBytes = byteBuffer.array()

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val secretKey = getOrCreateMasterKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv // 12-byte IV
        val ciphertext = cipher.doFinal(rawBytes)

        val payload = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(ciphertext, 0, payload, iv.size, ciphertext.size)
        return payload
    }

    /**
     * Decrypts encrypted BLOB back into trajectory matrix (K x 512 floats).
     */
    fun decryptTrajectoryMatrix(encryptedPayload: ByteArray, vectorCount: Int): List<FloatArray> {
        if (encryptedPayload.size < GCM_IV_LENGTH + 16) return emptyList()

        val iv = ByteArray(GCM_IV_LENGTH)
        val ciphertext = ByteArray(encryptedPayload.size - GCM_IV_LENGTH)
        System.arraycopy(encryptedPayload, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(encryptedPayload, GCM_IV_LENGTH, ciphertext, 0, ciphertext.size)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)
        val rawBytes = cipher.doFinal(ciphertext)

        val byteBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
        val result = mutableListOf<FloatArray>()

        for (i in 0 until vectorCount) {
            val vec = FloatArray(ShrutiAudioEngine.INTENT_DIM)
            for (j in 0 until ShrutiAudioEngine.INTENT_DIM) {
                if (byteBuffer.hasRemaining()) {
                    vec[j] = byteBuffer.float
                }
            }
            result.add(vec)
        }
        return result
    }

    /**
     * Stores a completed call session in the vault.
     */
    fun storeCallSession(
        sessionId: String,
        timestamp: Long,
        callType: String,
        callerLabel: String,
        actionType: String,
        durationSeconds: Int,
        trajectoryMatrix: List<FloatArray>
    ) {
        val encryptedBlob = encryptTrajectoryMatrix(trajectoryMatrix)
        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("timestamp", timestamp)
            put("call_type", callType)
            put("caller_label", callerLabel)
            put("action_type", actionType)
            put("duration_seconds", durationSeconds)
            put("vector_count", trajectoryMatrix.size)
            put("trajectory_blob", encryptedBlob)
        }

        db.insertWithOnConflict(TABLE_SESSIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Log.i(TAG, "Stored encrypted session $sessionId with ${trajectoryMatrix.size} vectors ($durationSeconds s).")
    }

    /**
     * Retrieves all session summaries (metadata only, no decrypted vectors).
     */
    fun getAllSessions(): List<CallSessionSummary> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_SESSIONS,
            arrayOf("session_id", "timestamp", "call_type", "caller_label", "action_type", "duration_seconds", "vector_count"),
            null, null, null, null, "timestamp DESC"
        )

        val list = mutableListOf<CallSessionSummary>()
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    CallSessionSummary(
                        sessionId = c.getString(c.getColumnIndexOrThrow("session_id")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        callType = c.getString(c.getColumnIndexOrThrow("call_type")),
                        callerLabel = c.getString(c.getColumnIndexOrThrow("caller_label")),
                        actionType = c.getString(c.getColumnIndexOrThrow("action_type")),
                        durationSeconds = c.getInt(c.getColumnIndexOrThrow("duration_seconds")),
                        vectorCount = c.getInt(c.getColumnIndexOrThrow("vector_count"))
                    )
                )
            }
        }
        return list
    }

    /**
     * Loads and decrypts the trajectory matrix for debrief playback.
     */
    fun loadCallTrajectory(sessionId: String): List<FloatArray>? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_SESSIONS,
            arrayOf("trajectory_blob", "vector_count"),
            "session_id = ?",
            arrayOf(sessionId),
            null, null, null
        )

        cursor.use { c ->
            if (c.moveToFirst()) {
                val blob = c.getBlob(c.getColumnIndexOrThrow("trajectory_blob"))
                val count = c.getInt(c.getColumnIndexOrThrow("vector_count"))
                return decryptTrajectoryMatrix(blob, count)
            }
        }
        return null
    }

    /**
     * Cosine similarity matching: queries vault using voice search vector.
     */
    fun searchByVector(queryVec: FloatArray, topK: Int = 3): List<CallSessionSummary> {
        val allSessions = getAllSessions()
        val scoredList = mutableListOf<CallSessionSummary>()

        for (session in allSessions) {
            val trajectory = loadCallTrajectory(session.sessionId) ?: continue
            // Mean pooled intent vector for session
            val pooledVec = FloatArray(ShrutiAudioEngine.INTENT_DIM)
            for (vec in trajectory) {
                for (i in 0 until ShrutiAudioEngine.INTENT_DIM) {
                    pooledVec[i] += vec[i]
                }
            }
            if (trajectory.isNotEmpty()) {
                for (i in 0 until ShrutiAudioEngine.INTENT_DIM) {
                    pooledVec[i] /= trajectory.size.toFloat()
                }
            }

            val sim = computeCosineSimilarity(queryVec, pooledVec)
            scoredList.add(session.copy(similarityScore = sim))
        }

        return scoredList.sortedByDescending { it.similarityScore }.take(topK)
    }

    private fun computeCosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-6f) dot / denom else 0.0f
    }

    private class VaultDbHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_SESSIONS (
                    session_id TEXT PRIMARY KEY,
                    timestamp INTEGER NOT NULL,
                    call_type TEXT NOT NULL,
                    caller_label TEXT NOT NULL,
                    actionType TEXT NOT NULL,
                    duration_seconds INTEGER NOT NULL,
                    vector_count INTEGER NOT NULL,
                    trajectory_blob BLOB NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
            onCreate(db)
        }
    }
}
