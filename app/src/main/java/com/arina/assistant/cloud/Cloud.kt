package com.arina.assistant.cloud

import android.content.Context
import com.arina.assistant.Defaults
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    /** Builds on GitHub that carry an Entrion.apk file: (build number, tag, download link, date). */
    class Build(val build: Int, val tag: String, val url: String, val date: String)

    fun githubBuilds(): List<Build> {
        val req = Request.Builder().url("https://api.github.com/repos/mdimam12120909-blip/Arina/releases?per_page=30")
            .header("Accept", "application/vnd.github+json").build()
        val text = try {
            http.newCall(req).execute().use { if (!it.isSuccessful) throw IOException("GitHub said HTTP ${it.code}"); it.body?.string().orEmpty() }
        } catch (e: IOException) { throw IOException("Could not reach GitHub: ${e.message}") }
        val a = JSONArray(text)
        val out = ArrayList<Build>()
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            val tag = r.optString("tag_name")
            val n = Regex("""build-(\d+)""").matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val assets = r.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val s = assets.getJSONObject(j)
                if (s.optString("name") == "Entrion.apk") out.add(Build(n, tag, s.optString("browser_download_url"), r.optString("published_at").take(10)))
            }
        }
        return out
    }

    fun audit(action: String, detail: String) {
        runCatching { adminCall("POST", "/rest/v1/audit_log", JSONObject().put("actor", email).put("action", action).put("detail", detail).toString(), "return=minimal") }
    }

    class AuditRow(val at: String, val actor: String, val action: String, val detail: String)

    fun auditLog(): List<AuditRow> {
        val a = JSONArray(adminCall("GET", "/rest/v1/audit_log?select=at,actor,action,detail&order=id.desc&limit=60"))
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> AuditRow(o.optString("at").take(16).replace('T', ' '), o.optString("actor"), o.optString("action"), o.optString("detail")) } }
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
