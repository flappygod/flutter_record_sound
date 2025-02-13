    <h1>Flutter Record Sound Plugin</h1>
    <p>
        <code>flutter_record_sound</code> is a Flutter plugin that provides a simple API for recording audio. It supports starting, stopping, pausing, resuming, and querying the recording state. Additionally, it provides amplitude information for the recorded audio.
    </p>

    <h2>Features</h2>
    <ul>
        <li>Start and stop audio recording.</li>
        <li>Pause and resume recording (if supported by the platform).</li>
        <li>Query the current recording state (e.g., isRecording, isPaused).</li>
        <li>Check for microphone permissions.</li>
        <li>Retrieve amplitude information (current and max amplitude).</li>
        <li>Supports customizable audio encoding, bit rate, and sampling rate.</li>
    </ul>

    <h2>Installation</h2>
    <p>Add the following dependency to your <code>pubspec.yaml</code> file:</p>
    <pre><code>dependencies:
flutter_record_sound: ^latest_version
</code></pre>
<p>Then, run the following command to fetch the package:</p>
<pre><code>flutter pub get</code></pre>

    <h2>Usage</h2>
    <h3>Import the Plugin</h3>
    <pre><code>import 'package:flutter_record_sound/flutter_record_sound.dart';</code></pre>

    <h3>Basic Example</h3>
    <pre><code>import 'package:flutter_record_sound/flutter_record_sound.dart';

final FlutterRecordSound recorder = FlutterRecordSound();

Future&lt;void&gt; recordAudio() async {
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

Future&lt;void&gt; stopRecording() async {
// Stop recording and get the file path
String? filePath = await recorder.stop();
print("Recording stopped. File saved at: $filePath");
}
</code></pre>

    <h2>API Reference</h2>
    <h3>1. <code>start</code></h3>
    <p>Starts audio recording.</p>
    <h4>Parameters:</h4>
    <ul>
        <li><code>path</code> (optional): The file path where the audio will be saved. If not provided, a temporary file will be created.</li>
        <li><code>encoder</code> (optional): The audio encoder to use. Defaults to <code>AudioEncoder.AAC</code>.</li>
        <li><code>bitRate</code> (optional): The bit rate for the recording. Defaults to <code>128000</code>.</li>
        <li><code>samplingRate</code> (optional): The sampling rate for the recording. Defaults to <code>44100.0</code>.</li>
    </ul>
    <h4>Example:</h4>
    <pre><code>await recorder.start(
path: "path/to/save/audio.m4a",
encoder: AudioEncoder.AAC,
bitRate: 128000,
samplingRate: 44100.0,
);</code></pre>

    <h3>2. <code>stop</code></h3>
    <p>Stops the recording and returns the file path of the recorded audio.</p>
    <h4>Returns:</h4>
    <p><code>Future&lt;String?&gt;</code>: The file path of the recorded audio.</p>
    <h4>Example:</h4>
    <pre><code>String? filePath = await recorder.stop();
print("Recording saved at: $filePath");</code></pre>

    <h3>3. <code>pause</code></h3>
    <p>Pauses the recording (if supported by the platform).</p>
    <h4>Example:</h4>
    <pre><code>await recorder.pause();
print("Recording paused.");</code></pre>

    <h3>4. <code>resume</code></h3>
    <p>Resumes the paused recording (if supported by the platform).</p>
    <h4>Example:</h4>
    <pre><code>await recorder.resume();
print("Recording resumed.");</code></pre>

    <h3>5. <code>isRecording</code></h3>
    <p>Checks if the recorder is currently recording.</p>
    <h4>Returns:</h4>
    <p><code>Future&lt;bool&gt;</code>: <code>true</code> if the recorder is recording, otherwise <code>false</code>.</p>
    <h4>Example:</h4>
    <pre><code>bool recording = await recorder.isRecording();
print("Is recording: $recording");</code></pre>

    <h3>6. <code>isPaused</code></h3>
    <p>Checks if the recorder is currently paused.</p>
    <h4>Returns:</h4>
    <p><code>Future&lt;bool&gt;</code>: <code>true</code> if the recorder is paused, otherwise <code>false</code>.</p>
    <h4>Example:</h4>
    <pre><code>bool paused = await recorder.isPaused();
print("Is paused: $paused");</code></pre>

    <h3>7. <code>hasPermission</code></h3>
    <p>Checks if the app has permission to access the microphone.</p>
    <h4>Returns:</h4>
    <p><code>Future&lt;bool&gt;</code>: <code>true</code> if the app has microphone permission, otherwise <code>false</code>.</p>
    <h4>Example:</h4>
    <pre><code>bool hasPermission = await recorder.hasPermission();
print("Has microphone permission: $hasPermission");</code></pre>

    <h3>8. <code>dispose</code></h3>
    <p>Releases resources used by the recorder.</p>
    <h4>Example:</h4>
    <pre><code>await recorder.dispose();
print("Recorder disposed.");</code></pre>

    <h3>9. <code>getAmplitude</code></h3>
    <p>Retrieves the current and maximum amplitude of the recorded audio.</p>
    <h4>Returns:</h4>
    <p><code>Future&lt;Amplitude&gt;</code>: An object containing the current and maximum amplitude.</p>
    <h4>Example:</h4>
    <pre><code>Amplitude amplitude = await recorder.getAmplitude();
print("Current amplitude: ${amplitude.current}");
print("Max amplitude: ${amplitude.max}");</code></pre>

    <h2>Platform Support</h2>
    <ul>
        <li><strong>Android</strong>: Fully supported.</li>
        <li><strong>iOS</strong>: Fully supported.</li>
        <li><strong>Web</strong>: Not supported (yet).</li>
        <li><strong>Desktop</strong>: Not supported (yet).</li>
    </ul>

    <h2>Permissions</h2>
    <p>Make sure to request microphone permissions in your app. For Android, add the following to your <code>AndroidManifest.xml</code>:</p>
    <pre><code>&lt;uses-permission android:name="android.permission.RECORD_AUDIO" /&gt;</code></pre>
    <p>For iOS, add the following to your <code>Info.plist</code>:</p>
    <pre><code>&lt;key&gt;NSMicrophoneUsageDescription&lt;/key&gt;
&lt;string&gt;We need access to your microphone to record audio.&lt;/string&gt;</code></pre>

    <h2>License</h2>
    <p>This plugin is licensed under the <a href="https://opensource.org/licenses/MIT">MIT License</a>.</p>

    <h2>Contributing</h2>
    <p>Contributions are welcome! If you find a bug or want to add a feature, feel free to open an issue or submit a pull request on GitHub.</p>

    <h2>Support</h2>
    <p>If you encounter any issues or have questions, please open an issue on the GitHub repository.</p>