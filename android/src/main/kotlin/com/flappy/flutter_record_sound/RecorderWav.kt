package com.flappy.flutter_record_sound

import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.media.MediaRecorder
import kotlinx.coroutines.launch
import android.media.AudioFormat
import android.media.AudioRecord
import java.io.FileOutputStream
import android.app.Activity
import java.io.IOException
import kotlin.math.log10
import android.util.Log
import kotlin.math.max
import java.io.File

/**
 * A class for recording audio in WAV format.
 * This class uses `AudioRecord` to capture raw PCM data and converts it to WAV format.
 */
class RecorderWav(private val activity: Activity) {

    // AudioRecord instance for capturing audio
    private var audioRecord: AudioRecord? = null

    // Flags to track recording state
    private var isRecording = false
    private var isPaused = false

    // File path for the recorded audio
    private var path: String? = null

    // Stores the current and maximum amplitude in decibels
    private var maxAmplitude = -160.0
    private var currentAmplitudeDb: Double = -160.0

    // Buffer size and sampling rate for recording
    private var bufferSize: Int = 0
    private var samplingRate: Int = 0

    /**
     * Checks if recording is currently in progress.
     */
    fun isRecording(): Boolean {
        return isRecording
    }

    /**
     * Checks if recording is currently paused.
     */
    fun isPaused(): Boolean {
        return isPaused
    }

    /**
     * Starts recording audio with the specified sampling rate.
     *
     * @param path The file path where the recording will be saved.
     * @param samplingRate The sampling rate for the recording.
     * @param result The result callback to notify Flutter about the recording status.
     */
    fun start(
        path: String,
        samplingRate: Int,
        result: MethodChannel.Result
    ) {
        stop() // Stop any previous recording
        this.path = path
        this.samplingRate = samplingRate

        // Calculate the minimum buffer size for the given sampling rate
        this.bufferSize = AudioRecord.getMinBufferSize(
            samplingRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize the AudioRecord instance
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    samplingRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                // Start recording
                audioRecord?.startRecording()
                isRecording = true
                isPaused = false

                // Notify Flutter that recording has started
                CoroutineScope(Dispatchers.Main).launch {
                    result.success(null)
                }

                // Write audio data to a PCM file
                writeAudioDataToFile()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error starting recording: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    result.error("-1", "Error starting recording", e.message)
                }
            }
        }
    }

    /**
     * Pauses the recording.
     */
    fun pause() {
        if (!isRecording) {
            Log.w(LOG_TAG, "Cannot pause. Recording is not in progress.")
            return
        }
        isPaused = true
    }

    /**
     * Resumes the recording.
     */
    fun resume() {
        if (!isRecording) {
            Log.w(LOG_TAG, "Cannot resume. Recording is not in progress.")
            return
        }
        isPaused = false
    }

    /**
     * Stops the recording and converts the PCM file to WAV format.
     */
    fun stop() {
        if (!isRecording) {
            return
        }
        isRecording = false

        // Stop and release the AudioRecord instance
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        try {
            // Convert the recorded PCM file to WAV format
            convertPcmToWav()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error stopping recording: ${e.message}")
        }
    }

    /**
     * Retrieves the current and maximum amplitude in decibels.
     */
    fun getAmplitude(): Map<String, Any> {
        synchronized(this) {
            val amplitude = currentAmplitudeDb
            return mapOf("current" to amplitude, "max" to maxAmplitude)
        }
    }

    /**
     * Writes audio data to a PCM file while recording.
     */
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
                            var localMaxAmplitude = 0.0
                            for (i in 0 until read) {
                                val amplitude = buffer[i].toDouble()
                                localMaxAmplitude = max(localMaxAmplitude, kotlin.math.abs(amplitude))
                            }

                            // Calculate and cache the decibel value
                            currentAmplitudeDb = if (localMaxAmplitude > 0) {
                                20 * log10(localMaxAmplitude / 32768.0)
                            } else {
                                -160.0
                            }

                            // Update the global maxAmplitude
                            synchronized(this) {
                                if (currentAmplitudeDb > maxAmplitude) {
                                    maxAmplitude = currentAmplitudeDb
                                }
                            }

                            // Write the buffer to the PCM file
                            outputStream.write(buffer.toByteArray(), 0, read * 2)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Error writing audio data to file: ${e.message}")
        }
    }

    /**
     * Converts a ShortArray to a ByteArray.
     */
    private fun ShortArray.toByteArray(): ByteArray {
        val byteArray = ByteArray(this.size * 2)
        for (i in this.indices) {
            byteArray[i * 2] = (this[i].toInt() and 0x00FF).toByte()
            byteArray[i * 2 + 1] = (this[i].toInt() shr 8).toByte()
        }
        return byteArray
    }

    /**
     * Converts the recorded PCM file to WAV format.
     */
    private fun convertPcmToWav() {
        val pcmFile = File("$path.pcm")
        val wavFile = path?.let { File(it) }
        try {
            // Read PCM data
            val pcmData = pcmFile.readBytes()

            // Add WAV file header
            val wavData = createWavHeader(pcmData.size, samplingRate) + pcmData

            // Write the WAV data to the file
            wavFile?.writeBytes(wavData)

            // Delete the temporary PCM file
            pcmFile.delete()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Error converting PCM to WAV: ${e.message}")
        }
    }

    /**
     * Creates a WAV file header for the given PCM data size and sampling rate.
     */
    private fun createWavHeader(dataSize: Int, sampleRate: Int): ByteArray {
        val totalSize = 36 + dataSize
        val byteRate = sampleRate * 2 * 1 // Byte rate = Sample rate * Channels * Bits per sample / 8
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
            (sampleRate and 0xff).toByte(),
            ((sampleRate shr 8) and 0xff).toByte(),
            ((sampleRate shr 16) and 0xff).toByte(),
            ((sampleRate shr 24) and 0xff).toByte(),
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
        // Log tag for debugging
        private const val LOG_TAG = "WavRecorder"
    }
}