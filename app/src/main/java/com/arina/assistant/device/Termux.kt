package com.arina.assistant.device

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs one shell command in Termux through its RUN_COMMAND service (documented in the Termux wiki).
 * Needs: Termux installed, allow-external-apps=true in ~/.termux/termux.properties, and this app's
 * "Run commands in Termux" permission.
 */
object Termux {
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val PKG = "com.termux"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    private const val HOME = "/data/data/com.termux/files/home"

    private class Waiter { val latch = CountDownLatch(1); @Volatile var text = "" }
    private val waiting = ConcurrentHashMap<Int, Waiter>()
    private val ids = AtomicInteger(5000)

    fun installed(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(PKG, 0); true
    }.getOrDefault(false)

    fun run(ctx: Context, command: String): String {
        if (!installed(ctx)) return "ERROR: Termux is not installed."
        if (ctx.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return "ERROR: this app does not have the 'Run commands in Termux' permission. Ask the user to allow it in the Settings tab."
        }
        val id = ids.incrementAndGet()
        val waiter = Waiter().also { waiting[id] = it }
        val result = Intent(ctx, TermuxResultReceiver::class.java).putExtra("id", id)
        val flags = PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(ctx, id, result, flags)
        val intent = Intent()
            .setClassName(PKG, "com.termux.app.RunCommandService")
            .setAction("com.termux.RUN_COMMAND")
            .putExtra("com.termux.RUN_COMMAND_PATH", BASH)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
        try {
            ctx.startService(intent)
        } catch (e: Exception) {
            waiting.remove(id)
            return "ERROR: could not reach Termux: ${e.message}. Termux needs allow-external-apps=true in ~/.termux/termux.properties."
        }
        if (!waiter.latch.await(60, TimeUnit.SECONDS)) {
            waiting.remove(id)
            return "ERROR: Termux did not answer within 60 seconds (the command may still be running)."
        }
        return waiter.text
    }

    internal fun deliver(id: Int, intent: Intent) {
        val w = waiting.remove(id) ?: return
        val b = intent.getBundleExtra("result")
        w.text = if (b == null) "ERROR: Termux returned no result."
        else {
            val out = b.getString("stdout").orEmpty().trim()
            val err = b.getString("stderr").orEmpty().trim()
            val code = b.getInt("exitCode", -1)
            val internal = b.getString("errmsg").orEmpty()
            buildString {
                append(if (code == 0) "OK: exit code 0\n" else "ERROR: exit code $code ${internal}\n")
                if (out.isNotEmpty()) append("STDOUT:\n").append(out.takeLast(3000)).append('\n')
                if (err.isNotEmpty()) append("STDERR:\n").append(err.takeLast(1500))
            }
        }
        w.latch.countDown()
    }
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Termux.deliver(intent.getIntExtra("id", -1), intent)
    }
}
