package com.richa.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

object AppLauncher {
    private fun normalize(value: String): String =
        value.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]+"), "")

    fun openByName(context: Context, spokenName: String): Boolean {
        val pm = context.packageManager
        val query = normalize(spokenName.removePrefix("the ").removeSuffix(" app").trim())
        if (query.isBlank()) return false

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        val match = activities.firstOrNull {
            normalize(it.loadLabel(pm).toString()) == query
        } ?: activities.map { info ->
            val label = normalize(info.loadLabel(pm).toString())
            val pkg = normalize(info.activityInfo.packageName.substringAfterLast('.'))
            val score = when {
                label.contains(query) -> 90
                query.contains(label) && label.length >= 3 -> 85
                pkg == query -> 80
                pkg.contains(query) -> 70
                fuzzy(label, query) -> 50
                else -> 0
            }
            info to score
        }.filter { it.second > 0 }.maxByOrNull { it.second }?.first ?: return false

        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }

    private fun fuzzy(label: String, query: String): Boolean {
        if (query.length < 3 || label.length < 3) return false
        val tokens = query.chunked(3)
        return tokens.count { label.contains(it) } >= (tokens.size + 1) / 2
    }
}
