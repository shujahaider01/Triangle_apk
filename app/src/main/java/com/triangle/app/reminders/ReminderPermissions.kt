package com.triangle.app.reminders

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Exact-alarm permission (API 31+) helpers for the Create Task/Habit
 * screens' reminder toggle. Unlike POST_NOTIFICATIONS (a normal runtime
 * permission, already requested at app launch in MainActivity), there's no
 * permission-request dialog for SCHEDULE_EXACT_ALARM — the only UI is a
 * system Settings screen the user grants it from, so this is only checked
 * (and its settings screen only launched) at the moment the user actually
 * opts into a reminder, not unconditionally at app start.
 */
object ReminderPermissions {
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun exactAlarmSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
}
