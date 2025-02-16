import 'package:flutter_record_sound/types/types.dart';
import 'flutter_record_sound_platform_interface.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// An implementation of [FlutterRecordSoundPlatform] that uses method channels
/// to communicate with the native platform (iOS/Android).
class MethodChannelFlutterRecordSound extends FlutterRecordSoundPlatform {
  /// The method channel used to interact with the native platform.
  /// This channel is responsible for sending method calls to the native side
  /// and receiving responses.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_record_sound');

  /// Checks if the app has permission to record audio.
  ///
  /// Returns:
  /// - A [Future] containing a boolean value indicating whether the app
  ///   has permission to record audio.
  @override
  Future<bool> hasPermission() async {
    final bool? result =
        await methodChannel.invokeMethod<bool>('hasPermission');
    return result ?? false; // Return false if the result is null.
  }

  /// Checks if the recording is currently paused.
  ///
  /// Returns:
  /// - A [Future] containing a boolean value indicating whether the recording
  ///   is paused.
  @override
  Future<bool> isPaused() async {
    final bool? result = await methodChannel.invokeMethod<bool>('isPaused');
    return result ?? false; // Return false if the result is null.
  }

  /// Checks if audio is currently being recorded.
  ///
  /// Returns:
  /// - A [Future] containing a boolean value indicating whether recording
  ///   is in progress.
  @override
  Future<bool> isRecording() async {
    final bool? result = await methodChannel.invokeMethod<bool>('isRecording');
    return result ?? false; // Return false if the result is null.
  }

  /// Pauses the current recording.
  ///
  /// Returns:
  /// - A [Future] that completes when the recording is paused.
  @override
  Future<void> pause() {
    return methodChannel.invokeMethod('pause');
  }

  /// Resumes a paused recording.
  ///
  /// Returns:
  /// - A [Future] that completes when the recording resumes.
  @override
  Future<void> resume() {
    return methodChannel.invokeMethod('resume');
  }

  /// Starts recording audio.
  ///
  /// Parameters:
  /// - [path]: The file path where the recording will be saved. If null, a default path will be used.
  /// - [encoder]: The audio encoding format. Defaults to [AudioEncoder.AAC].
  /// - [bitRate]: The audio bit rate. Defaults to 128000.
  /// - [samplingRate]: The audio sampling rate. Defaults to 44100.0.
  ///
  /// Returns:
  /// - A [Future] that completes when the recording starts.
  @override
  Future<void> start({
    String? path,
    AudioEncoder encoder = AudioEncoder.AAC,
    int bitRate = 128000,
    double samplingRate = 44100.0,
  }) {
    return methodChannel.invokeMethod('start', <dynamic, dynamic>{
      'path': path,
      'encoder': encoder.index, // Pass the encoder as its index value.
      'bitRate': bitRate,
      'samplingRate': samplingRate,
    });
  }

  /// Stops the recording and returns the file path of the recorded audio.
  ///
  /// Returns:
  /// - A [Future] containing the file path of the recorded audio, or null if no recording was saved.
  @override
  Future<String?> stop() {
    return methodChannel.invokeMethod('stop');
  }

  /// Releases resources used for audio recording.
  ///
  /// Returns:
  /// - A [Future] that completes when the resources are released.
  @override
  Future<void> dispose() async {
    await methodChannel.invokeMethod('dispose');
  }

  /// Retrieves the current amplitude of the audio being recorded.
  ///
  /// Returns:
  /// - A [Future] containing an [Amplitude] object representing the current
  ///   and maximum amplitude of the audio.
  @override
  Future<Amplitude> getAmplitude() async {
    final dynamic result = await methodChannel.invokeMethod('getAmplitude');
    return Amplitude(
      current: result?['current'] ?? 0.0, // Current amplitude or 0.0 if null.
      max: result?['max'] ?? 0.0, // Maximum amplitude or 0.0 if null.
    );
  }
}
