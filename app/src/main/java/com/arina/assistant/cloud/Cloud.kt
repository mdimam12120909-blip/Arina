package com.arina.assistant.cloud

import android.content.Context
import com.arina.assistant.Defaults
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Entrion's cloud account (Supabase): Google sign-in, the settings the admin controls, and who the admin is.
 * Plain REST calls over OkHttp. Every function here blocks, so call them off the main thread.
 *
 * What a person can see is decided by the database itself (row level security), not by this app:
 * the admin check below only decides which screen to show.
 */
class Cloud(private val ctx: Context) {

    companion object {
        private val JSON = "application/json".toMediaType()

        /** A setting the admin published (voice IDs, instructions), as last downloaded; [fallback] if never downloaded. */
        fun cfg(c: Context, key: String, fallback: String): String =
            c.getSharedPreferences("cloud_cfg", Context.MODE_PRIVATE).getString(key, null)?.takeIf { it.isNotBlank() } ?: fallback
    }

    private val prefs = ctx.getSharedPreferences("cloud", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val signedIn: Boolean get() = prefs.getString("refresh", "").orEmpty().isNotEmpty()
    val name: String get() = prefs.getString("name", "").orEmpty()
    val email: String get() = prefs.getString("email", "").orEmpty()
    val uid: String get() = prefs.getString("uid", "").orEmpty()

    // ---- account -----------------------------------------------------------------------------

    /** Exchanges the ID token Google gave the app for an Entrion session. */
    fun signInWithGoogle(idToken: String) {
        val body = JSONObject().put("provider", "google").put("id_token", idToken)
        val (code, text) = raw("POST", "/auth/v1/token?grant_type=id_token", body.toString(), bearer = null)
        if (code !in 200..299) throw IOException("Sign-in was refused (HTTP $code): ${message(text)}")
        saveSession(JSONObject(text))
    }

    fun signOut() {
        prefs.edit().clear().apply()
        ctx.getSharedPreferences("cloud_cfg", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun saveSession(o: JSONObject) {
        val user = o.optJSONObject("user")
        val meta = user?.optJSONObject("user_metadata")
        val edit = prefs.edit()
            .putString("access", o.getString("access_token"))
            .putString("refresh", o.getString("refresh_token"))
            .putLong("expires", System.currentTimeMillis() / 1000 + o.optLong("expires_in", 3600))
        if (user != null) {
            edit.putString("email", user.optString("email"))
            edit.putString("uid", user.optString("id"))
            edit.putString("name", meta?.optString("full_name")?.takeIf { it.isNotBlank() } ?: meta?.optString("name").orEmpty())
        }
        edit.apply()
    }

    /** A valid access token, refreshed first if it is about to expire. Null when nobody is signed in. */
    fun accessToken(): String? = runCatching { token() }.getOrNull()

    private fun token(): String? {
        val refresh = prefs.getString("refresh", "").orEmpty()
        if (refresh.isEmpty()) return null
        if (System.currentTimeMillis() / 1000 < prefs.getLong("expires", 0) - 60) return prefs.getString("access", null)
        val (code, text) = raw("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh).toString(), bearer = null)
        if (code in 200..299) { saveSession(JSONObject(text)); return prefs.getString("access", null) }
        if (code == 400 || code == 401) prefs.edit().clear().apply()   // the session is gone: ask to sign in again
        throw IOException("Could not renew the session (HTTP $code).")
    }

    // ---- settings ----------------------------------------------------------------------------

    /** Downloads the admin's published settings and keeps them for offline use. */
    fun fetchConfig() {
        val (code, text) = raw("GET", "/rest/v1/app_config?select=key,value", null, bearer = token())
        if (code !in 200..299) throw IOException("Could not load settings (HTTP $code): ${message(text)}")
        val rows = JSONArray(text)
        val edit = ctx.getSharedPreferences("cloud_cfg", Context.MODE_PRIVATE).edit()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            edit.putString(r.getString("key"), r.get("value").toString())
        }
        // Facts the admin added for every user; kept as one text for the system prompt. The table may not exist yet.
        runCatching {
            val (c2, t2) = raw("GET", "/rest/v1/global_facts?select=fact&order=id", null, bearer = token())
            if (c2 in 200..299) {
                val a = JSONArray(t2)
                edit.putString("global_facts", (0 until a.length()).joinToString("\n") { "- " + a.getJSONObject(it).getString("fact") })
            }
        }
        edit.apply()
    }

    fun isAdmin(): Boolean {
        val t = token() ?: return false
        val (code, text) = raw("POST", "/rest/v1/rpc/is_admin", "{}", bearer = t)
        return code in 200..299 && text.trim() == "true"
    }

    /** Admin only: the database refuses this for anyone else. */
    fun saveConfig(key: String, value: String) = upsert("app_config", key, value)

    fun saveSecret(key: String, value: String) = upsert("app_secrets", key, value)

    /** Admin only: secrets are never readable by other people. */
    fun secretKeys(): Set<String> {
        val t = token() ?: return emptySet()
        val (code, text) = raw("GET", "/rest/v1/app_secrets?select=key", null, bearer = t)
        if (code !in 200..299) return emptySet()
        val rows = JSONArray(text)
        return (0 until rows.length()).map { rows.getJSONObject(it).getString("key") }.toSet()
    }

    /** Admin only: the last four characters of a stored secret, or null if there is none. */
    fun secretTail(key: String): String? {
        val t = token() ?: return null
        val (code, text) = raw("GET", "/rest/v1/app_secrets?select=value&key=eq.$key", null, bearer = t)
        if (code !in 200..299) throw IOException("HTTP $code: ${message(text)}")
        val a = JSONArray(text)
        if (a.length() == 0) return null
        return a.getJSONObject(0).getString("value").takeLast(4)
    }

    private fun upsert(table: String, key: String, value: String) {
        val t = token() ?: throw IOException("Sign in first.")
        val row = JSONArray().put(JSONObject().put("key", key).put("value", value))
        val (code, text) = raw("POST", "/rest/v1/$table?on_conflict=key", row.toString(), bearer = t, prefer = "resolution=merge-duplicates,return=minimal")
        if (code !in 200..299) throw IOException("Could not save (HTTP $code): ${message(text)}")
    }

    /** Saves the person's first-run survey answers on their own row (the database lets nobody else touch it). */
    fun saveOnboarding(found: Int, uses: String) {
        val t = token() ?: throw IOException("Sign in first.")
        val id = uid.ifBlank { throw IOException("Sign in first.") }
        val row = JSONArray().put(JSONObject().put("user_id", id).put("found", found).put("uses", uses).put("updated_at", java.time.Instant.now().toString()))
        val (code, text) = raw("POST", "/rest/v1/onboarding_answers?on_conflict=user_id", row.toString(), bearer = t, prefer = "resolution=merge-duplicates,return=minimal")
        if (code !in 200..299) throw IOException("Could not save (HTTP $code): ${message(text)}")
    }

    // ---- admin tools (the database refuses all of these for anyone who is not the admin) ------------

    class Fact(val id: Long, val text: String)
    class User(val id: String, val email: String, val name: String, val created: String, val lastSeen: String, val banned: Boolean)

    private fun adminCall(method: String, path: String, body: String? = null, prefer: String? = null): String {
        val t = token() ?: throw IOException("Sign in first.")
        val (code, text) = raw(method, path, body, bearer = t, prefer = prefer)
        if (code !in 200..299) throw IOException("Not done (HTTP $code): ${message(text)}")
        return text
    }

    fun facts(): List<Fact> {
        val a = JSONArray(adminCall("GET", "/rest/v1/global_facts?select=id,fact&order=id.desc"))
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> Fact(o.getLong("id"), o.getString("fact")) } }
    }

    fun addFact(text: String) { adminCall("POST", "/rest/v1/global_facts", JSONObject().put("fact", text.trim()).toString(), "return=minimal") }

    fun deleteFact(id: Long) { adminCall("DELETE", "/rest/v1/global_facts?id=eq.$id") }

    fun users(): List<User> {
        val a = JSONArray(adminCall("POST", "/rest/v1/rpc/admin_list_users", "{}"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            User(o.getString("id"), o.optString("email"), o.optString("name"), o.optString("created_at").take(10),
                o.optString("last_sign_in_at").takeIf { s -> s != "null" }.orEmpty().take(10), o.optBoolean("banned"))
        }
    }

    fun setBan(id: String, ban: Boolean) { adminCall("POST", "/rest/v1/rpc/admin_set_ban", JSONObject().put("target", id).put("ban", ban).toString()) }

    fun deleteUser(id: String) { adminCall("POST", "/rest/v1/rpc/admin_delete_user", JSONObject().put("target", id).toString()) }

    /** total, today, week, active_week, banned */
    fun stats(): JSONObject = JSONObject(adminCall("POST", "/rest/v1/rpc/admin_stats", "{}"))

    // ---- app updates ---------------------------------------------------------------------------

    class Release(val id: Long, val build: Int, val version: String, val url: String, val notes: String, val force: Boolean, val active: Boolean, val date: String)

    private fun release(o: JSONObject) = Release(
        o.optLong("id"), o.getInt("build"), o.optString("version_name"), o.getString("apk_url"), o.optString("notes"),
        o.optBoolean("must_update"), o.optBoolean("active", true), o.optString("created_at").take(10)
    )

    /** The newest published release, for any user (signed in or not). Null when there is none or the phone is offline. */
    fun latestRelease(): Release? {
        val (code, text) = raw("GET", "/rest/v1/app_releases?select=*&active=eq.true&order=build.desc&limit=1", null, bearer = token())
        if (code !in 200..299) return null
        val a = JSONArray(text)
        return if (a.length() == 0) null else release(a.getJSONObject(0))
    }

    /** Admin only: every release ever published, newest first. */
    fun releases(): List<Release> {
        val a = JSONArray(adminCall("GET", "/rest/v1/app_releases?select=*&order=build.desc"))
        return (0 until a.length()).map { release(a.getJSONObject(it)) }
    }

    fun publishRelease(build: Int, version: String, url: String, notes: String, force: Boolean) {
        val o = JSONObject().put("build", build).put("version_name", version).put("apk_url", url)
            .put("notes", notes.trim()).put("must_update", force).put("active", true)
        adminCall("POST", "/rest/v1/app_releases?on_conflict=build", o.toString(), "resolution=merge-duplicates,return=minimal")
        audit("publish_update", "build $build" + if (force) " (forced)" else "")
    }

    fun setReleaseActive(id: Long, build: Int, active: Boolean) {
        adminCall("PATCH", "/rest/v1/app_releases?id=eq.$id", JSONObject().put("active", active).toString(), "return=minimal")
        audit(if (active) "restore_update" else "withdraw_update", "build $build")
    }

    /** Admin only: uploads the chosen Entrion.apk to the public "updates" storage and returns the link users will download from. */
    fun uploadApk(file: java.io.File, build: Int): String {
        val t = token() ?: throw IOException("Sign in first.")
        val path = "build-$build/Entrion.apk"
        val client = http.newBuilder().writeTimeout(180, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
        val req = Request.Builder().url(Defaults.SUPABASE_URL + "/storage/v1/object/updates/" + path)
            .header("apikey", Defaults.SUPABASE_KEY).header("Authorization", "Bearer $t").header("x-upsert", "true")
            .post(file.asRequestBody("application/vnd.android.package-archive".toMediaType())).build()
        try {
            client.newCall(req).execute().use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw IOException("Upload refused (HTTP ${it.code}): ${message(text)}")
            }
        } catch (e: IOException) { if (e.message?.startsWith("Upload refused") == true) throw e else throw IOException("Upload failed: ${e.message}") }
        return Defaults.SUPABASE_URL + "/storage/v1/object/public/updates/" + path
    }

    fun audit(action: String, detail: String) {
        runCatching { adminCall("POST", "/rest/v1/audit_log", JSONObject().put("actor", email).put("action", action).put("detail", detail).toString(), "return=minimal") }
    }

    class AuditRow(val at: String, val actor: String, val action: String, val detail: String)

    fun auditLog(): List<AuditRow> {
        val a = JSONArray(adminCall("GET", "/rest/v1/audit_log?select=at,actor,action,detail&order=id.desc&limit=60"))
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> AuditRow(o.optString("at").take(16).replace('T', ' '), o.optString("actor"), o.optString("action"), o.optString("detail")) } }
    }

