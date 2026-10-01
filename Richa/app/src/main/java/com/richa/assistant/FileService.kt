package com.richa.assistant
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStreamReader

class FileService(private val context: Context) {
    fun readText(uri: Uri, maxChars: Int = 120000): String {
        val name = uri.lastPathSegment ?: "file"
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { InputStreamReader(it).readText() } }.getOrNull()
            ?: return "Unable to read $name."
        return "FILE: $name\n\n" + text.take(maxChars)
    }
    fun copyToPrivate(uri: Uri): File? = runCatching {
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val target = File(dir, "attachment_" + System.currentTimeMillis())
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        target
    }.getOrNull()
}
