package com.alad.persiandub

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var pendingStart = false
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            val i = Intent(this, DubbingService::class.java).apply { action = DubbingService.ACTION_START; putExtra(DubbingService.EXTRA_RESULT_CODE, r.resultCode); putExtra(DubbingService.EXTRA_RESULT_DATA, r.data) }
            ContextCompat.startForegroundService(this, i)
        }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] != false) launchCapture()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            MaterialTheme {
                var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
                val running by DubbingService.running.collectAsState()
                val status by DubbingService.status.collectAsState()
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("ALAD Persian Live Dub", style = MaterialTheme.typography.headlineMedium)
                    Text("YouTube / any Android app → live Persian voice", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(apiKey, { apiKey = it; prefs.edit().putString("api_key", it).apply() }, Modifier.fillMaxWidth(), label = { Text("Gemini API key") }, singleLine = true)
                    Text("Status: $status")
                    Text("1. Enter your Gemini API key.\n2. Tap Start.\n3. Allow audio/screen capture.\n4. Open YouTube and play an English video.\n\nThe app captures playback audio, sends 16 kHz PCM to Gemini Live, and plays the 24 kHz Persian audio over the original app audio with ducking.")
                    Button(onClick = { if (running) stop() else start() }, Modifier.fillMaxWidth()) { Text(if (running) "Stop dubbing" else "Start dubbing") }
                    if (running) Text("Keep this foreground service running while YouTube is playing.")
                }
            }
        }
    }

    private fun start() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else launchCapture()
    }
    private fun launchCapture() {
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(pm.createScreenCaptureIntent())
    }
    private fun stop() { startService(Intent(this, DubbingService::class.java).setAction(DubbingService.ACTION_STOP)) }
}
