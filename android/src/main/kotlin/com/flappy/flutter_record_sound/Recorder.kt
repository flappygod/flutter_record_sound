package com.flappy.flutter_record_sound

import io.flutter.plugin.common.MethodChannel
import androidx.annotation.RequiresApi
import android.app.Activity
import android.os.Build

/**
 * Recorder class handles audio recording functionality.
 * It supports both WAV and Media-based recording depending on the encoder type.
 */
internal class Recorder(private val activity: Activity) {

    // Media-based recorder (e.g., AAC, AMR, etc.)
    private var mediaRecorder: RecorderMedia? = null

    // WAV-based recorder
    private var wavRecorder: RecorderWav? = null

    // Path to the recorded file
    private var path: String? = null

    /**
     * Starts recording with the specified parameters.
     *
     * @param path The file path where the recording will be saved.
     * @param encoder The audio encoder type (e.g., AAC, AMR, WAV).
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
        this.path = path

        // If the encoder is 6 (WAV format), use the WAV recorder
        if (encoder == 6) {
            wavRecorder = RecorderWav(activity)
            wavRecorder?.start(
                path,
                samplingRate.toInt(),
                result,
            )
        } else {
            // Otherwise, use the media-based recorder
            mediaRecorder = RecorderMedia(activity)
            mediaRecorder?.start(
                path,
                encoder,
                bitRate,
                samplingRate,
                result
            )
        }
    }

    /**
     * Stops the recording and returns the file path of the recorded audio.
     *
     * @param result The result callback to notify Flutter about the recording status.
     */
    fun stop(result: MethodChannel.Result) {
        stopRecording()
        result.success(path)
    }

    /**
     * Pauses the recording (requires Android N or higher).
     *
     * @param result The result callback to notify Flutter about the pause status.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun pause(result: MethodChannel.Result) {
        pauseRecording()
        result.success(null)
    }

    /**
     * Resumes the recording (requires Android N or higher).
     *
     * @param result The result callback to notify Flutter about the resume status.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    fun resume(result: MethodChannel.Result) {
        resumeRecording()
        result.success(null)
    }

    /**
     * Checks if the recorder is currently recording.
     *
     * @param result The result callback to notify Flutter about the recording status.
     */
    fun isRecording(result: MethodChannel.Result) {
        if (wavRecorder != null) {
            result.success(wavRecorder?.isRecording() ?: false)
            return
        }
        if (mediaRecorder != null) {
            result.success(mediaRecorder?.isRecording() ?: false)
            return
        }
        result.success(false)
    }

    /**
     * Checks if the recorder is currently paused.
     *
     * @param result The result callback to notify Flutter about the paused status.
     */
    fun isPaused(result: MethodChannel.Result) {
        if (wavRecorder != null) {
            result.success(wavRecorder?.isPaused() ?: false)
            return
        }
        if (mediaRecorder != null) {
            result.success(mediaRecorder?.isPaused() ?: false)
            return
        }
        result.success(false)
    }

    /**
     * Retrieves the current amplitude of the audio being recorded.
     *
     * @param result The result callback to notify Flutter about the amplitude.
     */
    fun getAmplitude(result: MethodChannel.Result) {
        if (wavRecorder != null) {
            result.success(wavRecorder?.getAmplitude())
        }
        if (mediaRecorder != null) {
            result.success(mediaRecorder?.getAmplitude())
        }
    }

    /**
     * Closes the recorder and releases resources.
     */
    fun close() {
        stopRecording()
    }

    /**
     * Stops the recording and cleans up resources.
     */
    private fun stopRecording() {
        if (wavRecorder != null) {
            wavRecorder?.stop()
            wavRecorder = null
        }
        if (mediaRecorder != null) {
            mediaRecorder?.stop()
            mediaRecorder = null
        }
    }

    /**
     * Pauses the recording (requires Android N or higher).
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun pauseRecording() {
        wavRecorder?.pause()
        mediaRecorder?.pause()
    }

    /**
     * Resumes the recording (requires Android N or higher).
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun resumeRecording() {
        wavRecorder?.resume()
        mediaRecorder?.resume()
    }
}