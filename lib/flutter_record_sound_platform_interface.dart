import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:flutter_record_sound/types/types.dart';
import 'flutter_record_sound_method_channel.dart';

abstract class FlutterRecordSoundPlatform extends PlatformInterface {
  /// Constructs a FlutterRecordSoundPlatform.
  FlutterRecordSoundPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterRecordSoundPlatform _instance = MethodChannelFlutterRecordSound();

  /// The default instance of [FlutterRecordSoundPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterRecordSound].
  static FlutterRecordSoundPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterRecordSoundPlatform] when
  /// they register themselves.
  static set instance(FlutterRecordSoundPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Starts new recording session.
  ///
  /// [path]: The output path file. If not provided will use temp folder.
  /// Ignored on web platform, output path is retrieve on stop.
  ///
  /// [encoder]: The audio encoder to be used for recording.
  /// Ignored on web platform.
  ///
  /// [bitRate]: The audio encoding bit rate in bits per second.
  ///
  /// [samplingRate]: The sampling rate for audio in samples per second.
  /// Ignored on web platform.
  Future<void> start({
    String? path,
    AudioEncoder encoder = AudioEncoder.AAC,
    int bitRate = 128000,
    double samplingRate = 44100.0,
  });

  /// Stops recording session and release internal recorder resource.
  /// Returns the output path.
  Future<String?> stop();

  /// Pauses recording session.
  ///
  /// Note: Usable on Android API >= 24(Nougat). Does nothing otherwise.
  Future<void> pause();

  /// Resumes recording session after [pause].
  ///
  /// Note: Usable on Android API >= 24(Nougat). Does nothing otherwise.
  Future<void> resume();

  /// Checks if there's valid recording session.
  /// So if session is paused, this method will still return [bool.true].
  Future<bool> isRecording();

  /// Checks if recording session is paused.
  Future<bool> isPaused();

  /// Checks and requests for audio record permission.
  Future<bool> hasPermission();

  /// Dispose the recorder
  Future<void> dispose();

  /// Gets current average & max amplitudes
  /// Always returns zeros on web platform
  Future<Amplitude> getAmplitude();
}