    /** Asks Entrion's server which other AI brains it can use. Names only; no key ever reaches the phone. */
    fun fetchServedKeys() {
        com.arina.assistant.Keys.brains = com.arina.assistant.ai.Gateway.status()
    }

    // ---- Pro plan and payments -------------------------------------------------------------------------

    class Payment(val id: Long, val email: String, val trx: String, val sender: String, val amount: String, val status: String, val created: String)

    /** "free" or "pro". A Pro plan whose end date has passed counts as free. */
    fun plan(): Pair<String, String> {
        val t = token() ?: return "free" to ""
        val (code, text) = raw("GET", "/rest/v1/subscriptions?select=plan,expires_at", null, bearer = t)
        if (code !in 200..299) return "free" to ""
        val a = JSONArray(text)
        if (a.length() == 0) return "free" to ""
        val o = a.getJSONObject(0)
        val exp = o.optString("expires_at", "").let { if (it == "null") "" else it }
        if (o.optString("plan") != "pro") return "free" to ""
        if (exp.isNotEmpty() && runCatching { java.time.OffsetDateTime.parse(exp).toInstant().isBefore(java.time.Instant.now()) }.getOrDefault(false)) return "free" to exp
        return "pro" to exp
    }

    fun submitPayment(trx: String, sender: String, amount: String) {
        val t = token() ?: throw IOException("Sign in first.")
        val body = JSONObject().put("method", "bkash").put("trx_id", trx.trim()).put("sender", sender.trim())
        amount.trim().toDoubleOrNull()?.let { body.put("amount", it) }
        val (code, text) = raw("POST", "/rest/v1/payments", body.toString(), bearer = t, prefer = "return=minimal")
        if (code == 409) throw IOException("This transaction ID was already used.")
        if (code !in 200..299) throw IOException("Could not send it (HTTP $code): ${message(text)}")
    }

