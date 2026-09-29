package com.richa.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Locale

object AppLauncher {
    fun openByName(context: Context, spokenName: String): Boolean {
        val pm = context.packageManager
        val query = spokenName.lowercase(Locale.getDefault()).replace(" ", "")
        val activities = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        val match = activities.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase(Locale.getDefault()).replace(" ", "")
            label == query || label.contains(query) || query.contains(label) || fuzzy(label, query)
        } ?: return false
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }

    private fun fuzzy(label: String, query: String): Boolean {
        if (query.length < 3) return false
        val tokens = query.chunked(3)
        return tokens.count { label.contains(it) } >= (tokens.size + 1) / 2
    }
}
