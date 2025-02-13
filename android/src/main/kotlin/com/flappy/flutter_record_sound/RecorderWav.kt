package com.flappy.flutter_record_sound

import android.app.Activity
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.log10
import kotlin.math.max

class RecorderWav(private val activity: Activity) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var path: String? = null
    private var currentAmplitudeDb: Double = -160.0 // Stores the current decibel value

    // Calculate the minimum buffer size
    private val bufferSize: Int = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    // Check if recording is in progress
    fun isRecording(): Boolean {
        return isRecording
    }

    // Check if recording is paused
    fun isPaused(): Boolean {
        return isPaused
    }

    // Start recording
    fun start(
        path: String,
        result: MethodChannel.Result
    ) {
        stop() // Stop any previous recording
        this.path = path
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize AudioRecord
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                // Start recording
                audioRecord?.startRecording()
                isRecording = true
                isPaused = false

                // Return success result
                CoroutineScope(Dispatchers.Main).launch {
                    result.success(null)
                }

                // Write audio data to file
                writeAudioDataToFile()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error starting recording: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    result.error("-1", "Error starting recording", e.message)
                }
            }
        }
    }

    // Pause recording
    fun pause() {
        if (!isRecording) {
            Log.w(LOG_TAG, "Cannot pause. Recording is not in progress.")
            return
        }
        isPaused = true
    }

    // Resume recording
    fun resume() {
        if (!isRecording) {
            Log.w(LOG_TAG, "Cannot resume. Recording is not in progress.")
            return
        }
        isPaused = false
    }

    // Stop recording
    fun stop() {
        if (!isRecording) {
            return
        }
        isRecording = false
        // Stop recording
        audioRecord?.stop()
        // Release resources
        audioRecord?.release()
        audioRecord = null
        try {
            // Convert PCM to WAV
            convertPcmToWav()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error stopping recording: ${e.message}")
        }
    }

    // Get the current amplitude (in decibels)
    fun getAmplitude(): Double {
        synchronized(this) {
            return currentAmplitudeDb
        }
    }

    // Write audio data to file
    private fun writeAudioDataToFile() {
        val pcmFile = File("$path.pcm")
        val buffer = ShortArray(bufferSize / 2)
        try {
            FileOutputStream(pcmFile).use { outputStream ->
                while (isRecording) {
                    if (!isPaused) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            // Calculate the maximum amplitude in the current buffer
                            var maxAmplitude = 0.0
                            for (i in 0 until read) {
                                val amplitude = buffer[i].toDouble()
                                maxAmplitude = max(maxAmplitude, kotlin.math.abs(amplitude))
                            }
                            // Calculate and cache the decibel value
                            currentAmplitudeDb = if (maxAmplitude > 0) {
                                20 * log10(maxAmplitude / 32768.0)
                            } else {
                                -160.0
                            }
                            // Write to file
                            outputStream.write(buffer.toByteArray(), 0, read * 2)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Error writing audio data to file: ${e.message}")
        }
    }

    // Convert ShortArray to ByteArray
    private fun ShortArray.toByteArray(): ByteArray {
        val byteArray = ByteArray(this.size * 2)
        for (i in this.indices) {
            byteArray[i * 2] = (this[i].toInt() and 0x00FF).toByte()
            byteArray[i * 2 + 1] = (this[i].toInt() shr 8).toByte()
        }
        return byteArray
    }

    // Convert PCM to WAV
    private fun convertPcmToWav() {
        val pcmFile = File("$path.pcm")
        val wavFile = path?.let { File(it) }
        try {
            // Read PCM data
            val pcmData = pcmFile.readBytes()
            // Add WAV file header
            val wavData = createWavHeader(pcmData.size) + pcmData
            // Write to WAV file
            wavFile?.writeBytes(wavData)
            // Delete temporary PCM file
            pcmFile.delete()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Error converting PCM to WAV: ${e.message}")
        }
    }

    // Create WAV file header
    private fun createWavHeader(dataSize: Int): ByteArray {
        val totalSize = 36 + dataSize
        val byteRate = SAMPLE_RATE * 2 * 1 // Byte rate = Sample rate * Channels * Bits per sample / 8
        return byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), // RIFF
            (totalSize and 0xff).toByte(),
            ((totalSize shr 8) and 0xff).toByte(),
            ((totalSize shr 16) and 0xff).toByte(),
            ((totalSize shr 24) and 0xff).toByte(),
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(), // WAVE
            'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(), // fmt
            16, 0, 0, 0, // Subchunk size
            1, 0, // Audio format (PCM)
            1, 0, // Number of channels (Mono)
            (SAMPLE_RATE and 0xff).toByte(),
            ((SAMPLE_RATE shr 8) and 0xff).toByte(),
            ((SAMPLE_RATE shr 16) and 0xff).toByte(),
            ((SAMPLE_RATE shr 24) and 0xff).toByte(),
            (byteRate and 0xff).toByte(),
            ((byteRate shr 8) and 0xff).toByte(),
            ((byteRate shr 16) and 0xff).toByte(),
            ((byteRate shr 24) and 0xff).toByte(),
            2, 0, // Block align
            16, 0, // Bits per sample
            'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), // data
            (dataSize and 0xff).toByte(),
            ((dataSize shr 8) and 0xff).toByte(),
            ((dataSize shr 16) and 0xff).toByte(),
            ((dataSize shr 24) and 0xff).toByte()
        )
    }

    companion object {
        // Sample rate
        private const val SAMPLE_RATE = 44100

        // Log tag
        private const val LOG_TAG = "WavRecorder"
    }
}