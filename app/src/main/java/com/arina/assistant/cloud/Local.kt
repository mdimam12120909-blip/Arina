package com.arina.assistant.cloud

import android.content.Context
import android.content.SharedPreferences

/**
 * Keeps each account's personal data on the phone apart. Memory, chats, skills, tasks, routines, the person's own
 * keys and their preferences are stored under a name that includes the signed-in account's id, so a second account
 * on the same phone starts with nothing of the first one's. Nobody signed in ("guest") uses the plain names.
 * The account id is written to the "cloud" preferences at sign-in and removed at sign-out.
 */
object Local {
    private val PERSONAL = listOf("arina_tasks", "arina_skills", "arina_memory", "arina_chat", "arina_routines", "arina_keys", "arina_ui")

    private fun uid(c: Context): String =
        c.applicationContext.getSharedPreferences("cloud", Context.MODE_PRIVATE).getString("uid", "").orEmpty()

    /** The preferences file of [name] that belongs to whoever is signed in right now. */
    fun prefs(c: Context, name: String): SharedPreferences {
        val id = uid(c).filter { it.isLetterOrDigit() }
        return c.applicationContext.getSharedPreferences(if (id.isEmpty()) name else "${name}_u$id", Context.MODE_PRIVATE)
    }

    /**
     * The first account to sign in on a phone takes over what was stored before sign-in existed (it was this
     * person's own data); it is moved, not copied, so no later account or guest can ever see it. Runs once.
     */
    fun claimGuestData(c: Context, accountId: String) {
        val app = c.applicationContext
        val flag = app.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        if (flag.getBoolean("guest_claimed", false)) return
        val id = accountId.filter { it.isLetterOrDigit() }
        if (id.isEmpty()) return
        for (name in PERSONAL) {
            val from = app.getSharedPreferences(name, Context.MODE_PRIVATE)
            val to = app.getSharedPreferences("${name}_u$id", Context.MODE_PRIVATE)
            val all = from.all
            if (all.isEmpty()) continue
            val e = to.edit()
            for ((k, v) in all) when (v) {
                is String -> e.putString(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
            }
            e.apply()
            from.edit().clear().apply()
        }
        flag.edit().putBoolean("guest_claimed", true).apply()
    }

    /** Starts the app fresh, so nothing of the previous account is left in memory. */
    fun restartApp(c: Context) {
        val app = c.applicationContext
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        app.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
