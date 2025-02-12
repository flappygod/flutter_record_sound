import Flutter
import UIKit
import AVFoundation

public class FlutterRecordSoundPlugin: NSObject, FlutterPlugin ,AVAudioRecorderDelegate{
    
    // 注册
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_record_sound", binaryMessenger: registrar.messenger())
        let instance = FlutterRecordSoundPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    
    // 属性定义
    var isRecording = false
    var isPaused = false
    var hasPermission = false
    var audioRecorder: AVAudioRecorder?
    var path: String?
    var maxAmplitude: Float = -160.0
    
    // 处理Flutter方法调用
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "start":
            let args = call.arguments as! [String: Any]
            path = args["path"] as? String
            
            if path == nil {
                let directory = NSTemporaryDirectory()
                let fileName = UUID().uuidString + ".m4a"
                path = NSURL.fileURL(withPathComponents: [directory, fileName])?.absoluteString
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
            result(isPaused)
        case "isRecording":
            result(isRecording)
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
    
    // 应用生命周期事件处理
    public func applicationWillTerminate(_ application: UIApplication) {
        stopRecording()
    }
    
    public func applicationDidEnterBackground(_ application: UIApplication) {
        stopRecording()
    }
    
    // 检查录音权限
    fileprivate func hasPermission(_ result: @escaping FlutterResult) {
        switch AVAudioSession.sharedInstance().recordPermission {
        case .granted:
            hasPermission = true
        case .denied:
            hasPermission = false
        case .undetermined:
            AVAudioSession.sharedInstance().requestRecordPermission { [unowned self] allowed in
                DispatchQueue.main.async {
                    self.hasPermission = allowed
                }
            }
        default:
            break
        }
        result(hasPermission)
    }
    
    // 开始录音
    fileprivate func start(path: String, encoder: Int, bitRate: Int, samplingRate: Float, result: @escaping FlutterResult) {
        stopRecording()
        
        let settings: [String: Any] = [
            AVFormatIDKey: getEncoder(encoder),
            AVEncoderBitRateKey: bitRate,
            AVSampleRateKey: samplingRate,
            AVNumberOfChannelsKey: 2,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]
        
        let options: AVAudioSession.CategoryOptions = [.defaultToSpeaker, .allowBluetooth]
        
        DispatchQueue.global(qos: .background).async {
            do {
                try AVAudioSession.sharedInstance().setCategory(.playAndRecord, options: options)
                try AVAudioSession.sharedInstance().setActive(true)
                
                let url = URL(string: path) ?? URL(fileURLWithPath: path)
                self.audioRecorder = try AVAudioRecorder(url: url, settings: settings)
                self.audioRecorder?.delegate = self
                self.audioRecorder?.isMeteringEnabled = true
                self.audioRecorder?.record()
                
                self.isRecording = true
                self.isPaused = false
                
                DispatchQueue.main.async {
                    result(nil)
                }
            } catch {
                DispatchQueue.main.async {
                    result(FlutterError(code: "", message: "Failed to start recording", details: nil))
                }
            }
        }
    }
    
    // 停止录音
    fileprivate func stop(_ result: @escaping FlutterResult) {
        stopRecording()
        result(path)
    }
    
    // 暂停录音
    fileprivate func pause(_ result: @escaping FlutterResult) {
        audioRecorder?.pause()
        isPaused = true
        result(nil)
    }
    
    // 恢复录音
    fileprivate func resume(_ result: @escaping FlutterResult) {
        if isPaused {
            audioRecorder?.record()
            isPaused = false
        }
        result(nil)
    }
    
    // 获取振幅
    fileprivate func getAmplitude(_ result: @escaping FlutterResult) {
        var amp: [String: Float] = ["current": -160.0]
        
        if isRecording {
            audioRecorder?.updateMeters()
            if let current = audioRecorder?.averagePower(forChannel: 0) {
                if current > maxAmplitude {
                    maxAmplitude = current
                }
                amp["current"] = current
                amp["max"] = maxAmplitude
            }
        }
        
        result(amp)
    }
    
    // 停止录音并重置状态
    fileprivate func stopRecording() {
        audioRecorder?.stop()
        audioRecorder = nil
        isRecording = false
        isPaused = false
        maxAmplitude = -160.0
    }
    
    // 释放资源
    fileprivate func dispose(_ result: @escaping FlutterResult) {
        stopRecording()
        result(path)
    }
    
    // 获取编码器类型
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
            return Int(kAudioFormatOpus)
        default:
            return Int(kAudioFormatMPEG4AAC)
        }
    }
}
