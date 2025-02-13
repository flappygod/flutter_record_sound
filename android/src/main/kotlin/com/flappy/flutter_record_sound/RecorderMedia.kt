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

internal class RecorderMedia(private val activity: Activity) {
    private var isRecording = false
    private var isPaused = false
    private var mediaRecorder: MediaRecorder? = null
    private var path: String? = null
    private var maxAmplitude = -160.0

    // Start recording with specified parameters
    fun start(
        path: String,
        encoder: Int,
        bitRate: Int,
        samplingRate: Double,
        result: MethodChannel.Result
    ) {

        //stop at first
        stopRecording()

        //save path
        this.path = path

        CoroutineScope(Dispatchers.IO).launch {
            try {
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(activity.baseContext)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
                mediaRecorder!!.setAudioEncodingBitRate(bitRate)
                mediaRecorder!!.setAudioSamplingRate(samplingRate.toInt())
                mediaRecorder!!.setOutputFormat(getOutputFormat(encoder))
                mediaRecorder!!.setAudioEncoder(getEncoder(encoder))
                mediaRecorder!!.setOutputFile(path)
                mediaRecorder!!.prepare()
                mediaRecorder!!.start()
                isRecording = true
                isPaused = false
                CoroutineScope(Dispatchers.Main).launch {
                    result.success(null)
                }
                Log.d(LOG_TAG, "Recording started successfully.")
            } catch (e: Exception) {
                mediaRecorder?.release()
                mediaRecorder = null
                CoroutineScope(Dispatchers.Main).launch {
                    result.error("-1", "Start recording failure", e.message)
                }
                Log.e(LOG_TAG, "Failed to start recording: ${e.message}")
            }
        }
    }

    //Stop recording and return the file path
    fun stop() {
        stopRecording()
    }

    //Pause recording if supported
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun pause() {
        pauseRecording()
    }

    //resume recording if supported
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun resume() {
        resumeRecording()
    }

    //check if currently recording
    fun isRecording(): Boolean {
        return isRecording
    }

    //check if currently paused
    fun isPaused(): Boolean {
        return isPaused
    }

    //get the current and max amplitude
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


    // Stop recording and release resources
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

    //pause recording
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

    //resume recording
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

    //get the output format based on encoder
    private fun getOutputFormat(encoder: Int): Int {
        return if (encoder == 3 || encoder == 4) {
            MediaRecorder.OutputFormat.THREE_GPP
        } else MediaRecorder.OutputFormat.MPEG_4
    }

    //get the audio encoder based on encoder
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

    //common recorder
    companion object {
        private const val LOG_TAG = "CommonRecorder"
    }
}