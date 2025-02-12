package com.flappy.flutter_record_sound

import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodCall
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.content.Context
import android.app.Activity
import java.io.IOException
import android.Manifest
import android.os.Build
import java.io.File

/** FlutterRecordSoundPlugin */
class FlutterRecordSoundPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware, RequestPermissionsResultListener {

    private lateinit var channel: MethodChannel
    private var context: Context? = null
    private var activity: Activity? = null
    private var activityPluginBinding: ActivityPluginBinding? = null
    private var recorder: Recorder? = null
    private var pendingPermResult: MethodChannel.Result? = null

    companion object {
        private const val RECORD_AUDIO_REQUEST_CODE = 1001
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        this.context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_record_sound")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        context = null
        activity = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        addBinding(binding)
        this.recorder = Recorder(binding.activity)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        addBinding(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        removeBinding()
    }

    override fun onDetachedFromActivity() {
        close()
        removeBinding()
    }

    private fun addBinding(binding: ActivityPluginBinding) {
        activityPluginBinding?.removeRequestPermissionsResultListener(this)
        activity = binding.activity
        activityPluginBinding = binding
        activityPluginBinding!!.addRequestPermissionsResultListener(this)
    }

    private fun removeBinding() {
        activityPluginBinding?.removeRequestPermissionsResultListener(this)
        activity = null
        activityPluginBinding = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "start" -> {
                var path = call.argument<String>("path")
                if (path == null) {
                    val outputDir = activity?.cacheDir
                    if (outputDir == null) {
                        result.error("-3", "Failed to access cache directory", null)
                        return
                    }
                    try {
                        val outputFile = File.createTempFile("audio", ".m4a", outputDir)
                        path = outputFile.path
                    } catch (e: IOException) {
                        e.printStackTrace()
                        result.error("-3", "Failed to create temporary file", e.message)
                        return
                    }
                }

                val encoder = call.argument<Int>("encoder")
                val bitRate = call.argument<Int>("bitRate")
                val samplingRate = call.argument<Double>("samplingRate")

                if (encoder == null || bitRate == null || samplingRate == null) {
                    result.error("-4", "Invalid arguments for start method", null)
                    return
                }

                if (path != null) {
                    recorder?.start(path, encoder, bitRate, samplingRate, result)
                }
            }

            "stop" -> recorder?.stop(result) ?: result.error("-5", "Recorder is not initialized", null)

            "pause" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    recorder?.pause(result) ?: result.error("-5", "Recorder is not initialized", null)
                } else {
                    result.error("-6", "Pause is not supported on this Android version", null)
                }
            }

            "resume" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    recorder?.resume(result) ?: result.error("-5", "Recorder is not initialized", null)
                } else {
                    result.error("-6", "Resume is not supported on this Android version", null)
                }
            }

            "isPaused" -> recorder?.isPaused(result) ?: result.error("-5", "Recorder is not initialized", null)

            "isRecording" -> recorder?.isRecording(result) ?: result.error("-5", "Recorder is not initialized", null)

            "hasPermission" -> hasPermission(result)

            "getAmplitude" -> recorder?.getAmplitude(result) ?: result.error("-5", "Recorder is not initialized", null)

            "dispose" -> {
                close()
                result.success(null)
            }

            else -> result.notImplemented()
        }
    }

    private fun close() {
        recorder?.close()
        recorder = null
        pendingPermResult = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (pendingPermResult != null) {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pendingPermResult!!.success(true)
                } else {
                    pendingPermResult!!.error("-2", "Permission denied", null)
                }
                pendingPermResult = null
                return true
            }
        }
        return false
    }

    private fun hasPermission(result: MethodChannel.Result) {
        if (!isPermissionGranted) {
            pendingPermResult = result
            askForPermission()
        } else {
            result.success(true)
        }
    }

    private val isPermissionGranted: Boolean
        get() {
            val activity = this.activity ?: return false
            val result = ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            return result == PackageManager.PERMISSION_GRANTED
        }

    private fun askForPermission() {
        val activity = this.activity
        if (activity == null) {
            pendingPermResult?.error("-1", "Activity is null", null)
            pendingPermResult = null
            return
        }
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_REQUEST_CODE
        )
    }
}