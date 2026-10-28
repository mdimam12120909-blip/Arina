package com.arina.assistant.health

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import java.io.File

enum class Risk(val label: String) { High("HIGH"), Medium("MEDIUM"), Low("LOW") }

class Finding(val pkg: String, val label: String, val risk: Risk, val reasons: List<String>)

class DeviceStats(val ramFreeMb: Long, val ramTotalMb: Long, val storageFreeGb: Float, val storageTotalGb: Float, val battery: Int)

/**
 * A heuristic check of the installed apps: where each one came from and what powerful things it can do.
 * It is NOT an antivirus engine: it points out apps that deserve a second look. The user decides what to delete.
 */
object PhoneHealth {

    private val TRUSTED_STORES = setOf(
        "com.android.vending", "com.sec.android.app.samsungapps", "com.heytap.market", "com.oppo.market",
        "com.xiaomi.mipicks", "com.miui.packageinstaller.store", "com.amazon.venezia", "org.fdroid.fdroid",
        "com.huawei.appmarket", "com.vivo.appstore", "com.bbk.appstore", "com.google.android.feedback"
    )

    fun stats(ctx: Context): DeviceStats {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = StatFs(Environment.getDataDirectory().path)
        val bat = ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return DeviceStats(
            mi.availMem / 1_048_576, mi.totalMem / 1_048_576,
            st.availableBytes / 1_073_741_824f, st.totalBytes / 1_073_741_824f, bat
        )
    }

    private fun installer(ctx: Context, pkg: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(pkg).installingPackageName
        else @Suppress("DEPRECATION") ctx.packageManager.getInstallerPackageName(pkg)
    }.getOrNull()

    fun scan(ctx: Context): List<Finding> {
        val pm = ctx.packageManager
        val accessibility = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').mapNotNull { android.content.ComponentName.unflattenFromString(it)?.packageName }.toSet()
        val listeners = NotificationManagerCompat.getEnabledListenerPackages(ctx)
        val admins = runCatching {
            ctx.getSystemService(DevicePolicyManager::class.java).activeAdmins?.map { it.packageName }?.toSet()
        }.getOrNull().orEmpty()

        val packages: List<PackageInfo> =
            if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            else @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        val out = ArrayList<Finding>()
        for (p in packages) {
            val info = p.applicationInfo ?: continue
            val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            if (system || p.packageName == ctx.packageName) continue
            val perms = p.requestedPermissions?.toSet().orEmpty()
            val src = installer(ctx, p.packageName)
            val sideloaded = src == null || src !in TRUSTED_STORES

            var points = 0
            val why = ArrayList<String>()
            if (sideloaded) { points += 1; why += "Not installed from a known app store" }
            if (p.packageName in accessibility) { points += if (sideloaded) 4 else 2; why += "Has an Accessibility service switched on (can read and tap your screen)" }
            if (p.packageName in admins) { points += 3; why += "Is a device administrator" }
            if (p.packageName in listeners) { points += 2; why += "Can read all your notifications" }
            if ("android.permission.READ_SMS" in perms || "android.permission.RECEIVE_SMS" in perms) { points += if (sideloaded) 2 else 1; why += "Can read or receive your SMS (OTP codes)" }
            if ("android.permission.SYSTEM_ALERT_WINDOW" in perms && sideloaded) { points += 1; why += "Can draw over other apps" }
            if ("android.permission.REQUEST_INSTALL_PACKAGES" in perms && sideloaded) { points += 1; why += "Can install other apps" }
            if ("android.permission.READ_CALL_LOG" in perms && sideloaded) { points += 1; why += "Can read your call history" }
            if (sideloaded && pm.getLaunchIntentForPackage(p.packageName) == null) { points += 2; why += "Has no launcher icon (hidden app)" }

            if (why.isEmpty() || points < 2) continue
            val risk = when { points >= 6 -> Risk.High; points >= 3 -> Risk.Medium; else -> Risk.Low }
            out += Finding(p.packageName, info.loadLabel(pm).toString(), risk, why)
        }
        return out.sortedWith(compareBy({ it.risk.ordinal }, { it.label.lowercase() }))
    }

    fun uninstallIntent(pkg: String) = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appInfoIntent(pkg: String) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Android does not let one app clear another app's cache, so this cleans AriAuri's own. Returns bytes freed. */
    fun clearOwnCache(ctx: Context): Long {
        var freed = 0L
        fun wipe(f: File?) {
            if (f == null || !f.exists()) return
            if (f.isDirectory) f.listFiles()?.forEach { wipe(it) }
            freed += f.length()
            f.delete()
        }
        ctx.cacheDir.listFiles()?.forEach { wipe(it) }
        ctx.externalCacheDir?.listFiles()?.forEach { wipe(it) }
        return freed
    }
}
