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

    //activity plugin binding
    private var activityPluginBinding: ActivityPluginBinding? = null

    //record permission result
    private var pendingPermResult: MethodChannel.Result? = null

    //method channel
    private lateinit var channel: MethodChannel

    //context
    private var context: Context? = null

    //activity
    private var activity: Activity? = null

    //recorder
    private var recorder: Recorder? = null

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
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        addBinding(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        removeBinding()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        addBinding(binding)
    }

    override fun onDetachedFromActivity() {
        removeBinding()
    }

    private fun addBinding(binding: ActivityPluginBinding) {
        activityPluginBinding?.removeRequestPermissionsResultListener(this)
        activity = binding.activity
        activityPluginBinding = binding
        activityPluginBinding!!.addRequestPermissionsResultListener(this)
        this.recorder = Recorder(binding.activity)
    }

    private fun removeBinding() {
        activityPluginBinding?.removeRequestPermissionsResultListener(this)
        activity = null
        activityPluginBinding = null
        recorder?.close()
        recorder = null
        pendingPermResult = null
    }

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

    //Handle the start recording method call
    private fun handleStart(call: MethodCall, result: MethodChannel.Result) {
        val encoder = call.argument<Int>("encoder")
        val bitRate = call.argument<Int>("bitRate")
        val samplingRate = call.argument<Double>("samplingRate")
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
                result.error("-3", "Failed to create temporary file", e.message)
                return
            }
        }
        if (encoder == null || bitRate == null || samplingRate == null) {
            result.error("-4", "Invalid arguments for start method", null)
            return
        }
        recorder?.start(path!!, encoder, bitRate, samplingRate, result)
    }

    //Handle the stop recording method call
    private fun handleStop(result: MethodChannel.Result) {
        recorder?.stop(result) ?: result.error("-5", "Recorder is not initialized", null)
    }

    //Handle the pause recording method call
    private fun handlePause(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder?.pause(result) ?: result.error("-5", "Recorder is not initialized", null)
        } else {
            result.error("-6", "Pause is not supported on this Android version", null)
        }
    }

    //Handle the resume recording method call
    private fun handleResume(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recorder?.resume(result) ?: result.error("-5", "Recorder is not initialized", null)
        } else {
            result.error("-6", "Resume is not supported on this Android version", null)
        }
    }

    //handle the isPaused method call
    private fun handleIsPaused(result: MethodChannel.Result) {
        recorder?.isPaused(result) ?: result.error("-5", "Recorder is not initialized", null)
    }

    //handle the isRecording method call
    private fun handleIsRecording(result: MethodChannel.Result) {
        recorder?.isRecording(result) ?: result.error("-5", "Recorder is not initialized", null)
    }


    //handle the getAmplitude method call
    private fun handleGetAmplitude(result: MethodChannel.Result) {
        recorder?.getAmplitude(result) ?: result.error("-5", "Recorder is not initialized", null)
    }

    //handle the dispose method call
    private fun handleDispose(result: MethodChannel.Result) {
        recorder?.close() ?: result.error("-5", "Recorder is not initialized", null)
        result.success(null)
    }


    //handle the hasPermission method call
    private fun handleHasPermission(result: MethodChannel.Result) {
        if (!isPermissionGranted) {
            pendingPermResult = result
            askForPermission()
        } else {
            result.success(true)
        }
    }

    //Check if the record audio permission is granted
    private val isPermissionGranted: Boolean
        get() {
            val activity = this.activity ?: return false
            val result = ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            return result == PackageManager.PERMISSION_GRANTED
        }

    //Request the record audio permission
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

    ///request permission result
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
}