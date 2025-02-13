package com.flappy.flutter_record_sound

import io.flutter.plugin.common.MethodChannel
import androidx.annotation.RequiresApi
import android.app.Activity
import android.os.Build

internal class Recorder(private val activity: Activity) {
    private var mediaRecorder: RecorderMedia? = null
    private var wavRecorder: RecorderWav? = null
    private var path: String? = null
    private var maxAmplitude = -160.0

    //start recording with specified parameters
    fun start(
        path: String,
        encoder: Int,
        bitRate: Int,
        samplingRate: Double,
        result: MethodChannel.Result
    ) {
        stopRecording()
        this.path = path
        if (encoder == 6) {
            wavRecorder = RecorderWav(activity)
            wavRecorder?.start(
                path,
                samplingRate.toInt(),
                result,
            )
        } else {
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

    fun stop(result: MethodChannel.Result) {
        stopRecording()
        result.success(path)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun pause(result: MethodChannel.Result) {
        pauseRecording()
        result.success(null)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun resume(result: MethodChannel.Result) {
        resumeRecording()
        result.success(null)
    }

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

    fun getAmplitude(result: MethodChannel.Result) {
        if (wavRecorder != null) {
            val amplitude = wavRecorder?.getAmplitude() ?: -160.0
            result.success(mapOf("current" to amplitude, "max" to maxAmplitude))
        }
        if (mediaRecorder != null) {
            result.success(mediaRecorder?.getAmplitude())
        }
    }

    ///close recording
    fun close() {
        stopRecording()
    }

    ///stop recording
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

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun pauseRecording() {
        wavRecorder?.pause()
        mediaRecorder?.pause()
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun resumeRecording() {
        wavRecorder?.resume()
        mediaRecorder?.resume()
    }

}