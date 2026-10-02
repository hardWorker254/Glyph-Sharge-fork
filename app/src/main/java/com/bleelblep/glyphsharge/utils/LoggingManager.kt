package com.bleelblep.glyphsharge.utils

import android.annotation.SuppressLint
import android.content.Context
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
    private var isLoggingEnabled = false
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

    private fun createLogFiles() {
        val logsDir = File(context.getExternalFilesDir(null), "logs").apply {
            if (!exists()) mkdirs()
        }

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
