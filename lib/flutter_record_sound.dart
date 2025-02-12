import 'package:flutter_record_sound/types/types.dart';
import 'flutter_record_sound_platform_interface.dart';

class FlutterRecordSound {
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

  Future<String?> stop() {
    return FlutterRecordSoundPlatform.instance.stop();
  }

  Future<void> pause() {
    return FlutterRecordSoundPlatform.instance.pause();
  }

  Future<void> resume() {
    return FlutterRecordSoundPlatform.instance.resume();
  }

  Future<bool> isRecording() {
    return FlutterRecordSoundPlatform.instance.isRecording();
  }

  Future<bool> isPaused() async {
    return FlutterRecordSoundPlatform.instance.isPaused();
  }

  Future<bool> hasPermission() async {
    return FlutterRecordSoundPlatform.instance.hasPermission();
  }

  Future<void> dispose() async {
    return FlutterRecordSoundPlatform.instance.dispose();
  }

  Future<Amplitude> getAmplitude() {
    return FlutterRecordSoundPlatform.instance.getAmplitude();
  }
}
