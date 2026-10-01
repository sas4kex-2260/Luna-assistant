package com.richa.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class VisionService(private val context: Context) {
    fun analyzeFile(file: java.io.File, prompt: String, onDone: (String) -> Unit) {
        Thread {
            try {
                val key = SecureStore.getGeminiKey(context) ?: run { onDone("Add a Gemini API key first."); return@Thread }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: throw IllegalStateException("Could not read image")
                val out = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role","user").put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(JSONObject().put("inline_data", JSONObject().put("mime_type","image/jpeg").put("data", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)))))))
                val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent").openConnection() as HttpURLConnection).apply { requestMethod="POST"; connectTimeout=12000; readTimeout=40000; doOutput=true; setRequestProperty("Content-Type","application/json"); setRequestProperty("x-goog-api-key",key) }
                conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
                val code=conn.responseCode
                if(code !in 200..299) throw IllegalStateException("Vision request failed ("+code+")")
                val raw=conn.inputStream.bufferedReader().use{it.readText()}
                onDone(JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text"))
            } catch(t:Throwable) { onDone("Vision failed: "+(t.message ?: "unknown error")) }
        }.start()
    }

    fun analyze(uri: Uri, prompt: String, onDone: (String) -> Unit) {
        Thread {
            try {
                val key = SecureStore.getGeminiKey(context) ?: run { onDone("Add a Gemini API key first."); return@Thread }
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException("Could not read image")
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role","user").put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(JSONObject().put("inline_data", JSONObject().put("mime_type","image/jpeg").put("data", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)))))))
                val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent").openConnection() as HttpURLConnection).apply {
                    requestMethod="POST"; connectTimeout=12000; readTimeout=40000; doOutput=true
                    setRequestProperty("Content-Type","application/json"); setRequestProperty("x-goog-api-key",key)
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
                val code=conn.responseCode
                val raw=(if(code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
                if(code !in 200..299) throw IllegalStateException("Vision request failed ("+code+")")
                onDone(JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text"))
            } catch(t:Throwable) { onDone("Vision failed: "+(t.message ?: "unknown error")) }
        }.start()
    }
}
