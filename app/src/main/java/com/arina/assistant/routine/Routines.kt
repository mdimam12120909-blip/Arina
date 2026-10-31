package com.arina.assistant.routine

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.arina.assistant.MainActivity
import com.arina.assistant.R
import com.arina.assistant.ui.ArinaEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** One routine: something Entrion reminds the user about at a set time on chosen days. */
data class Routine(
    val id: Long,
    val title: String,
    val hour: Int,
    val minute: Int,
    /** Bit 0 = Monday ... bit 6 = Sunday. 127 = every day. */
    val days: Int,
    val note: String,
    val enabled: Boolean
) {
    fun timeText() = "%02d:%02d".format(hour, minute)

    /** The next moment this routine fires after [from], or null if no day is chosen. */
    fun nextAfter(from: Long): Long? {
        if (days and 127 == 0) return null
        val c = Calendar.getInstance().apply { timeInMillis = from }
        for (offset in 0..7) {
            val t = Calendar.getInstance().apply {
                timeInMillis = from
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val dow = (t.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Calendar: Sunday=1 -> our Monday=0
            if (t.timeInMillis > from && (days shr dow) and 1 == 1) return t.timeInMillis
        }
        return null
    }
}

class RoutineStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("arina_routines", Context.MODE_PRIVATE)

    fun all(): List<Routine> = runCatching {
        val a = JSONArray(prefs.getString("list", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Routine(o.getLong("id"), o.getString("title"), o.getInt("hour"), o.getInt("minute"), o.optInt("days", 127), o.optString("note"), o.optBoolean("enabled", true))
        }.sortedWith(compareBy({ it.hour }, { it.minute }))
    }.getOrDefault(emptyList())

    fun save(list: List<Routine>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("hour", it.hour).put("minute", it.minute)
                .put("days", it.days).put("note", it.note).put("enabled", it.enabled))
        }
        prefs.edit().putString("list", a.toString()).apply()
    }

    fun get(id: Long) = all().firstOrNull { it.id == id }
}

object RoutineScheduler {
    private const val CHANNEL = "arina_routines"

    private fun pending(ctx: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, (id % Int.MAX_VALUE).toInt(), Intent(ctx, RoutineReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id))
    }

    /** Sets (or clears) the alarm for one routine. Exact when Android allows it, otherwise close to exact. */
    fun schedule(ctx: Context, r: Routine) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        cancel(ctx, r.id)
        if (!r.enabled) return
        val at = r.nextAfter(System.currentTimeMillis()) ?: return
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, r.id))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, r.id))
    }

    fun scheduleAll(ctx: Context) = RoutineStore(ctx).all().forEach { schedule(ctx, it) }

    fun notify(ctx: Context, title: String, text: String, id: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Entrion routines", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(open).setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(id, n) }
    }
}

/** An alarm went off: show the notification, let Entrion say it, and set the next alarm. */
class RoutineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1)
        val r = RoutineStore(context).get(id) ?: return
        RoutineScheduler.notify(context, r.title, r.note.ifBlank { "Time for: ${r.title}" }, (id % 100000).toInt())
        RoutineScheduler.schedule(context, r)
        ArinaEngine.get(context).routineDue(r)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) RoutineScheduler.scheduleAll(context)
    }
}
