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

    // Activity plugin binding for managing activity lifecycle
    private var activityPluginBinding: ActivityPluginBinding? = null

    // Pending result for permission requests
    private var pendingPermResult: MethodChannel.Result? = null

    // Method channel for communication between Flutter and native code
    private lateinit var channel: MethodChannel

    // Application context
    private var context: Context? = null

    // Current activity
    private var activity: Activity? = null

    // Recorder instance for managing audio recording
    private var recorder: Recorder? = null

    companion object {
        private const val RECORD_AUDIO_REQUEST_CODE = 1001 // Request code for audio recording permission
    }

    // Called when the plugin is attached to the Flutter engine
    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        this.context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_record_sound")
        channel.setMethodCallHandler(this)
    }

    // Called when the plugin is detached from the Flutter engine
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        context = null
    }

    // Called when the plugin is attached to an activity
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        addBinding(binding)
    }

    // Called when the activity is detached for configuration changes
    override fun onDetachedFromActivityForConfigChanges() {
        removeBinding()
    }

    // Called when the activity is reattached after configuration changes
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        addBinding(binding)
    }

    // Called when the plugin is detached from an activity
    override fun onDetachedFromActivity() {
        removeBinding()
    }

    // Adds the activity binding and initializes the recorder
    private fun addBinding(binding: ActivityPluginBinding) {
        activityPluginBinding?.removeRequestPermissionsResultListener(this)
        activity = binding.activity
        activityPluginBinding = binding
        activityPluginBinding!!.addRequestPermissionsResultListener(this)
        this.recorder = Recorder(binding.activity)
    }

    // Removes the activity binding and cleans up resources
    private fun removeBinding() {
        activityPluginBinding?.removeRequestPermissionsResultListener(this)
        activity = null
        activityPluginBinding = null
        recorder?.close()
        recorder = null
        pendingPermResult = null
    }

    // Handles method calls from Flutter
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "start" -> handleStart(call, result)
            "stop" -> handleStop(result)
            "pause" -> handlePause(result)
            "resume" -> handleResume(result)
            "isPaused" -> handleIsPaused(result)
            "isRecording" -> handleIsRecording(result)
            "hasPermission" -> handleHasPermission(result)
            "getAmplitude" -> handleGetAmplitude(result)
            "dispose" -> handleDispose(result)
            else -> result.notImplemented()
        }
    }

    // Handles the "start" method call to start recording
    private fun handleStart(call: MethodCall, result: MethodChannel.Result) {
        val encoder = call.argument<Int>("encoder")
        val bitRate = call.argument<Int>("bitRate")
        val samplingRate = call.argument<Double>("samplingRate")
        var path = call.argument<String>("path")
        if (encoder == null || bitRate == null || samplingRate == null) {
            result.error("-4", "Invalid arguments for start method", null)
            return
        }
        if (path == null) {
            val outputDir = activity?.cacheDir
            if (outputDir == null) {
                result.error("-3", "Failed to access cache directory", null)
                return
            }
            try {
                val outputFile = File.createTempFile("audio", getFileExtension(encoder), outputDir)
                path = outputFile.path
            } catch (e: IOException) {
                result.error("-3", "Failed to create temporary file", e.message)
                return
            }
        }
        recorder?.start(path!!, encoder, bitRate, samplingRate, result)
    }

    // Handles the "stop" method call to stop recording
    private fun handleStop(result: MethodChannel.Result) {
        recorder?.stop(result) ?: result.error("-5", "Recorder is not initialized", null)
    }

    // Handles the "pause" method call to pause recording
    private fun handlePause(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder?.pause(result) ?: result.error("-5", "Recorder is not initialized", null)
        } else {
            result.error("-6", "Pause is not supported on this Android version", null)
        }
    }

    // Handles the "resume" method call to resume recording
    private fun handleResume(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder?.resume(result) ?: result.error("-5", "Recorder is not initialized", null)
        } else {
            result.error("-6", "Resume is not supported on this Android version", null)
        }
    }

    // Handles the "isPaused" method call to check if recording is paused
    private fun handleIsPaused(result: MethodChannel.Result) {
        recorder?.isPaused(result) ?: result.error("-5", "Recorder is not initialized", null)
    }

    // Handles the "isRecording" method call to check if recording is in progress
    private fun handleIsRecording(result: MethodChannel.Result) {
        recorder?.isRecording(result) ?: result.error("-5", "Recorder is not initialized", null)
    }

    // Handles the "getAmplitude" method call to get the current amplitude
    private fun handleGetAmplitude(result: MethodChannel.Result) {
        recorder?.getAmplitude(result) ?: result.error("-5", "Recorder is not initialized", null)
    }

    // Handles the "dispose" method call to release resources
    private fun handleDispose(result: MethodChannel.Result) {
        recorder?.close() ?: result.error("-5", "Recorder is not initialized", null)
        result.success(null)
    }

    // Handles the "hasPermission" method call to check for audio recording permission
    private fun handleHasPermission(result: MethodChannel.Result) {
        if (!isPermissionGranted) {
            pendingPermResult = result
            askForPermission()
        } else {
            result.success(true)
        }
    }

    // Checks if the audio recording permission is granted
    private val isPermissionGranted: Boolean
        get() {
            val activity = this.activity ?: return false
            val result = ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            return result == PackageManager.PERMISSION_GRANTED
        }

    // Requests audio recording permission
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

    // Handles the result of a permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (pendingPermResult != null) {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pendingPermResult!!.success(true)
                } else {
                    pendingPermResult!!.success(false)
                }
                pendingPermResult = null
                return true
            }
        }
        return false
    }

    // Returns the file extension based on the encoder type
    private fun getFileExtension(encoder: Int): String {
        return when (encoder) {
            6 -> ".wav" // WAV format
            3, 4 -> ".amr" // AMR format
            5 -> ".opus" // Opus format
            else -> ".m4a" // Default to AAC format
        }
    }
}