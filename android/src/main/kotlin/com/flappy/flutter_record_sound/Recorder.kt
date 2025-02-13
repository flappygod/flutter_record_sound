package com.flappy.flutter_record_sound

import android.app.Activity
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import io.flutter.plugin.common.MethodChannel
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception
import java.lang.IllegalStateException
import kotlin.math.log10

internal class Recorder(private val activity: Activity) {
    private var isRecording = false
    private var isPaused = false
    private var recorder: MediaRecorder? = null
    private var wavRecorder: WavRecorder? = null
    private var path: String? = null
    private var maxAmplitude = -160.0

    // 使用协程优化start方法
    fun start(
        path: String,
        encoder: Int,
        bitRate: Int,
        samplingRate: Double,
        result: MethodChannel.Result
    ) {
        stopRecording()
        Log.d(LOG_TAG, "Start recording")
        this.path = path

        if (encoder == 6) {
            // 使用 WavRecorder 录制 WAV 格式
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    wavRecorder = WavRecorder(path)
                    wavRecorder?.startRecording()
                    isRecording = true
                    isPaused = false

                    // 在主线程中返回结果
                    CoroutineScope(Dispatchers.Main).launch {
                        result.success(null)
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "WAV recording failed: ${e.message}")
                    wavRecorder = null

                    // 在主线程中返回错误
                    CoroutineScope(Dispatchers.Main).launch {
                        result.error("-1", "Start WAV recording failure", e.message)
                    }
                }
            }
        } else {
            // 使用 MediaRecorder 录制其他格式
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(activity.baseContext)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                    recorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
                    recorder!!.setAudioEncodingBitRate(bitRate)
                    recorder!!.setAudioSamplingRate(samplingRate.toInt())
                    recorder!!.setOutputFormat(getOutputFormat(encoder))
                    recorder!!.setAudioEncoder(getEncoder(encoder))
                    recorder!!.setOutputFile(path)

                    recorder!!.prepare()
                    recorder!!.start()
                    isRecording = true
                    isPaused = false

                    // 在主线程中返回结果
                    CoroutineScope(Dispatchers.Main).launch {
                        result.success(null)
                    }
                } catch (e: Exception) {
                    recorder?.release()
                    recorder = null

                    // 在主线程中返回错误
                    CoroutineScope(Dispatchers.Main).launch {
                        result.error("-1", "Start recording failure", e.message)
                    }
                }
            }
        }
    }

    fun stop(result: MethodChannel.Result) {
        stopRecording()
        result.success(path)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun pause(result: MethodChannel.Result) {
        if (wavRecorder != null) {
            // WAV 格式支持暂停
            wavRecorder?.pauseRecording()
            isPaused = true
            result.success(null)
        } else {
            pauseRecording()
            result.success(null)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun resume(result: MethodChannel.Result) {
        if (wavRecorder != null) {
            // WAV 格式支持恢复
            wavRecorder?.resumeRecording()
            isPaused = false
            result.success(null)
        } else {
            resumeRecording()
            result.success(null)
        }
    }

    fun isRecording(result: MethodChannel.Result) {
        result.success(isRecording)
    }

    fun isPaused(result: MethodChannel.Result) {
        result.success(isPaused)
    }

    fun getAmplitude(result: MethodChannel.Result) {
        if (wavRecorder != null) {
            // 获取 WAV 格式的振幅
            val amplitude = wavRecorder?.getAmplitude() ?: -160.0
            result.success(mapOf("current" to amplitude, "max" to maxAmplitude))
        } else {
            val amp: MutableMap<String, Any> = HashMap()
            var current = -160.0
            if (isRecording) {
                current = 20 * log10(recorder!!.maxAmplitude / 32768.0)
                if (current > maxAmplitude) {
                    maxAmplitude = current
                }
            }
            amp["current"] = current
            amp["max"] = maxAmplitude
            result.success(amp)
        }
    }

    fun close() {
        stopRecording()
    }

    private fun stopRecording() {
        if (wavRecorder != null) {
            wavRecorder?.stopRecording()
            wavRecorder = null
        } else if (recorder != null) {
            try {
                if (isRecording || isPaused) {
                    Log.d(LOG_TAG, "Stop recording")
                    recorder!!.stop()
                }
            } catch (ex: IllegalStateException) {
                // Mute this exception since 'isRecording' can't be 100% sure
            } finally {
                recorder!!.reset()
                recorder!!.release()
                recorder = null
            }
        }
        isRecording = false
        isPaused = false
        maxAmplitude = -160.0
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun pauseRecording() {
        if (recorder != null) {
            try {
                if (isRecording) {
                    Log.d(LOG_TAG, "Pause recording")
                    recorder!!.pause()
                    isPaused = true
                }
            } catch (ex: IllegalStateException) {
                Log.d(
                    LOG_TAG, """
     Did you call pause() before before start() or after stop()?
     ${ex.message}
     """.trimIndent()
                )
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun resumeRecording() {
        if (recorder != null) {
            try {
                if (isPaused) {
                    Log.d(LOG_TAG, "Resume recording")
                    recorder!!.resume()
                    isPaused = false
                }
            } catch (ex: IllegalStateException) {
                Log.d(
                    LOG_TAG, """
     Did you call resume() before before start() or after stop()?
     ${ex.message}
     """.trimIndent()
                )
            }
        }
    }

    private fun getOutputFormat(encoder: Int): Int {
        return if (encoder == 3 || encoder == 4) {
            MediaRecorder.OutputFormat.THREE_GPP
        } else MediaRecorder.OutputFormat.MPEG_4
    }

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
                        "OPUS codec is available starting from API 29.\nFalling back to AAC"
                    )
                }
                MediaRecorder.AudioEncoder.AAC
            }

            else -> MediaRecorder.AudioEncoder.AAC
        }
    }

    companion object {
        private const val LOG_TAG = "Record"
    }
}