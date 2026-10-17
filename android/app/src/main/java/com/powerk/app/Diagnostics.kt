package com.powerk.app

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.PixelCopy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Sends logs, crashes, ANR/exit reasons, frame stats and occasional screenshots to the server so
 * problems can be inspected there. Nothing here may throw into the app.
 */
object Diag {
    private const val TAG = "Diag"
    private const val MAX_PENDING = 500
    private const val FLUSH_MS = 30_000L
    private val JSON_TYPE = "application/json".toMediaType()

    private lateinit var app: Application
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()

    private val lock = Any()
    private val pending = ArrayDeque<JSONObject>()
    private val crumbs = ArrayDeque<String>()
    private val lastSeen = HashMap<String, Long>()

    var enabled by mutableStateOf(true)
        private set
    var screenshots by mutableStateOf(true)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    @Volatile var route: String = ""
    @Volatile var linkState: String = ""

    private val device: String = "${Build.MANUFACTURER}-${Build.MODEL}".replace(' ', '_')
    private val version: String get() = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"

    fun init(application: Application) {
        app = application
        prefs = application.getSharedPreferences("powerk", Context.MODE_PRIVATE).also {
            enabled = it.getBoolean("diag_enabled", true)
            screenshots = it.getBoolean("diag_shots", true)
        }
        installCrashHandler()
        i("app", "start v$version sdk${Build.VERSION.SDK_INT} $device")
        scope.launch {
            runCatching { collectExitReasons() }.onFailure { Log.w(TAG, "exit reasons", it) }
            runCatching { uploadPendingCrashes() }.onFailure { Log.w(TAG, "pending crashes", it) }
        }
    }

    fun toggleEnabled(value: Boolean) { enabled = value; prefs?.edit()?.putBoolean("diag_enabled", value)?.apply() }
    fun toggleScreenshots(value: Boolean) { screenshots = value; prefs?.edit()?.putBoolean("diag_shots", value)?.apply() }

    fun d(tag: String, msg: String) = log("debug", tag, msg)
    fun i(tag: String, msg: String) = log("info", tag, msg)
    fun w(tag: String, msg: String) = log("warn", tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) = log("error", tag, if (t != null) "$msg: ${Log.getStackTraceString(t)}" else msg)

    private fun log(level: String, tag: String, msg: String) {
        when (level) {
            "error" -> Log.e(tag, msg)
            "warn" -> Log.w(tag, msg)
            else -> Log.i(tag, msg)
        }
        val now = System.currentTimeMillis()
        synchronized(lock) {
            crumbs.addLast("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(now)} $level/$tag $msg".take(300))
            while (crumbs.size > 80) crumbs.removeFirst()
            if (!enabled || level == "debug") return
            // A repeating message is reported once per 30 s.
            val key = "$tag|${msg.take(120)}"
            if (now - (lastSeen[key] ?: 0L) < 30_000L) return
            lastSeen[key] = now
            if (lastSeen.size > 400) lastSeen.clear()
            pending.addLast(JSONObject().put("ts", now).put("level", level).put("tag", tag).put("msg", msg.take(2000)))
            while (pending.size > MAX_PENDING) pending.removeFirst()
        }
    }

    /** Runs while the UI is alive; also flushes right after an error. */
    suspend fun runLoop() {
        while (true) {
            delay(FLUSH_MS)
            flush()
        }
    }

    fun flushSoon() { scope.launch { flush() } }

    private fun post(path: String, body: okhttp3.RequestBody): Boolean {
        if (!Server.configured) return false
        val request = Request.Builder().url(Server.base + "/api/diagnostics" + path).post(body)
            .apply { if (Server.token.isNotBlank()) header("X-Token", Server.token) }
            .build()
        return runCatching { client.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    private fun envelope(): JSONObject = JSONObject().put("source", "android").put("device", device).put("version", version)

    suspend fun flush(): Boolean = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext false
        val batch = synchronized(lock) { pending.toList().also { pending.clear() } }
        if (batch.isEmpty()) return@withContext true
        val payload = envelope().put("entries", JSONArray(batch)).toString()
        val ok = post("/logs", payload.toRequestBody(JSON_TYPE))
        if (ok) {
            status = "Sent ${batch.size} log lines at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(System.currentTimeMillis())}"
        } else synchronized(lock) {
            batch.asReversed().forEach { pending.addFirst(it) }
            while (pending.size > MAX_PENDING) pending.removeFirst()
            status = "Server unreachable; ${pending.size} lines queued"
        }
        ok
    }

    // ---- crashes -------------------------------------------------------------------------

    private fun crashDir() = File(app.filesDir, "diag").also { it.mkdirs() }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val report = envelope()
                    .put("summary", "${error.javaClass.simpleName}: ${error.message} (thread ${thread.name})")
                    .put("stack", Log.getStackTraceString(error))
                    .put("thread", thread.name)
                    .put("route", route)
                    .put("link", linkState)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("model", Build.MODEL)
                    .put("occurredAt", System.currentTimeMillis())
                    .put("breadcrumbs", JSONArray(synchronized(lock) { crumbs.toList() }))
                File(crashDir(), "crash-${System.currentTimeMillis()}.json").writeText(report.toString())
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun logcatTail(): String = runCatching {
        val proc = ProcessBuilder("logcat", "-d", "-t", "250", "-v", "time").redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().use { it.readText() }.takeLast(60_000)
    }.getOrDefault("")

    private suspend fun uploadPendingCrashes() {
        val files = crashDir().listFiles { f -> f.name.startsWith("crash-") }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) return
        val logcat = logcatTail()
        for (file in files) {
            val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: run { file.delete(); continue }
            json.put("logcat", logcat)
            // Keep the file until the server accepts it, so a crash made offline is not lost.
            if (post("/crash", json.toString().toRequestBody(JSON_TYPE))) file.delete() else break
        }
    }

