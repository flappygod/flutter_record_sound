import 'package:flutter_record_sound/types/types.dart';
import 'flutter_record_sound_platform_interface.dart';

/// The `FlutterRecordSound` class provides a wrapper for audio recording functionality,
/// including starting, stopping, pausing, resuming, and more.
class FlutterRecordSound {
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
  Future<void> start({
    String? path,
    AudioEncoder encoder = AudioEncoder.AAC,
    int bitRate = 128000,
    double samplingRate = 44100.0,
  }) {
    return FlutterRecordSoundPlatform.instance.start(
      path: path,
      encoder: encoder,
      bitRate: bitRate,
      samplingRate: samplingRate,
    );
  }

  /// Stops the recording and returns the file path of the recorded audio.
  ///
  /// Returns:
  /// - A [Future] containing the file path of the recorded audio, or null if no recording was saved.
  Future<String?> stop() {
    return FlutterRecordSoundPlatform.instance.stop();
  }

  /// Pauses the current recording.
  ///
  /// Returns:
  /// - A [Future] that completes when the recording is paused.
  Future<void> pause() {
    return FlutterRecordSoundPlatform.instance.pause();
  }

  /// Resumes a paused recording.
  ///
  /// Returns:
  /// - A [Future] that completes when the recording resumes.
  Future<void> resume() {
    return FlutterRecordSoundPlatform.instance.resume();
  }

  /// Checks if audio is currently being recorded.
  ///
  /// Returns:
  /// - A [Future] containing a boolean value indicating whether recording is in progress.
  Future<bool> isRecording() {
    return FlutterRecordSoundPlatform.instance.isRecording();
  }

  /// Checks if the recording is currently paused.
  ///
  /// Returns:
  /// - A [Future] containing a boolean value indicating whether the recording is paused.
  Future<bool> isPaused() async {
    return FlutterRecordSoundPlatform.instance.isPaused();
  }

  /// Checks if the app has permission to record audio.
  ///
  /// Returns:
  /// - A [Future] containing a boolean value indicating whether the app has audio recording permission.
  Future<bool> hasPermission() async {
    return FlutterRecordSoundPlatform.instance.hasPermission();
  }

  /// Releases resources used for audio recording.
  ///
  /// Returns:
  /// - A [Future] that completes when the resources are released.
  Future<void> dispose() async {
    return FlutterRecordSoundPlatform.instance.dispose();
  }

  /// Retrieves the current amplitude of the audio being recorded.
  ///
  /// Returns:
  /// - A [Future] containing an [Amplitude] object representing the current audio amplitude.
  Future<Amplitude> getAmplitude() {
    return FlutterRecordSoundPlatform.instance.getAmplitude();
  }
}
