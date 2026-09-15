package com.alad.persiandub

import android.content.Context
import android.media.*

class AudioPlayer(context: Context) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    fun start() {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).setOnAudioFocusChangeListener {}.build()
            manager.requestAudioFocus(focus!!)
        }
        val min = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(min * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
        track!!.play()
    }
    fun write(data: ByteArray) { track?.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING) }
    fun stop() { runCatching { track?.stop() }; track?.release(); track = null; if (android.os.Build.VERSION.SDK_INT >= 26) focus?.let { manager.abandonAudioFocusRequest(it) }; focus = null }
}
