package com.alad.persiandub

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection

class AudioCapture {
    companion object { const val RATE = 16000; const val CHANNELS = AudioFormat.CHANNEL_IN_MONO; const val FORMAT = AudioFormat.ENCODING_PCM_16BIT }
    @Volatile private var running = false
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start(projection: MediaProjection, ownUid: Int, onChunk: (ByteArray) -> Unit) {
        if (running) return
        val min = AudioRecord.getMinBufferSize(RATE, CHANNELS, FORMAT).coerceAtLeast(RATE / 2)
        val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .apply { if (ownUid > 0) excludeUid(ownUid) }
            .build()
        val format = AudioFormat.Builder().setEncoding(FORMAT).setSampleRate(RATE).setChannelMask(CHANNELS).build()
        record = AudioRecord.Builder().setAudioFormat(format).setAudioPlaybackCaptureConfig(capture).setBufferSizeInBytes(min * 2).build()
        record!!.startRecording()
        running = true
        Thread {
            val buffer = ByteArray(min)
            while (running) {
                val n = record?.read(buffer, 0, buffer.size) ?: -1
                if (n > 0) onChunk(buffer.copyOf(n))
            }
        }.start()
    }

    fun stop() {
        running = false
        runCatching { record?.stop() }
        record?.release(); record = null
    }
}
