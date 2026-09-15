package com.alad.persiandub

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow

class DubbingService : Service() {
    companion object {
        const val ACTION_START = "com.alad.persiandub.START"
        const val ACTION_STOP = "com.alad.persiandub.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        val running = MutableStateFlow(false)
        val status = MutableStateFlow("Stopped")
    }
    private var capture: AudioCapture? = null
    private var player: AudioPlayer? = null
    private var gemini: GeminiLiveClient? = null
    private var projection: android.media.projection.MediaProjection? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(10, notification())
                val key = getSharedPreferences("settings", MODE_PRIVATE).getString("api_key", "") ?: ""
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (key.isBlank() || data == null || code == 0) { status.value = "API key or capture permission missing"; stopSelf(); return START_NOT_STICKY }
                startDubbing(key, code, data)
            }
            ACTION_STOP -> stopDubbing()
        }
        return START_NOT_STICKY
    }

    private fun startDubbing(key: String, resultCode: Int, data: Intent) {
        stopDubbing(false)
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = pm.getMediaProjection(resultCode, data)
        player = AudioPlayer(this).also { it.start() }
        gemini = GeminiLiveClient(key).also { client ->
            client.onStatus = { status.postValue(it) }
            client.onAudio = { player?.write(it) }
            client.connect()
        }
        capture = AudioCapture().also { c ->
            c.start(projection!!, applicationInfo.uid) { gemini?.sendAudio(it) }
        }
        running.value = true
        status.value = "Capturing YouTube audio…"
    }

    private fun stopDubbing(setStopped: Boolean = true) {
        capture?.stop(); capture = null
        gemini?.close(); gemini = null
        player?.stop(); player = null
        projection?.stop(); projection = null
        running.value = false
        if (setStopped) status.value = "Stopped"
        if (setStopped) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }

    override fun onDestroy() { stopDubbing(false); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification = NotificationCompat.Builder(this, "alad")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("ALAD Persian Live Dub")
        .setContentText("English → Persian live dubbing")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    override fun onCreate() {
        super.onCreate()
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("alad", "ALAD", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
