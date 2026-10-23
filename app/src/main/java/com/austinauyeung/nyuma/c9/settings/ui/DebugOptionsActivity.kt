package com.austinauyeung.nyuma.c9.settings.ui

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.austinauyeung.nyuma.c9.C9
import com.austinauyeung.nyuma.c9.core.ui.AppTheme

class DebugOptionsActivity : ComponentActivity() {
    private val createLogcatFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) writeLogcatTo(uri)
    }

    private fun requestLogcatExport() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        createLogcatFile.launch("C9-logcat-$timestamp.txt")
    }

    private fun writeLogcatTo(uri: Uri) {
        lifecycleScope.launch {
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val process = ProcessBuilder("logcat", "-d", "-v", "threadtime")
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val exitCode = process.waitFor()
                    val header = buildString {
                        appendLine("C9 Logcat Export")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("Captured: ${Date()}")
                        appendLine("Command: logcat -d -v threadtime")
                        appendLine("Exit code: $exitCode")
                        appendLine("Note: Android may restrict which system logs a normal app can read.")
                        appendLine("----- LOGCAT -----")
                    }
                    val stream = contentResolver.openOutputStream(uri)
                        ?: throw IOException("Could not open the selected file")
                    stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(header)
                        writer.write(output)
                        if (output.isNotEmpty() && !output.endsWith("\n")) writer.newLine()
                    }
                    if (exitCode == 0) "Logcat saved. Review it before sharing."
                    else "Logcat saved, but logcat exited with code $exitCode."
                } catch (e: Exception) {
                    "Failed to save logcat: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            Toast.makeText(this@DebugOptionsActivity, resultMessage, Toast.LENGTH_LONG).show()
        }
    }
    private lateinit var settingsState: SettingsState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = SettingsState.Factory(C9.getInstance().settingsRepository)
        settingsState = ViewModelProvider(this, factory)[SettingsState::class.java]

        setContent {
            AppTheme {
                DebugOptionsScreen(
                    settingsState = settingsState,
                    onExportLogcat = { requestLogcatExport() },
                    onNavigateBack = {
                        finish()
                    }
                )
            }
        }
    }
}