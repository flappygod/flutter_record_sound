package com.flappy.flutter_record_sound

import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import android.media.MediaRecorder
import kotlinx.coroutines.launch
import android.app.Activity
import kotlin.math.log10
import android.os.Build
import android.util.Log

/**
 * A class for managing audio recording using MediaRecorder.
 * Supports starting, stopping, pausing, resuming, and retrieving amplitude information.
 */
internal class RecorderMedia(private val activity: Activity) {

    // Flags to track recording state
    private var isRecording = false
    private var isPaused = false

    // MediaRecorder instance for recording audio
    private var mediaRecorder: MediaRecorder? = null

    // File path for the recorded audio
    private var path: String? = null

    // Maximum amplitude recorded
    private var maxAmplitude = -160.0

    /**
     * Starts recording audio with the specified parameters.
     *
     * @param path The file path where the recording will be saved.
     * @param encoder The audio encoder type (e.g., AAC, AMR).
     * @param bitRate The bit rate for the recording.
     * @param samplingRate The sampling rate for the recording.
     * @param result The result callback to notify Flutter about the recording status.
     */
    fun start(
        path: String,
        encoder: Int,
        bitRate: Int,
        samplingRate: Double,
        result: MethodChannel.Result
    ) {
        // Stop any ongoing recording before starting a new one
        stopRecording()

        // Save the file path
        this.path = path

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize MediaRecorder
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(activity.baseContext)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                // Configure MediaRecorder settings
                mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
                mediaRecorder!!.setAudioEncodingBitRate(bitRate)
                mediaRecorder!!.setAudioSamplingRate(samplingRate.toInt())
                mediaRecorder!!.setOutputFormat(getOutputFormat(encoder))
                mediaRecorder!!.setAudioEncoder(getEncoder(encoder))
                mediaRecorder!!.setOutputFile(path)

                // Prepare and start recording
                mediaRecorder!!.prepare()
                mediaRecorder!!.start()
                isRecording = true
                isPaused = false

                // Notify Flutter that recording has started
                CoroutineScope(Dispatchers.Main).launch {
                    result.success(null)
                }
                Log.d(LOG_TAG, "Recording started successfully.")
            } catch (e: Exception) {
                // Handle errors during recording setup
                mediaRecorder?.release()
                mediaRecorder = null
                CoroutineScope(Dispatchers.Main).launch {
                    result.error("-1", "Start recording failure", e.message)
                }
                Log.e(LOG_TAG, "Failed to start recording: ${e.message}")
            }
        }
    }

    /**
     * Stops the recording and releases resources.
     */
    fun stop() {
        stopRecording()
    }

    /**
     * Pauses the recording (requires Android N or higher).
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun pause() {
        pauseRecording()
    }

    /**
     * Resumes the recording (requires Android N or higher).
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun resume() {
        resumeRecording()
    }

    /**
     * Checks if the recorder is currently recording.
     */
    fun isRecording(): Boolean {
        return isRecording
    }

    /**
     * Checks if the recorder is currently paused.
     */
    fun isPaused(): Boolean {
        return isPaused
    }

    /**
     * Retrieves the current and maximum amplitude in decibels.
     */
    fun getAmplitude(): Map<String, Any> {
        val amp: MutableMap<String, Any> = HashMap()
        var current = -160.0
        if (isRecording) {
            val maxAmp = mediaRecorder?.maxAmplitude ?: 0
            if (maxAmp > 0) {
                current = 20 * log10(maxAmp / 32768.0)
                if (current > maxAmplitude) {
                    maxAmplitude = current
                }
            }
        }
        amp["current"] = current
        amp["max"] = maxAmplitude
        return amp
    }

    /**
     * Stops the recording and releases resources.
     */
    private fun stopRecording() {
        if (mediaRecorder != null) {
            try {
                if (isRecording || isPaused) {
                    Log.d(LOG_TAG, "Stopping recording.")
                    mediaRecorder!!.stop()
                }
            } catch (ex: IllegalStateException) {
                Log.w(LOG_TAG, "Attempted to stop recording in an invalid state: ${ex.message}")
            } finally {
                mediaRecorder!!.reset()
                mediaRecorder!!.release()
                mediaRecorder = null
            }
        }
        isRecording = false
        isPaused = false
        maxAmplitude = -160.0
    }

    /**
     * Pauses the recording (requires Android N or higher).
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun pauseRecording() {
        if (mediaRecorder != null) {
            try {
                if (isRecording) {
                    Log.d(LOG_TAG, "Pausing recording.")
                    mediaRecorder!!.pause()
                    isPaused = true
                }
            } catch (ex: IllegalStateException) {
                Log.w(LOG_TAG, "Failed to pause recording: ${ex.message}")
            }
        }
    }

    /**
     * Resumes the recording (requires Android N or higher).
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun resumeRecording() {
        if (mediaRecorder != null) {
            try {
                if (isPaused) {
                    Log.d(LOG_TAG, "Resuming recording.")
                    mediaRecorder!!.resume()
                    isPaused = false
                }
            } catch (ex: IllegalStateException) {
                Log.w(LOG_TAG, "Failed to resume recording: ${ex.message}")
            }
        }
    }

    /**
     * Determines the output format based on the encoder type.
     */
    private fun getOutputFormat(encoder: Int): Int {
        return if (encoder == 3 || encoder == 4) {
            MediaRecorder.OutputFormat.THREE_GPP
        } else MediaRecorder.OutputFormat.MPEG_4
    }

    /**
     * Determines the audio encoder based on the encoder type.
     */
    private fun getEncoder(encoder: Int): Int {
        return when (encoder) {
            1 -> MediaRecorder.AudioEncoder.AAC_ELD
            2 -> MediaRecorder.AudioEncoder.HE_AAC
            3 -> MediaRecorder.AudioEncoder.AMR_NB
            4 -> MediaRecorder.AudioEncoder.AMR_WB
            5 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return MediaRecorder.AudioEncoder.OPUS
                } else {
                    Log.d(
                        LOG_TAG,
                        "OPUS codec is available starting from API 29. Falling back to AAC."
                    )
                }
                MediaRecorder.AudioEncoder.AAC
            }
            else -> MediaRecorder.AudioEncoder.AAC
        }
    }

    companion object {
        // Log tag for debugging
        private const val LOG_TAG = "CommonRecorder"
    }
}