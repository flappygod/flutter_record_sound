import Flutter
import UIKit
import AVFoundation

/**
 A Flutter plugin for recording audio on iOS.
 This plugin provides methods to start, stop, pause, resume recording, check permissions, and retrieve amplitude information.
 */
public class FlutterRecordSoundPlugin: NSObject, FlutterPlugin, AVAudioRecorderDelegate {
    
    // MARK: - Plugin Registration
    
    /**
     Registers the plugin with the Flutter engine.
     
     - Parameters:
        - registrar: The registrar provided by Flutter for registering plugins.
     */
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_record_sound", binaryMessenger: registrar.messenger())
        let instance = FlutterRecordSoundPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    // MARK: - Properties
    
    /// Indicates whether the recorder is currently recording.
    private var isRecording = false
    
    /// Indicates whether the recorder is currently paused.
    private var isPaused = false
    
    /// Indicates whether the app has permission to record audio.
    private var hasPermission = false
    
    /// The audio recorder instance used for recording.
    private var audioRecorder: AVAudioRecorder?
    
    /// The file path where the audio recording is saved.
    private var path: String?
    
    /// The maximum amplitude recorded so far.
    private var maxAmplitude: Float = -160.0
    
    /// A thread-safe queue for managing state variables.
    private let stateQueue = DispatchQueue(label: "com.flappy.flutter_record_sound.state")
    
    /// Thread-safe accessor for `isRecording`.
    private var isRecordingSafe: Bool {
        get { stateQueue.sync { isRecording } }
        set { stateQueue.sync { isRecording = newValue } }
    }
    
    /// Thread-safe accessor for `isPaused`.
    private var isPausedSafe: Bool {
        get { stateQueue.sync { isPaused } }
        set { stateQueue.sync { isPaused = newValue } }
    }
    
    // MARK: - Flutter Method Call Handling
    
