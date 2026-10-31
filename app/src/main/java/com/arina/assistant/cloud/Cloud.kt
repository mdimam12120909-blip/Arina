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

    private fun upsert(table: String, key: String, value: String) {
        val t = token() ?: throw IOException("Sign in first.")
        val row = JSONArray().put(JSONObject().put("key", key).put("value", value))
        val (code, text) = raw("POST", "/rest/v1/$table?on_conflict=key", row.toString(), bearer = t, prefer = "resolution=merge-duplicates,return=minimal")
        if (code !in 200..299) throw IOException("Could not save (HTTP $code): ${message(text)}")
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
