import 'package:flutter_record_sound/flutter_record_sound_platform_interface.dart';
import 'package:flutter_web_plugins/flutter_web_plugins.dart';
import 'package:flutter/foundation.dart';
import 'package:web/web.dart';
import 'types/amplitude.dart';
import 'types/encoder.dart';
import 'dart:js_interop';
import 'dart:js_util';
import 'dart:async';
import 'dart:math';
import 'dart:html';

class FlutterRecordSoundPluginWeb extends FlutterRecordSoundPlatform {
  static void registerWith(Registrar registrar) {
    FlutterRecordSoundPlatform.instance = FlutterRecordSoundPluginWeb();
  }

  MediaRecorder? _mediaRecorder;
  AudioContext? _audioContext;
  AnalyserNode? _analyserNode;
  MediaStreamAudioSourceNode? _audioSourceNode;
  final List<Blob> _chunks = <Blob>[];
  Completer<String>? _onStopCompleter;

  //监听数据可用事件
  var jsAvailable;
  var jsStop;

  // 最大振幅
  double _maxAmplitude = -160;

  @override
  Future<void> dispose() async {
    if (_mediaRecorder?.state == 'recording' || _mediaRecorder?.state == 'paused') {
      _mediaRecorder?.stop();
    }
    _resetMediaRecorder();
    _audioContext?.close();
    _audioContext = null;
    _analyserNode = null;
    _audioSourceNode = null;
    _chunks.clear();
  }

  @override
  Future<bool> hasPermission() async {
    try {
      final MediaStream? stream = await promiseToFuture(
        window.navigator.mediaDevices.getUserMedia(
          MediaStreamConstraints(
            audio: true.toJS,
          ),
        ),
      );
      // 停止音频流以释放资源
      stream?.getTracks().toDart.forEach((track) {
        track.stop();
      });
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
    if (_mediaRecorder?.state == 'recording') {
      _mediaRecorder?.stop();
      _resetMediaRecorder();
    }

    try {
      final MediaStream? stream = await promiseToFuture(
        window.navigator.mediaDevices.getUserMedia(
          MediaStreamConstraints(
            audio: true.toJS,
          ),
        ),
      );
      if (stream != null) {
        _initializeRecorder(stream);
        _initializeAudioContext(stream);
      } else {
        throw Exception('Audio recording not supported.');
      }
    } catch (error, stack) {
      _handleError(error, stack);
      rethrow;
    }
  }

  @override
  Future<String?> stop() async {
    if (_mediaRecorder?.state == 'recording' || _mediaRecorder?.state == 'paused') {
      _onStopCompleter = Completer<String>();
      _mediaRecorder?.stop();
      return _onStopCompleter?.future;
    }
    return null;
  }

  @override
  Future<Amplitude> getAmplitude() async {
    if (_analyserNode == null) {
      return Amplitude(current: -160, max: _maxAmplitude);
    }

    //获取音频时域数据
    final Uint8List timeDomainData = Uint8List(_analyserNode!.fftSize);
    _analyserNode!.getByteTimeDomainData(timeDomainData.toJS);

    //计算当前振幅分贝
    double sum = 0;
    for (final value in timeDomainData) {
      //范围 [-1, 1]
      final double normalizedValue = (value / 128.0) - 1.0;
      sum += normalizedValue * normalizedValue;
    }
    final rms = sqrt(sum / timeDomainData.length);
    final double currentAmplitude = rms > 0 ? 20 * log10(rms) : -160;

    //更新最大振幅
    if (currentAmplitude > _maxAmplitude) {
      _maxAmplitude = currentAmplitude;
    }

    return Amplitude(current: currentAmplitude, max: _maxAmplitude);
  }

  void _initializeRecorder(MediaStream stream) {
    if (kDebugMode) {
      print('Start recording');
    }
    _mediaRecorder = MediaRecorder(stream);

    //监听数据可用事件
    jsAvailable = allowInterop(_onData);
    jsStop = allowInterop(_onStop);
    _mediaRecorder?.addEventListener('dataavailable', jsAvailable);
    _mediaRecorder?.addEventListener('stop', jsStop);
    _mediaRecorder?.start();
  }

  void _initializeAudioContext(MediaStream stream) {
    _audioContext = AudioContext();
    _audioSourceNode = _audioContext!.createMediaStreamSource(stream);
    _analyserNode = _audioContext!.createAnalyser();
    _analyserNode!.fftSize = 2048;
    _audioSourceNode!.connect(_analyserNode!);
  }

  void _handleError(Object error, StackTrace trace) {
    if (kDebugMode) {
      print('Error during recording: $error');
      print(trace);
    }
  }

  void _onData(var event) {
    final Blob? blob = getProperty(event, 'data');
    if (blob != null) {
      _chunks.add(blob);
    }
  }

  void _onStop(var event) {
    if (kDebugMode) {
      print('Stop recording');
    }

    //将 Blob 转换为可下载的 URL
    final Blob audioBlob = Blob(_chunks.toJS);
    final String audioUrl = Url.createObjectUrl(audioBlob);

    //清理资源
    _resetMediaRecorder();

    //返回音频 URL
    _onStopCompleter?.complete(audioUrl);
  }

  void _resetMediaRecorder() {
    // 监听数据可用事件
    _mediaRecorder?.removeEventListener('dataavailable', jsAvailable);
    _mediaRecorder?.removeEventListener('stop', jsStop);
    _mediaRecorder = null;
    _chunks.clear();
  }

  /// 计算以 10 为底的对数
  double log10(num x) {
    return log(x) / ln10;
  }
}
