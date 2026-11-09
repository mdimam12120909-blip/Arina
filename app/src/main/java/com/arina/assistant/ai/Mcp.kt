package com.arina.assistant.ai

import android.content.SharedPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class McpServer(val name: String, val url: String, val token: String = "")

/** The MCP servers the user connected, saved per account. */
class McpStore(private val prefs: SharedPreferences) {
    fun load(): List<McpServer> {
        val arr = runCatching { JSONArray(prefs.getString("mcp_servers", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { McpServer(it.optString("n"), it.optString("u"), it.optString("t")) } }
            .filter { it.name.isNotBlank() && it.url.startsWith("http") }
    }
    fun save(list: List<McpServer>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("u", it.url).put("t", it.token)) }
        prefs.edit().putString("mcp_servers", arr.toString()).apply()
    }
}

/** A tool offered by a server. [fn] is the name Gemini sees. */
class McpTool(val server: McpServer, val tool: String, val fn: String, val description: String, val schema: JSONObject)

/**
 * MCP client over Streamable HTTP (JSON-RPC 2.0 by POST). Entrion connects to the user's MCP servers, lists their tools
 * for Gemini and calls them on request. Works with servers that take a bearer token (or none).
 */
class McpHub(private val store: McpStore) {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()
    private val sessions = HashMap<String, String>()
    @Volatile private var cache: List<McpTool> = emptyList()
    @Volatile private var loadedAt = 0L

    fun servers() = store.load()
    fun cached(): List<McpTool> = cache

    private fun clean(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(24)

    private fun rpc(sv: McpServer, method: String, params: JSONObject?, id: Int?): JSONObject? {
        val body = JSONObject().put("jsonrpc", "2.0").put("method", method)
        if (params != null) body.put("params", params)
        if (id != null) body.put("id", id)
        val rb = Request.Builder().url(sv.url).post(body.toString().toRequestBody(JSON))
            .header("Accept", "application/json, text/event-stream").header("Content-Type", "application/json")
        if (sv.token.isNotBlank()) rb.header("Authorization", "Bearer ${sv.token}")
        sessions[sv.url]?.let { rb.header("Mcp-Session-Id", it) }
        http.newCall(rb.build()).execute().use { r ->
            r.header("Mcp-Session-Id")?.let { sessions[sv.url] = it }
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            if (id == null) return null
            val text = r.body?.string().orEmpty()
            val json = if ((r.header("Content-Type") ?: "").contains("event-stream")) {
                text.lineSequence().filter { it.startsWith("data:") }.map { it.removePrefix("data:").trim() }
                    .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.lastOrNull { it.has("result") || it.has("error") }
            } else runCatching { JSONObject(text) }.getOrNull()
            if (json == null) throw RuntimeException("empty answer")
            json.optJSONObject("error")?.let { throw RuntimeException(it.optString("message", "error")) }
            return json.optJSONObject("result")
        }
    }

    private fun connect(sv: McpServer) {
        if (sessions.containsKey(sv.url)) return
        rpc(sv, "initialize", JSONObject().put("protocolVersion", "2025-03-26").put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "Entrion").put("version", "1.0")), 1)
        runCatching { rpc(sv, "notifications/initialized", JSONObject(), null) }
        if (!sessions.containsKey(sv.url)) sessions[sv.url] = ""
    }

    /** Asks every server for its tools. Blocking: run off the main thread. Returns how many tools were found. */
    fun refresh(): Int {
        val out = ArrayList<McpTool>()
        for (sv in store.load()) {
            runCatching {
                connect(sv)
                val res = rpc(sv, "tools/list", JSONObject(), 2)
                val arr = res?.optJSONArray("tools") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    val nm = t.optString("name")
                    out += McpTool(sv, nm, "mcp_${clean(sv.name)}_${clean(nm)}".take(60), t.optString("description"), t.optJSONObject("inputSchema") ?: JSONObject())
                }
            }.onFailure { Diag.log("mcp ${sv.name}: ${it.message}") }
        }
        cache = out; loadedAt = System.currentTimeMillis()
        return out.size
    }

    /** Test one server before saving it: its tool count, or a failure text. */
    fun test(sv: McpServer): String = try {
        sessions.remove(sv.url); connect(sv)
        val n = rpc(sv, "tools/list", JSONObject(), 2)?.optJSONArray("tools")?.length() ?: 0
        "OK: $n tools"
    } catch (e: Exception) { "ERROR: ${e.message}" }

    private fun schemaFor(s: JSONObject): JSONObject {
        fun conv(o: JSONObject): JSONObject {
            val r = JSONObject()
            val type = o.optString("type", "string").uppercase().let { if (it in setOf("STRING", "NUMBER", "INTEGER", "BOOLEAN", "ARRAY", "OBJECT")) it else "STRING" }
            r.put("type", type)
            if (o.has("description")) r.put("description", o.optString("description"))
            if (o.has("enum")) r.put("enum", o.optJSONArray("enum"))
            o.optJSONObject("properties")?.let { p -> val np = JSONObject(); p.keys().forEach { k -> np.put(k, conv(p.getJSONObject(k))) }; r.put("properties", np) }
            o.optJSONObject("items")?.let { r.put("items", conv(it)) }
            if (type == "ARRAY" && !r.has("items")) r.put("items", JSONObject().put("type", "STRING"))
            return r
        }
        val c = conv(s.apply { if (!has("type")) put("type", "object") })
        s.optJSONArray("required")?.let { c.put("required", it) }
        return c
    }

    /** Function declarations for Gemini, one per MCP tool found. */
    fun declarations(): JSONArray = JSONArray().apply {
        cache.forEach { t ->
            val d = JSONObject().put("name", t.fn).put("description", "[${t.server.name}] ${t.description}".take(900))
            val sc = schemaFor(JSONObject(t.schema.toString()))
            if ((sc.optJSONObject("properties")?.length() ?: 0) > 0) d.put("parameters", sc)
            put(d)
        }
    }

    fun owns(fn: String) = cache.any { it.fn == fn }

    fun call(fn: String, args: JSONObject): String {
        val t = cache.firstOrNull { it.fn == fn } ?: return "ERROR: unknown tool"
        return try {
            connect(t.server)
            val res = rpc(t.server, "tools/call", JSONObject().put("name", t.tool).put("arguments", args), 3)
            val content = res?.optJSONArray("content") ?: JSONArray()
            val text = (0 until content.length()).mapNotNull { content.optJSONObject(it)?.optString("text")?.takeIf { s -> s.isNotBlank() } }.joinToString("\n")
            val out = text.ifBlank { res?.toString().orEmpty() }.take(6000)
            if (res?.optBoolean("isError") == true) "ERROR: $out" else out
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }
}