    /** OS-reported exits (ANR, native crash, OEM kill) that the Java handler never sees. */
    private suspend fun collectExitReasons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val last = prefs?.getLong("diag_exit_ts", 0L) ?: 0L
        val exits = am.getHistoricalProcessExitReasons(app.packageName, 0, 8).filter { it.timestamp > last }
        if (exits.isEmpty()) return
        prefs?.edit()?.putLong("diag_exit_ts", exits.maxOf { it.timestamp })?.apply()
        for (info in exits) {
            val reason = when (info.reason) {
                ApplicationExitInfo.REASON_ANR -> "ANR"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
                ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
                else -> continue
            }
            val text = "process exit $reason: ${info.description.orEmpty()} importance=${info.importance} rss=${info.rss / 1024}KB"
            if (reason == "ANR" || reason == "NATIVE_CRASH" || reason == "INIT_FAILURE") {
                val trace = runCatching { info.traceInputStream?.bufferedReader()?.use { it.readText() }?.take(100_000) }.getOrNull()
                val report = envelope().put("summary", text).put("stack", trace.orEmpty()).put("kind", reason).put("occurredAt", info.timestamp)
                post("/crash", report.toString().toRequestBody(JSON_TYPE))
            } else {
                w("exit", text)
            }
        }
        flush()
    }

    // ---- frame jank ----------------------------------------------------------------------

    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameNs = 0L
    private var frames = 0
    private var slow = 0
    private var frozen = 0
    private var worstMs = 0L
    private var windowStart = 0L

    fun startFrameMonitor() {
        if (frameCallback != null) return
        lastFrameNs = 0L; frames = 0; slow = 0; frozen = 0; worstMs = 0L; windowStart = SystemClock.elapsedRealtime()
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameNs != 0L) {
                    val ms = (frameTimeNanos - lastFrameNs) / 1_000_000
                    frames++
                    if (ms > 32) slow++
                    if (ms > 100) frozen++
                    if (ms > worstMs) worstMs = ms
                }
                lastFrameNs = frameTimeNanos
                val now = SystemClock.elapsedRealtime()
                if (now - windowStart >= 30_000L) {
                    if (frames > 30) {
                        val line = "route=$route frames=$frames slow>32ms=$slow frozen>100ms=$frozen worst=${worstMs}ms"
                        if (frozen >= 3 || slow * 10 > frames) w("perf", line) else i("perf", line)
                    }
                    frames = 0; slow = 0; frozen = 0; worstMs = 0L; windowStart = now
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        frameCallback = cb
        Choreographer.getInstance().postFrameCallback(cb)
    }

    fun stopFrameMonitor() {
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
    }

    // ---- screenshots ---------------------------------------------------------------------

    private var lastShotAt = 0L
    private val lastShotByScreen = HashMap<String, Long>()
    private val startedAt = SystemClock.elapsedRealtime()

    /** Reused for every capture so a start-up screenshot never triggers a full-screen bitmap GC. */
    @Volatile private var freeBitmap: Bitmap? = null

    /** Captures only this app's own window (never other apps or system UI). */
    fun captureScreen(activity: Activity, screen: String, force: Boolean = false) {
        if (!enabled || !screenshots) return
        if (screen == "settings") return
        val now = SystemClock.elapsedRealtime()
        // Never capture during the first seconds: the alloc + PixelCopy wobbles the start-up frames.
        if (!force && (now - startedAt < 8_000L || now - lastShotAt < 20_000L || now - (lastShotByScreen[screen] ?: -300_000L) < 300_000L)) return
        val view = activity.window?.decorView ?: return
        if (view.width == 0 || view.height == 0) return
        lastShotAt = now
        lastShotByScreen[screen] = now
        val reusable = freeBitmap?.takeIf { !it.isRecycled && it.width == view.width && it.height == view.height }
        freeBitmap = null
        val bitmap = reusable ?: Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(activity.window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) scope.launch { upload(bitmap, screen) } else freeBitmap = bitmap
            }, Handler(Looper.getMainLooper()))
        }.onFailure { freeBitmap = bitmap; Log.w(TAG, "pixelcopy", it) }
    }

    private fun upload(source: Bitmap, screen: String) {
        val width = 540
        val scaled = if (source.width > width) Bitmap.createScaledBitmap(source, width, source.height * width / source.width, true) else source
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 62, out)
        if (scaled !== source) scaled.recycle()
        // Keep the full-size frame for the next capture instead of recycling it.
        freeBitmap = source
        val body = out.toByteArray().toRequestBody("image/jpeg".toMediaType())
        val q = "?device=${java.net.URLEncoder.encode(device, "UTF-8")}&screen=${java.net.URLEncoder.encode(screen, "UTF-8")}"
        post("/screenshot$q", body)
    }

    fun sendNow() {
        scope.launch {
            if (!flush() && status == null) status = "Server unreachable"
        }
    }
}