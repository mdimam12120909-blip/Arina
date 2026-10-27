package com.arina.assistant.device

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs shell commands inside Termux through its RUN_COMMAND service. Termux needs
 * `allow-external-apps=true` in ~/.termux/termux.properties, and the user must grant AriAuri the
 * "Run commands in Termux environment" permission. Blocking: call off the main thread.
 */
object TermuxBridge {
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val PKG = "com.termux"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION = "com.termux.RUN_COMMAND"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    private const val HOME = "/data/data/com.termux/files/home"
    const val EXTRA_ID = "ariauri_exec_id"

    private val counter = AtomicInteger(0)
    private val waiting = ConcurrentHashMap<Int, CompletableFuture<String>>()

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun run(ctx: Context, command: String): String {
        if (!installed(ctx)) return "ERROR: Termux is not installed."
        if (ctx.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return "ERROR: AriAuri has no Termux permission. Ask the user to turn on 'Termux commands' in the Settings tab."
        }
        val id = counter.incrementAndGet()
        val future = CompletableFuture<String>()
        waiting[id] = future
        val result = Intent(ctx, TermuxResultReceiver::class.java).putExtra(EXTRA_ID, id)
        val pending = PendingIntent.getBroadcast(ctx, id, result, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val intent = Intent(ACTION).setClassName(PKG, SERVICE)
            .putExtra("com.termux.RUN_COMMAND_PATH", BASH)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            .putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "AriAuri")
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending)
        try {
            ctx.startService(intent)
        } catch (e: SecurityException) {
            waiting.remove(id)
            return "ERROR: Termux refused. In Termux run: mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties  then restart Termux. (${e.message})"
        } catch (e: Exception) {
            waiting.remove(id)
            return "ERROR: could not reach Termux: ${e.message}"
        }
        return try {
            future.get(60, TimeUnit.SECONDS)
        } catch (e: Exception) {
            "OK: the command was sent to Termux but no result came back within 60 seconds."
        } finally {
            waiting.remove(id)
        }
    }

    fun deliver(id: Int, intent: Intent) {
        val b = intent.getBundleExtra("result")
        val text = if (b == null) "ERROR: Termux sent no result."
        else {
            val err = b.getInt("err", -1)
            val msg = b.getString("errmsg").orEmpty()
            val out = b.getString("stdout").orEmpty().trim().take(3000)
            val errOut = b.getString("stderr").orEmpty().trim().take(1500)
            val code = if (b.containsKey("exitCode")) b.getInt("exitCode") else -1
            if (err != -1 && err != 0) "ERROR: Termux error $err: ${msg.take(300)}"
            else buildString {
                append(if (code == 0) "OK: exit code 0" else "ERROR: exit code $code")
                if (out.isNotEmpty()) append("\nOUTPUT:\n").append(out)
                if (errOut.isNotEmpty()) append("\nERRORS:\n").append(errOut)
            }
        }
        waiting[id]?.complete(text)
    }
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(TermuxBridge.EXTRA_ID, -1)
        if (id >= 0) TermuxBridge.deliver(id, intent)
    }
}
