import 'package:flutter_record_sound/flutter_record_sound_platform_interface.dart';
import 'package:flutter_web_plugins/flutter_web_plugins.dart';
import 'package:flutter/foundation.dart';
import 'dart:js_interop_unsafe';
import 'package:web/web.dart';
import 'types/amplitude.dart';
import 'types/encoder.dart';
import 'dart:js_interop';
import 'dart:async';
import 'dart:math';

/// A web implementation of the `FlutterRecordSoundPlatform` interface.
/// This class uses the Web APIs to enable audio recording in the browser.
class FlutterRecordSoundPluginWeb extends FlutterRecordSoundPlatform {
  /// Registers this class as the default instance of `FlutterRecordSoundPlatform`.
  static void registerWith(Registrar registrar) {
    FlutterRecordSoundPlatform.instance = FlutterRecordSoundPluginWeb();
  }

  MediaRecorder? _mediaRecorder;
  AudioContext? _audioContext;
  AnalyserNode? _analyserNode;
  MediaStreamAudioSourceNode? _audioSourceNode;
  final List<Blob> _chunks = <Blob>[];
  Completer<String>? _onStopCompleter;

  // Event listeners for data availability and stop events.
  dynamic jsAvailable;
  dynamic jsStop;

  // Maximum amplitude recorded.
  double _maxAmplitude = -160;

  /// Disposes of the resources used for audio recording.
  @override
  Future<void> dispose() async {
    if (_mediaRecorder?.state == 'recording' ||
        _mediaRecorder?.state == 'paused') {
      _mediaRecorder?.stop();
    }
    _resetMediaRecorder();
    _audioContext?.close();
    _audioContext = null;
    _analyserNode = null;
    _audioSourceNode = null;
    _chunks.clear();
  }

