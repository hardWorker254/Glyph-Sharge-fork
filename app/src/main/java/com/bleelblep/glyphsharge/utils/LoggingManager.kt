package com.bleelblep.glyphsharge.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("StaticFieldLeak")
object LoggingManager {
    /**
     * Whether the log file is written.
     *
     * On, and not behind a switch that nothing sets: the file is the only
     * diagnostic this app has on a phone where logcat cannot be read, and a
     * log that is never written is not a log. The writes are event-level — a
     * session opening, a device registering, a trigger firing — not per frame,
     * so the cost is a handful of lines per session.
     */
    private var isLoggingEnabled = true
    private val logMutex = Mutex()
    @SuppressLint("ConstantLocale")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    @SuppressLint("ConstantLocale")
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    private lateinit var context: Context
    private lateinit var logFile: File
    private lateinit var c15LogFile: File

    // Pre-compiled regex patterns for C15-related checks
    private val c15RelatedPatterns = setOf(
        "c15", "c14", "channel 17", "channel 16",
        "hardware channel 17", "hardware channel 16",
        "final state", "breathing", "exhale", "glyph",
        "frame", "session", "toggle", "animate",
        "builder", "service", "register", "turnoff",
        "brightness", "channel", "isolation", "excluded",
        "dim state", "hold phase", "sdk",
    )

    fun initialize(context: Context) {
        this.context = context
        createLogFiles()
    }

    /** How many session logs are kept on disk. */
    private const val MAX_LOG_FILES = 10

    private fun createLogFiles() {
        val logsDir = File(context.getExternalFilesDir(null), "logs").apply {
            if (!exists()) mkdirs()
        }

        // One file per launch, so the folder would otherwise grow for as long
        // as the app is installed. The newest few are what a bug report needs;
        // the rest is history nobody reads.
        logsDir.listFiles { file -> file.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOG_FILES)
            ?.forEach { it.delete() }

        val timestamp = fileDateFormat.format(Date())
        logFile = File(logsDir, "glyphzen_debug_$timestamp.log")
        c15LogFile = File(logsDir, "glyphzen_c15_debug_$timestamp.log")

        // Create files if they don't exist
        if (!logFile.exists()) {
            logFile.createNewFile()
            writeToFile(logFile, "=== GlyphZen Debug Log Started at ${dateFormat.format(Date())} ===\n")
        }
        if (!c15LogFile.exists()) {
            c15LogFile.createNewFile()
            writeToFile(c15LogFile, "=== GlyphZen C15 Debug Log Started at ${dateFormat.format(Date())} ===\n")
        }
    }

    fun log(tag: String, message: String) {
        if (!isLoggingEnabled) return

        CoroutineScope(Dispatchers.IO).launch {
            logMutex.withLock {
                val timestamp = dateFormat.format(Date())
                val logEntry = "[$timestamp] [$tag] $message\n"

                // Write to main log
                writeToFile(logFile, logEntry)

                // Write to C15 log if relevant
                if (isC15Related(tag, message)) {
                    writeToFile(c15LogFile, logEntry)
                }

                // Also log to Android logcat
                Log.d("GlyphZen_$tag", message)
            }
        }
    }

    private fun isC15Related(tag: String, message: String): Boolean {
        val lowerMessage = message.lowercase()
        val lowerTag = tag.lowercase()

        return c15RelatedPatterns.any { pattern ->
            lowerMessage.contains(pattern) || lowerTag.contains(pattern)
        }
    }

    fun logSDKOperation(operation: String, details: String) {
        log("SDK", "$operation: $details")
    }

    fun logSessionState(state: String, details: String = "") {
        log("SESSION", "$state${if (details.isNotEmpty()) " - $details" else ""}")
    }

    private fun writeToFile(file: File, content: String) {
        try {
            FileWriter(file, true).use { writer ->
                writer.write(content)
                writer.flush()
            }
        } catch (e: Exception) {
            Log.e("LoggingManager", "Error writing to log file: ${e.message}")
        }
    }

    /**
     * How an export ended.
     *
     * Three answers, because the user's next move differs: there is nothing to
     * export, it worked, or the place they chose could not be written.
     */
    enum class ExportResult {
        /** The log was written. */
        DONE,

        /** There was nothing to write yet. */
        EMPTY,

        /** The chosen place could not be written. */
        FAILED,
    }

    /**
     * Writes the assembled log to [uri].
     *
     * The one way to get the log off the phone: it is a file in the app's own
     * directory, and the app cannot read logcat on a phone nobody can attach
     * a shell to. The user picks a place — a file in Downloads, something they
     * can share from there — and the log goes there.
     */
    fun exportTo(uri: Uri): ExportResult {
        val content = exportLogs()
        if (content.isBlank()) return ExportResult.EMPTY

        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray())
                stream.flush()
            } ?: error("The chosen location could not be opened for writing")
            ExportResult.DONE
        } catch (e: Exception) {
            Log.e("LoggingManager", "Could not write the logs to $uri", e)
            ExportResult.FAILED
        }
    }

    fun exportLogs(): String {
        return try {
            val logContent = StringBuilder()

            // Read main log file
            if (logFile.exists()) {
                logContent.append("=== Main Debug Log ===\n")
                logContent.append(logFile.readText())
                logContent.append("\n")
            }

            // Read C15 log file
            if (c15LogFile.exists()) {
                logContent.append("=== C15 Debug Log ===\n")
                logContent.append(c15LogFile.readText())
            }

            logContent.toString()
        } catch (e: Exception) {
            "Error exporting logs: ${e.message}"
        }
    }
}