    /**
     Handles method calls from Flutter.
     
     - Parameters:
        - call: The method call from Flutter.
        - result: The result callback to send data back to Flutter.
     */
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "start":
            let args = call.arguments as! [String: Any]
            path = args["path"] as? String
            if path == nil {
                // Generate a temporary file path if none is provided
                let directory = NSTemporaryDirectory()
                let fileName = UUID().uuidString + getFileExtension(for: args["encoder"] as? Int ?? 0)
                if let validPath = NSURL.fileURL(withPathComponents: [directory, fileName])?.absoluteString {
                    path = validPath
                } else {
                    result(FlutterError(code: "-2", message: "Failed to generate file path", details: nil))
                    return
                }
            }
            start(
                path: path!,
                encoder: args["encoder"] as? Int ?? 0,
                bitRate: args["bitRate"] as? Int ?? 128000,
                samplingRate: args["samplingRate"] as? Float ?? 44100.0,
                result: result
            )
        case "stop":
            stop(result)
        case "pause":
            pause(result)
        case "resume":
            resume(result)
        case "isPaused":
            result(isPausedSafe)
        case "isRecording":
            result(isRecordingSafe)
        case "hasPermission":
            hasPermission(result)
        case "getAmplitude":
            getAmplitude(result)
        case "dispose":
            dispose(result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    // MARK: - App Lifecycle Handling
    
    /**
     Stops recording when the app is about to terminate.
     */
    public func applicationWillTerminate(_ application: UIApplication) {
        stopRecording()
    }
    
    /**
     Stops recording when the app enters the background.
     */
    public func applicationDidEnterBackground(_ application: UIApplication) {
        stopRecording()
    }
    
    // MARK: - Permission Handling
    
    /**
     Checks and requests permission to record audio.
     
     - Parameters:
        - result: The result callback to send the permission status back to Flutter.
     */
    fileprivate func hasPermission(_ result: @escaping FlutterResult) {
        switch AVAudioSession.sharedInstance().recordPermission {
        case .granted:
            hasPermission = true
            result(hasPermission)
        case .denied:
            hasPermission = false
            result(hasPermission)
        case .undetermined:
            // Request permission if not determined
            AVAudioSession.sharedInstance().requestRecordPermission { [unowned self] allowed in
                DispatchQueue.main.async {
                    self.hasPermission = allowed
                    result(self.hasPermission) // Return result after permission request
                }
            }
        default:
            result(false)
        }
    }
    
    // MARK: - Recording Methods
    
    /**
     Starts recording audio with the specified parameters.
     
     - Parameters:
        - path: The file path where the recording will be saved.
        - encoder: The audio encoder type (e.g., AAC, AMR, WAV).
        - bitRate: The bit rate for the recording.
        - samplingRate: The sampling rate for the recording.
        - result: The result callback to notify Flutter about the recording status.
     */
    fileprivate func start(path: String, encoder: Int, bitRate: Int, samplingRate: Float, result: @escaping FlutterResult) {
        stopRecording() // Stop any ongoing recording
        
        let settings: [String: Any] = [
            AVFormatIDKey: getEncoder(encoder),
            AVEncoderBitRateKey: bitRate,
            AVSampleRateKey: samplingRate,
            AVNumberOfChannelsKey: 2,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue,
            AVLinearPCMBitDepthKey: 16, // 16-bit PCM for WAV
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsFloatKey: false
        ]
        
        let options: AVAudioSession.CategoryOptions = [.defaultToSpeaker, .allowBluetooth]
        
        DispatchQueue.global(qos: .background).async {
            do {
                // Configure the audio session
                try AVAudioSession.sharedInstance().setCategory(.playAndRecord, options: options)
                try AVAudioSession.sharedInstance().setActive(true)
                
                // Initialize the audio recorder
                let url = URL(string: path) ?? URL(fileURLWithPath: path)
                self.audioRecorder = try AVAudioRecorder(url: url, settings: settings)
                self.audioRecorder?.delegate = self
                self.audioRecorder?.isMeteringEnabled = true
                self.audioRecorder?.record()
                
                // Update state
                self.isRecordingSafe = true
                self.isPausedSafe = false
                
                DispatchQueue.main.async {
                    result(nil)
                }
            } catch {
                DispatchQueue.main.async {
                    result(FlutterError(code: "-1", message: "Failed to start recording", details: nil))
                }
            }
        }
    }
    
    /**
     Stops the recording and returns the file path.
     
     - Parameters:
        - result: The result callback to send the file path back to Flutter.
     */
    fileprivate func stop(_ result: @escaping FlutterResult) {
        stopRecording()
        result(path)
    }
    
    /**
     Pauses the recording.
     
     - Parameters:
        - result: The result callback to notify Flutter about the pause status.
     */
    fileprivate func pause(_ result: @escaping FlutterResult) {
        audioRecorder?.pause()
        isPausedSafe = true
        result(nil)
    }
    
    /**
     Resumes the recording.
     
     - Parameters:
        - result: The result callback to notify Flutter about the resume status.
     */
    fileprivate func resume(_ result: @escaping FlutterResult) {
        if isPausedSafe {
            audioRecorder?.record()
            isPausedSafe = false
        }
        result(nil)
    }
    
    /**
     Retrieves the current and maximum amplitude.
     
     - Parameters:
        - result: The result callback to send amplitude data back to Flutter.
     */
    fileprivate func getAmplitude(_ result: @escaping FlutterResult) {
        var amp: [String: Float] = ["current": -160.0]
        
        if isRecordingSafe {
            audioRecorder?.updateMeters()
            if let current = audioRecorder?.averagePower(forChannel: 0), current != -Float.infinity {
                if current > maxAmplitude {
                    maxAmplitude = current
                }
                amp["current"] = current
                amp["max"] = maxAmplitude
            }
        }
        
        result(amp)
    }
    
    /**
     Stops the recording and resets the state.
     */
    fileprivate func stopRecording() {
        if let recorder = audioRecorder, recorder.isRecording {
            recorder.stop()
        }
        audioRecorder = nil
        isRecordingSafe = false
        isPausedSafe = false
        maxAmplitude = -160.0
    }
    
    /**
     Releases resources and stops the recording.
     
     - Parameters:
        - result: The result callback to notify Flutter about the disposal status.
     */
    fileprivate func dispose(_ result: @escaping FlutterResult) {
        stopRecording()
        result(path)
    }
    
    // MARK: - Helper Methods
    
    /**
     Returns the encoder type for the given encoder ID.
     
     - Parameters:
        - encoder: The encoder ID.
     - Returns: The corresponding encoder type.
     */
    fileprivate func getEncoder(_ encoder: Int) -> Int {
        switch encoder {
        case 1:
            return Int(kAudioFormatMPEG4AAC_ELD)
        case 2:
            return Int(kAudioFormatMPEG4AAC_HE)
        case 3:
            return Int(kAudioFormatAMR)
        case 4:
            return Int(kAudioFormatAMR_WB)
        case 5:
            if #available(iOS 14.0, *) {
                return Int(kAudioFormatOpus)
            } else {
                return Int(kAudioFormatMPEG4AAC) // Fallback to AAC
            }
        case 6: // WAV format
            return Int(kAudioFormatLinearPCM)
        default:
            return Int(kAudioFormatMPEG4AAC)
        }
    }
    
    /**
     Returns the file extension for the given encoder ID.
     
     - Parameters:
        - encoder: The encoder ID.
     - Returns: The corresponding file extension.
     */
    fileprivate func getFileExtension(for encoder: Int) -> String {
        switch encoder {
        case 6:
            return ".wav" // WAV format
        case 3, 4:
            return ".amr" // AMR format
        case 5:
            return ".opus" // Opus format
        default:
            return ".m4a" // Default to AAC
        }
    }
}
