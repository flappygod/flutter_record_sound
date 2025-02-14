import 'dart:html' as html;
import 'dart:typed_data';
import 'dart:async';
import 'dart:math';
import 'package:flutter_record_sound/flutter_record_sound_platform_interface.dart';
import 'package:flutter_web_plugins/flutter_web_plugins.dart';
import 'package:flutter/foundation.dart';
import 'types/amplitude.dart';
import 'types/encoder.dart';

class FlutterRecordSoundPluginWeb extends FlutterRecordSoundPlatform {
  static void registerWith(Registrar registrar) {
    FlutterRecordSoundPlatform.instance = FlutterRecordSoundPluginWeb();
  }

  html.MediaRecorder? _mediaRecorder;
  final List<html.Blob> _chunks = <html.Blob>[];
  Completer<String>? _onStopCompleter;

  // 最大振幅
  double _maxAmplitude = -160;

  @override
  Future<void> dispose() async {
    _mediaRecorder?.stop();
    _resetMediaRecorder();
  }

  @override
  Future<bool> hasPermission() async {
    final html.MediaDevices? mediaDevices = html.window.navigator.mediaDevices;
    if (mediaDevices == null) {
      return false;
    }

    try {
      await mediaDevices.getUserMedia({'audio': true});
      return true;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<bool> isPaused() async {
    return _mediaRecorder?.state == 'paused';
  }

  @override
  Future<bool> isRecording() async {
    return _mediaRecorder?.state == 'recording';
  }

  @override
  Future<void> pause() async {
    if (kDebugMode) {
      print('Recording paused');
    }
    _mediaRecorder?.pause();
  }

  @override
  Future<void> resume() async {
    if (kDebugMode) {
      print('Recording resumed');
    }
    _mediaRecorder?.resume();
  }

  @override
  Future<void> start({
    String? path,
    AudioEncoder encoder = AudioEncoder.AAC,
    int bitRate = 128000,
    double samplingRate = 44100.0,
  }) async {
    _mediaRecorder?.stop();
    _resetMediaRecorder();

    try {
      final html.MediaStream? stream = await html.window.navigator.mediaDevices
          ?.getUserMedia({
        'audio': true,
        'audioBitsPerSecond': bitRate,
        'bitsPerSecond': bitRate,
      });

      if (stream != null) {
        _initializeRecorder(stream);
      } else {
        if (kDebugMode) {
          print('Audio recording not supported.');
        }
      }
    } catch (error, stack) {
      _handleError(error, stack);
    }
  }

  @override
  Future<String?> stop() async {
    _onStopCompleter = Completer<String>();
    _mediaRecorder?.stop();
    return _onStopCompleter?.future;
  }

  @override
  Future<Amplitude> getAmplitude() async {
    if (_chunks.isEmpty) {
      return Amplitude(current: -160, max: _maxAmplitude);
    }

    // 将 Blob 转换为 ArrayBuffer
    final html.Blob blob = html.Blob(_chunks);
    final ByteBuffer buffer = await blobToArrayBuffer(blob);

    //将 ArrayBuffer 转换为 Float32List
    final Uint8List uint8List = Uint8List.view(buffer);
    final Float32List float32List = Float32List.sublistView(uint8List);

    //计算 RMS 振幅
    double sum = 0;
    for (final sample in float32List) {
      sum += sample * sample;
    }
    // 使用 dart:math 的 sqrt
    final rms = sqrt(sum / float32List.length);
    // 使用 log10
    final currentAmplitude = 20 * (rms > 0 ? log10(rms) : -1.0);

    // 更新最大振幅
    if (currentAmplitude > _maxAmplitude) {
      _maxAmplitude = currentAmplitude;
    }

    return Amplitude(current: currentAmplitude, max: _maxAmplitude);
  }

  void _initializeRecorder(html.MediaStream stream) {
    if (kDebugMode) {
      print('Start recording');
    }
    _mediaRecorder = html.MediaRecorder(stream);
    _mediaRecorder?.addEventListener('dataavailable', _onDataAvailable);
    _mediaRecorder?.addEventListener('stop', _onStop);
    _mediaRecorder?.start();
  }

  void _handleError(Object error, StackTrace trace) {
    if (kDebugMode) {
      print('Error during recording: $error');
      print(trace);
    }
  }

  void _onDataAvailable(html.Event event) {
    if (event is html.BlobEvent && event.data != null) {
      _chunks.add(event.data!);
    }
  }

  void _onStop(html.Event event) {
    if (kDebugMode) {
      print('Stop recording');
    }
    String? audioUrl;
    if (_chunks.isNotEmpty) {
      final html.Blob blob = html.Blob(_chunks);
      audioUrl = html.Url.createObjectUrl(blob);
    }
    _resetMediaRecorder();
    _onStopCompleter?.complete(audioUrl);
  }

  void _resetMediaRecorder() {
    _mediaRecorder?.removeEventListener('dataavailable', _onDataAvailable);
    _mediaRecorder?.removeEventListener('stop', _onStop);
    _mediaRecorder = null;
    _chunks.clear();
  }

  Future<ByteBuffer> blobToArrayBuffer(html.Blob blob) {
    final completer = Completer<ByteBuffer>();
    final reader = html.FileReader();
    reader.onLoadEnd.listen((_) {
      completer.complete(reader.result as ByteBuffer);
    });
    reader.onError.listen((error) {
      completer.completeError(error);
    });
    reader.readAsArrayBuffer(blob);
    return completer.future;
  }

  /// 计算以 10 为底的对数
  double log10(num x) {
    return log(x) / ln10;
  }
}