    /** Own payments for a person, every payment for the admin. */
    fun payments(): List<Payment> {
        val t = token() ?: return emptyList()
        val (code, text) = raw("GET", "/rest/v1/payments?select=id,user_id,trx_id,sender,amount,status,created_at&order=id.desc&limit=60", null, bearer = t)
        if (code !in 200..299) return emptyList()
        val a = JSONArray(text)
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { Payment(it.getLong("id"), it.optString("user_id").take(8), it.getString("trx_id"), it.optString("sender"), it.optString("amount").let { v -> if (v == "null") "" else v }, it.getString("status"), it.optString("created_at").take(10)) } }
    }

    fun reviewPayment(id: Long, approve: Boolean) { adminCall("POST", "/rest/v1/rpc/admin_review_payment", JSONObject().put("pid", id).put("approve", approve).toString()) }

    // ---- the account's own data follows it to every phone ----------------------------------------------

    private fun fingerprint(s: String) = s.length.toString() + ":" + s.hashCode()
    private val syncPrefs get() = ctx.getSharedPreferences("cloud_sync_" + uid.filter { it.isLetterOrDigit() }, Context.MODE_PRIVATE)

    /**
     * Run once right after sign-in, before the screen is rebuilt. If this account already has data in the cloud and
     * this phone has never synced it, the cloud copy wins: that is the person's real data, and what the phone holds
     * is only a fresh start. Returns how many sets were restored.
     */
    fun syncDown(): Int {
        val t = token() ?: return 0
        val (code, text) = raw("GET", "/rest/v1/user_data?select=name,data", null, bearer = t)
        if (code !in 200..299) throw IOException("Could not load your data (HTTP $code): ${message(text)}")
        val rows = JSONArray(text)
        var n = 0
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val name = r.getString("name"); val data = r.getString("data")
            if (name !in com.arina.assistant.cloud.Local.SYNCED || data.isBlank()) continue
            val lastPushed = syncPrefs.getString(name, null)
            val local = com.arina.assistant.cloud.Local.dump(ctx, name)
            val remoteFp = fingerprint(data)
            val localUnchanged = lastPushed != null && fingerprint(local) == lastPushed
            if (lastPushed == null || (localUnchanged && remoteFp != lastPushed)) {
                runCatching { com.arina.assistant.cloud.Local.restore(ctx, name, data); syncPrefs.edit().putString(name, remoteFp).apply(); n++ }
            }
        }
        return n
    }

    /** Uploads whatever changed on this phone since the last upload. */
    fun syncUp() {
        val t = token() ?: return
        for (name in com.arina.assistant.cloud.Local.SYNCED) {
            val data = com.arina.assistant.cloud.Local.dump(ctx, name)
            if (data.isBlank() || data.length > 400_000) continue
            val fp = fingerprint(data)
            if (syncPrefs.getString(name, null) == fp) continue
            val row = JSONObject().put("name", name).put("data", data).put("updated_at", java.time.Instant.now().toString())
            val (code, _) = raw("POST", "/rest/v1/user_data?on_conflict=user_id,name", row.toString(), bearer = t, prefer = "resolution=merge-duplicates,return=minimal")
            if (code in 200..299) syncPrefs.edit().putString(name, fp).apply()
        }
    }

    // ---- plumbing ----------------------------------------------------------------------------

    private fun raw(method: String, path: String, body: String?, bearer: String?, prefer: String? = null): Pair<Int, String> {
        val req = Request.Builder().url(Defaults.SUPABASE_URL + path)
            .header("apikey", Defaults.SUPABASE_KEY)
            .apply {
                if (bearer != null) header("Authorization", "Bearer $bearer")
                if (prefer != null) header("Prefer", prefer)
            }
            .method(method, body?.toRequestBody(JSON))
            .build()
        try {
            http.newCall(req).execute().use { return it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw IOException("No connection to Entrion cloud: ${e.message}")
        }
    }

    private fun message(text: String): String = runCatching {
        val o = JSONObject(text)
        o.optString("msg").ifBlank { o.optString("message") }.ifBlank { o.optString("error_description") }.ifBlank { text }
    }.getOrDefault(text).take(200)
}
