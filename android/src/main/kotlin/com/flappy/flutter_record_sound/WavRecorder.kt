package com.flappy.flutter_record_sound

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.log10

class WavRecorder(private val outputFilePath: String) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private val bufferSize: Int = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private var maxAmplitude = -160.0

    fun startRecording() {
        if (isRecording) {
            Log.w(LOG_TAG, "Recording is already in progress.")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true
        isPaused = false

        Thread {
            writeAudioDataToFile()
        }.start()
    }

    fun pauseRecording() {
        if (!isRecording) {
            Log.w(LOG_TAG, "Cannot pause. Recording is not in progress.")
            return
        }
        isPaused = true
    }

    fun resumeRecording() {
        if (!isRecording) {
            Log.w(LOG_TAG, "Cannot resume. Recording is not in progress.")
            return
        }
        isPaused = false
    }

    fun stopRecording() {
        if (!isRecording) {
            Log.w(LOG_TAG, "Cannot stop. Recording is not in progress.")
            return
        }

        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        convertPcmToWav(outputFilePath)
    }

    fun getAmplitude(): Double {
        if (!isRecording) return -160.0

        val buffer = ByteArray(bufferSize)
        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0

        if (read > 0) {
            var maxAmplitude = 0
            for (i in buffer.indices step 2) {
                if (i + 1 < buffer.size) {
                    val amplitude = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                    maxAmplitude = maxOf(maxAmplitude, amplitude)
                }
            }
            return 20 * log10(maxAmplitude / 32768.0)
        }

        return -160.0
    }

    private fun writeAudioDataToFile() {
        val pcmFile = File(outputFilePath)
        val buffer = ByteArray(bufferSize)

        try {
            FileOutputStream(pcmFile).use { outputStream ->
                while (isRecording) {
                    if (!isPaused) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Error writing audio data to file: ${e.message}")
        }
    }

    private fun convertPcmToWav(pcmFilePath: String) {
        val pcmFile = File(pcmFilePath)
        val wavFile = if (pcmFilePath.endsWith(".pcm")) {
            File(pcmFilePath.replace(".pcm", ".wav"))
        } else {
            File("$pcmFilePath.wav")
        }

        try {
            val pcmData = pcmFile.readBytes()
            val wavData = createWavHeader(pcmData.size) + pcmData
            wavFile.writeBytes(wavData)
            pcmFile.delete()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Error converting PCM to WAV: ${e.message}")
        }
    }

    private fun createWavHeader(dataSize: Int): ByteArray {
        val totalSize = 36 + dataSize
        val byteRate = SAMPLE_RATE * 2

        return byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            (totalSize and 0xff).toByte(),
            ((totalSize shr 8) and 0xff).toByte(),
            ((totalSize shr 16) and 0xff).toByte(),
            ((totalSize shr 24) and 0xff).toByte(),
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
            'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(),
            16, 0, 0, 0,
            1, 0,
            1, 0,
            (SAMPLE_RATE and 0xff).toByte(),
            ((SAMPLE_RATE shr 8) and 0xff).toByte(),
            ((SAMPLE_RATE shr 16) and 0xff).toByte(),
            ((SAMPLE_RATE shr 24) and 0xff).toByte(),
            (byteRate and 0xff).toByte(),
            ((byteRate shr 8) and 0xff).toByte(),
            ((byteRate shr 16) and 0xff).toByte(),
            ((byteRate shr 24) and 0xff).toByte(),
            2, 0,
            16, 0,
            'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
            (dataSize and 0xff).toByte(),
            ((dataSize shr 8) and 0xff).toByte(),
            ((dataSize shr 16) and 0xff).toByte(),
            ((dataSize shr 24) and 0xff).toByte()
        )
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val LOG_TAG = "WavRecorder"
    }
}