package com.richa.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

object AppLauncher {
    private fun normalize(value: String): String = value.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]+"), "")

    fun installedLabels(context: Context): List<String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).map { it.loadLabel(pm).toString() }.distinct().sorted()
    }

    fun openByName(context: Context, spokenName: String): Boolean {
        val pm = context.packageManager
        val raw = spokenName.trim().lowercase(Locale.getDefault()).removePrefix("the ").removeSuffix(" application").removeSuffix(" app").trim()
        val query = normalize(raw)
        if (query.isBlank()) return false
        val aliases = mapOf(
            "google" to listOf("com.google.android.googlequicksearchbox", "com.google.android.googlequicksearchbox:search"),
            "googlechrome" to listOf("com.android.chrome"),
            "chrome" to listOf("com.android.chrome"),
            "playstore" to listOf("com.android.vending"),
            "youtube" to listOf("com.google.android.youtube"),
            "gmail" to listOf("com.google.android.gm"),
            "maps" to listOf("com.google.android.apps.maps"),
            "photos" to listOf("com.google.android.apps.photos"),
            "whatsapp" to listOf("com.whatsapp"),
            "instagram" to listOf("com.instagram.android"),
            "facebook" to listOf("com.facebook.katana"),
            "spotify" to listOf("com.spotify.music")
        )
        for (pkg in aliases[query].orEmpty()) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launch); return true }
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        val match = activities.firstOrNull { normalize(it.loadLabel(pm).toString()) == query } ?: activities.map { info ->
            val label = normalize(info.loadLabel(pm).toString())
            val pkg = normalize(info.activityInfo.packageName.substringAfterLast('.'))
            val score = when { label == query -> 100; label.contains(query) -> 90; query.contains(label) && label.length >= 3 -> 85; pkg == query -> 80; pkg.contains(query) -> 70; fuzzy(label, query) -> 50; else -> 0 }
            info to score
        }.filter { it.second > 0 }.maxByOrNull { it.second }?.first ?: return false
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launch); return true
    }

    private fun fuzzy(label: String, query: String): Boolean {
        if (query.length < 3 || label.length < 3) return false
        val tokens = query.chunked(3)
        return tokens.count { label.contains(it) } >= (tokens.size + 1) / 2
    }
}