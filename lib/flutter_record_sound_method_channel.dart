import 'package:flutter_record_sound/types/types.dart';
import 'flutter_record_sound_platform_interface.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// An implementation of [FlutterRecordSoundPlatform] that uses method channels.
class MethodChannelFlutterRecordSound extends FlutterRecordSoundPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_record_sound');

  @override
  Future<bool> hasPermission() async {
    final bool? result = await methodChannel.invokeMethod<bool>('hasPermission');
    return result ?? false;
  }

  @override
  Future<bool> isPaused() async {
    final bool? result = await methodChannel.invokeMethod<bool>('isPaused');
    return result ?? false;
  }

  @override
  Future<bool> isRecording() async {
    final bool? result = await methodChannel.invokeMethod<bool>('isRecording');
    return result ?? false;
  }

  @override
  Future<void> pause() {
    return methodChannel.invokeMethod('pause');
  }

  @override
  Future<void> resume() {
    return methodChannel.invokeMethod('resume');
  }

  @override
  Future<void> start({
    String? path,
    AudioEncoder encoder = AudioEncoder.AAC,
    int bitRate = 128000,
    double samplingRate = 44100.0,
  }) {
    return methodChannel.invokeMethod('start', <dynamic, dynamic>{
      'path': path,
      'encoder': encoder.index,
      'bitRate': bitRate,
      'samplingRate': samplingRate,
    });
  }

  @override
  Future<String?> stop() {
    return methodChannel.invokeMethod('stop');
  }

  @override
  Future<void> dispose() async {
    await methodChannel.invokeMethod('dispose');
  }

  @override
  Future<Amplitude> getAmplitude() async {
    final dynamic result = await methodChannel.invokeMethod('getAmplitude');
    return Amplitude(
      current: result?['current'] ?? 0.0,
      max: result?['max'] ?? 0.0,
    );
  }
}