  /// Checks if the browser has permission to access the microphone.
  ///
  /// Returns:
  /// - A [Future] containing a boolean value indicating whether the permission is granted.
  @override
  Future<bool> hasPermission() async {
    try {
      final MediaStream stream = await window.navigator.mediaDevices
          .getUserMedia(
            MediaStreamConstraints(
              audio: true.toJS,
            ),
          )
          .toDart;
      // Stop the audio stream to release resources.
      stream.getTracks().toDart.forEach((track) {
        track.stop();
      });
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Checks if the recording is currently paused.
  ///
  /// Returns:
  /// - A [Future] containing a boolean value indicating whether the recording is paused.
  @override
  Future<bool> isPaused() async {
    return _mediaRecorder?.state == 'paused';
  }

  /// Checks if audio is currently being recorded.
  ///
  /// Returns:
  /// - A [Future] containing a boolean value indicating whether recording is in progress.
  @override
  Future<bool> isRecording() async {
    return _mediaRecorder?.state == 'recording';
  }

  /// Pauses the current recording.
  @override
  Future<void> pause() async {
    if (kDebugMode) {
      print('Recording paused');
    }
    _mediaRecorder?.pause();
  }

  /// Resumes a paused recording.
  @override
  Future<void> resume() async {
    if (kDebugMode) {
      print('Recording resumed');
    }
    _mediaRecorder?.resume();
  }

  /// Starts recording audio.
  ///
  /// Parameters:
  /// - [path]: The file path where the recording will be saved (not used in web).
  /// - [encoder]: The audio encoding format. Defaults to [AudioEncoder.AAC].
  /// - [bitRate]: The audio bit rate. Defaults to 128000.
  /// - [samplingRate]: The audio sampling rate. Defaults to 44100.0.
  @override
  Future<void> start({
    String? path,
    AudioEncoder encoder = AudioEncoder.AAC,
    int bitRate = 128000,
    double samplingRate = 44100.0,
  }) async {
    if (_mediaRecorder?.state == 'recording') {
      _mediaRecorder?.stop();
      _resetMediaRecorder();
    }

    try {
      final MediaStream stream = await window.navigator.mediaDevices
          .getUserMedia(
            MediaStreamConstraints(
              audio: true.toJS,
            ),
          )
          .toDart;
      _initializeRecorder(stream);
      _initializeAudioContext(stream);
    } catch (error, stack) {
      _handleError(error, stack);
      rethrow;
    }
  }

  /// Stops the recording and returns the URL of the recorded audio.
  ///
  /// Returns:
  /// - A [Future] containing the URL of the recorded audio, or null if no recording was active.
  @override
  Future<String?> stop() async {
    if (_mediaRecorder?.state == 'recording' ||
        _mediaRecorder?.state == 'paused') {
      _onStopCompleter = Completer<String>();
      _mediaRecorder?.stop();
      return _onStopCompleter?.future;
    }
    return null;
  }

  /// Retrieves the current and maximum amplitude of the audio being recorded.
  ///
  /// Returns:
  /// - A [Future] containing an [Amplitude] object with the current and maximum amplitude values.
  @override
  Future<Amplitude> getAmplitude() async {
    if (_analyserNode == null) {
      return Amplitude(current: -160, max: _maxAmplitude);
    }

    // Get the audio time-domain data.
    final Uint8List timeDomainData = Uint8List(_analyserNode!.fftSize);
    _analyserNode!.getByteTimeDomainData(timeDomainData.toJS);

    // Calculate the current amplitude in decibels.
    double sum = 0;
    for (final value in timeDomainData) {
      // Normalize the value to the range [-1, 1].
      final double normalizedValue = (value / 128.0) - 1.0;
      sum += normalizedValue * normalizedValue;
    }
    final rms = sqrt(sum / timeDomainData.length);
    final double currentAmplitude = rms > 0 ? 20 * log10(rms) : -160;

    // Update the maximum amplitude.
    if (currentAmplitude > _maxAmplitude) {
      _maxAmplitude = currentAmplitude;
    }

    return Amplitude(current: currentAmplitude, max: _maxAmplitude);
  }

  /// Initializes the media recorder with the provided audio stream.
  void _initializeRecorder(MediaStream stream) {
    if (kDebugMode) {
      print('Start recording');
    }
    _mediaRecorder = MediaRecorder(stream);

    //Add event listeners for data availability and stop events.
    jsAvailable = ((JSObject data) {
      _onData(data);
    }).toJS;
    jsStop = ((JSObject data) {
      _onStop(data);
    }).toJS;
    _mediaRecorder?.addEventListener('dataavailable', jsAvailable);
    _mediaRecorder?.addEventListener('stop', jsStop);
    _mediaRecorder?.start();
  }

  /// Initializes the audio context and connects the audio stream to an analyser node.
  void _initializeAudioContext(MediaStream stream) {
    _audioContext = AudioContext();
    _audioSourceNode = _audioContext!.createMediaStreamSource(stream);
    _analyserNode = _audioContext!.createAnalyser();
    _analyserNode!.fftSize = 2048;
    _audioSourceNode!.connect(_analyserNode!);
  }

  /// Handles errors that occur during recording.
  void _handleError(Object error, StackTrace trace) {
    if (kDebugMode) {
      print('Error during recording: $error');
      print(trace);
    }
  }

  /// Handles the `dataavailable` event and collects audio data chunks.
  void _onData(JSObject event) {
    //final Blob? blob = getProperty(event, 'data');
    final Blob? blob = event.getProperty('data'.toJS);
    if (blob != null) {
      _chunks.add(blob);
    }
  }

  /// Handles the `stop` event and finalizes the recording.
  void _onStop(JSObject event) {
    if (kDebugMode) {
      print('Stop recording');
    }

    // Combine the collected audio chunks into a single Blob and create a downloadable URL.
    final Blob audioBlob = Blob(_chunks.toJS);
    final String audioUrl = URL.createObjectURL(audioBlob);

    // Clean up resources.
    _resetMediaRecorder();

    // Complete the stop completer with the audio URL.
    _onStopCompleter?.complete(audioUrl);
  }

  /// Resets the media recorder and clears the collected audio chunks.
  void _resetMediaRecorder() {
    _mediaRecorder?.removeEventListener('dataavailable', jsAvailable);
    _mediaRecorder?.removeEventListener('stop', jsStop);
    _mediaRecorder = null;
    _chunks.clear();
  }

  /// Calculates the base-10 logarithm of a number.
  double log10(num x) {
    return log(x) / ln10;
  }
}
