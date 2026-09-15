package com.alad.persiandub

import android.util.Base64
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Direct Gemini Live WebSocket client using the current v1beta wire format. */
class GeminiLiveClient(private val apiKey: String) {
    companion object {
        private const val URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val MODEL = "models/gemini-3.1-flash-live-preview"
        // Shared across connect/reconnect cycles instead of creating a new
        // OkHttpClient (and its thread/connection pools) on every open().
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        }
    }
    var onAudio: ((ByteArray) -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var ws: WebSocket? = null
    @Volatile private var stopped = false
    @Volatile private var ready = false
    private var handle: String? = null
    private val pending = ArrayDeque<ByteArray>()

    fun connect() {
        stopped = false
        open()
    }

    private fun open() {
        val request = Request.Builder().url("$URL?key=$apiKey").build()
        ws = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                ready = false
                val setup = JSONObject().apply {
                    put("setup", JSONObject().apply {
                        put("model", MODEL)
                        put("generationConfig", JSONObject().apply { put("responseModalities", org.json.JSONArray().put("AUDIO")) })
                        put("systemInstruction", JSONObject().apply {
                            put("parts", org.json.JSONArray().put(JSONObject().put("text", "You are a real-time dubbing engine. Listen to the incoming spoken audio, translate its meaning from English into natural Persian (Farsi), and speak the Persian translation immediately. Do not answer the speaker, explain, summarize, or use English. Output only Persian speech.")))
                        })
                        put("sessionResumption", JSONObject().apply { put("handle", handle ?: JSONObject.NULL) })
                    })
                }
                webSocket.send(setup.toString())
                onStatus?.invoke("Connecting to Gemini")
            }

            override fun onMessage(webSocket: WebSocket, text: String) { parse(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { parse(bytes.utf8()) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready = false
                onStatus?.invoke("Gemini error: ${t.message ?: "connection failed"}")
                retry()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ready = false
                if (!stopped) retry()
            }
        })
    }

    private fun parse(text: String) {
        runCatching {
            val j = JSONObject(text)
            j.optJSONObject("sessionResumptionUpdate")?.let { u ->
                if (u.has("newHandle")) handle = u.optString("newHandle").takeIf { it.isNotBlank() }
            }
            if (j.has("setupComplete")) {
                ready = true
                onStatus?.invoke("Gemini ready — Persian dubbing on")
                while (pending.isNotEmpty()) sendNow(pending.removeFirst())
            }
            j.optJSONObject("serverContent")?.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val data = part.optJSONObject("inlineData")?.optString("data") ?: continue
                    if (data.isNotEmpty()) onAudio?.invoke(Base64.decode(data, Base64.DEFAULT))
                }
            }
            j.optJSONObject("error")?.let { onStatus?.invoke("Gemini error: ${it.optString("message", "unknown error")}") }
        }
    }

    fun sendAudio(pcm: ByteArray) {
        if (stopped) return
        if (!ready) {
            synchronized(pending) { if (pending.size < 5) pending.addLast(pcm.copyOf()) }
            return
        }
        sendNow(pcm)
    }

    private fun sendNow(pcm: ByteArray) {
        val msg = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("audio", JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
                })
            })
        }
        ws?.send(msg.toString())
    }

    private fun retry() {
        if (!stopped) executor.schedule({ if (!stopped) open() }, 1500, TimeUnit.MILLISECONDS)
    }

    fun close() {
        stopped = true; ready = false; pending.clear(); ws?.close(1000, "user stop"); ws = null; executor.shutdownNow(); onStatus?.invoke("Stopped")
    }
}
