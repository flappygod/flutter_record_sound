flutter_record_sound is a Flutter plugin that provides a simple API for recording audio. It supports starting, stopping, pausing, resuming, and querying the recording state. Additionally, it provides amplitude information for the recorded audio.

Features
Start and stop audio recording.
Pause and resume recording (if supported by the platform).
Query the current recording state (e.g., isRecording, isPaused).
Check for microphone permissions.
Retrieve amplitude information (current and max amplitude).
Supports customizable audio encoding, bit rate, and sampling rate.
Installation
Add the following dependency to your pubspec.yaml file:

dependencies:
flutter_record_sound: ^latest_version
Then, run the following command to fetch the package:

flutter pub get
Usage
Import the Plugin
import 'package:flutter_record_sound/flutter_record_sound.dart';
复制
Basic Example
import 'package:flutter_record_sound/flutter_record_sound.dart';

final FlutterRecordSound recorder = FlutterRecordSound();

Future<void> recordAudio() async {
// Check for microphone permissions
bool hasPermission = await recorder.hasPermission();
if (!hasPermission) {
print("Microphone permission denied.");
return;
}

// Start recording
await recorder.start(
path: "path/to/save/audio.m4a", // Optional: Specify the file path
encoder: AudioEncoder.AAC,     // Optional: Specify the audio encoder
bitRate: 128000,               // Optional: Specify the bit rate
samplingRate: 44100.0,         // Optional: Specify the sampling rate
);
print("Recording started...");
}

Future<void> stopRecording() async {
// Stop recording and get the file path
String? filePath = await recorder.stop();
print("Recording stopped. File saved at: $filePath");
}
复制
API Reference
1. start
   Starts audio recording.

Parameters:
path (optional): The file path where the audio will be saved. If not provided, a temporary file will be created.
encoder (optional): The audio encoder to use. Defaults to AudioEncoder.AAC.
bitRate (optional): The bit rate for the recording. Defaults to 128000.
samplingRate (optional): The sampling rate for the recording. Defaults to 44100.0.
Example:
await recorder.start(
path: "path/to/save/audio.m4a",
encoder: AudioEncoder.AAC,
bitRate: 128000,
samplingRate: 44100.0,
);
复制
2. stop
   Stops the recording and returns the file path of the recorded audio.

Returns:
Future<String?>: The file path of the recorded audio.

Example:
String? filePath = await recorder.stop();
print("Recording saved at: $filePath");
复制
3. pause
   Pauses the recording (if supported by the platform).

Example:
await recorder.pause();
print("Recording paused.");
复制
4. resume
   Resumes the paused recording (if supported by the platform).

Example:
await recorder.resume();
print("Recording resumed.");
复制
5. isRecording
   Checks if the recorder is currently recording.

Returns:
Future<bool>: true if the recorder is recording, otherwise false.

Example:
bool recording = await recorder.isRecording();
print("Is recording: $recording");
复制
6. isPaused
   Checks if the recorder is currently paused.

Returns:
Future<bool>: true if the recorder is paused, otherwise false.

Example:
bool paused = await recorder.isPaused();
print("Is paused: $paused");
复制
7. hasPermission
   Checks if the app has permission to access the microphone.

Returns:
Future<bool>: true if the app has microphone permission, otherwise false.

Example:
bool hasPermission = await recorder.hasPermission();
print("Has microphone permission: $hasPermission");
复制
8. dispose
   Releases resources used by the recorder.

Example:
await recorder.dispose();
print("Recorder disposed.");
复制
9. getAmplitude
   Retrieves the current and maximum amplitude of the recorded audio.

Returns:
Future<Amplitude>: An object containing the current and maximum amplitude.

Example:
Amplitude amplitude = await recorder.getAmplitude();
print("Current amplitude: ${amplitude.current}");
print("Max amplitude: ${amplitude.max}");