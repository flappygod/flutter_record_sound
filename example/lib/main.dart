import 'package:flutter/material.dart';
import 'package:flutter_record_sound/flutter_record_sound.dart';
import 'package:flutter_record_sound/flutter_record_sound_method_channel.dart';
import 'package:flutter_record_sound/types/types.dart';
import 'package:audioplayers/audioplayers.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Record Sound Test',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const RecordSoundTestPage(),
    );
  }
}

class RecordSoundTestPage extends StatefulWidget {
  const RecordSoundTestPage({super.key});

  @override
  State<RecordSoundTestPage> createState() => _RecordSoundTestPageState();
}

class _RecordSoundTestPageState extends State<RecordSoundTestPage> {
  final FlutterRecordSound _recordSound = FlutterRecordSound();
  final AudioPlayer _audioPlayer =
      AudioPlayer(); // Create an instance of AudioPlayer

  bool _hasPermission = false;
  bool _isRecording = false;
  bool _isPaused = false;
  String? _recordedFilePath;
  Amplitude? _amplitude;

  @override
  void initState() {
    super.initState();
    _checkPermission();
  }

  Future<void> _checkPermission() async {
    final hasPermission = await _recordSound.hasPermission();
    setState(() {
      _hasPermission = hasPermission;
    });
  }

  Future<void> _startRecording() async {
    try {
      await _recordSound.start(path: 'test_record.aac');
      setState(() {
        _isRecording = true;
        _isPaused = false;
      });
    } catch (e) {
      _showError(e.toString());
    }
  }

  Future<void> _stopRecording() async {
    try {
      final filePath = await _recordSound.stop();
      setState(() {
        _isRecording = false;
        _isPaused = false;
        _recordedFilePath = filePath;
      });
    } catch (e) {
      _showError(e.toString());
    }
  }

  Future<void> _pauseRecording() async {
    try {
      await _recordSound.pause();
      setState(() {
        _isPaused = true;
      });
    } catch (e) {
      _showError(e.toString());
    }
  }

  Future<void> _resumeRecording() async {
    try {
      await _recordSound.resume();
      setState(() {
        _isPaused = false;
      });
    } catch (e) {
      _showError(e.toString());
    }
  }

  Future<void> _getAmplitude() async {
    try {
      final amplitude = await _recordSound.getAmplitude();
      setState(() {
        _amplitude = amplitude;
      });
    } catch (e) {
      _showError(e.toString());
    }
  }

  Future<void> _playRecording() async {
    if (_recordedFilePath != null) {
      try {
        await _audioPlayer.play(_recordedFilePath!, isLocal: true);
      } catch (e) {
        _showError(e.toString());
      }
    } else {
      _showError('No recorded file to play.');
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Error: $message')),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Record Sound Test'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Permission: ${_hasPermission ? "Granted" : "Denied"}'),
            const SizedBox(height: 10),
            Text('Recording: ${_isRecording ? "Yes" : "No"}'),
            const SizedBox(height: 10),
            Text('Paused: ${_isPaused ? "Yes" : "No"}'),
            const SizedBox(height: 10),
            if (_recordedFilePath != null)
              Text('Recorded File Path: $_recordedFilePath'),
            const SizedBox(height: 10),
            if (_amplitude != null)
              Text(
                  'Amplitude: Current=${_amplitude!.current}, Max=${_amplitude!.max}'),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: _hasPermission ? _startRecording : null,
              child: const Text('Start Recording'),
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: _isRecording && !_isPaused ? _pauseRecording : null,
              child: const Text('Pause Recording'),
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: _isRecording && _isPaused ? _resumeRecording : null,
              child: const Text('Resume Recording'),
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: _isRecording ? _stopRecording : null,
              child: const Text('Stop Recording'),
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: _isRecording ? _getAmplitude : null,
              child: const Text('Get Amplitude'),
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: _recordedFilePath != null ? _playRecording : null,
              child: const Text('Play Recording'),
            ),
          ],
        ),
      ),
    );
  }
}
