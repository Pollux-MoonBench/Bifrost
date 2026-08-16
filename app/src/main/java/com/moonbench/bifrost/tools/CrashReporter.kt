package com.moonbench.bifrost.tools

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    private const val FILE_NAME = "bifrost-crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun existingReport(context: Context): File? =
        reportFile(context)?.takeIf { it.exists() && it.length() > 0 }

    fun clear(context: Context) {
        reportFile(context)?.takeIf { it.exists() }?.delete()
    }

    private fun reportFile(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val target = reportFile(context) ?: return
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        target.writeText(
            buildString {
                appendLine("Bifrost crash report")
                appendLine("time: $stamp")
                appendLine("version: $versionName")
                appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("build: ${Build.DISPLAY}")
                appendLine("thread: ${thread.name}")
                appendLine()
                append(stack.toString())
            }
        )
    }
}
