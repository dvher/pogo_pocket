package dev.pogo.pocket

import androidx.compose.ui.graphics.Color
import dev.pogo.pocket.core.pogocore.Pogocore
import org.json.JSONArray
import org.json.JSONObject

// Kotlin views of the JSON the Go core returns (see core/*.go).

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val color: String,
    val updatedAt: Long,
) {
    companion object {
        fun parse(o: JSONObject) = Note(
            o.getString("id"), o.getString("title"), o.getString("content"),
            o.getString("color"), o.getLong("updatedAt"),
        )

        fun parseList(json: String): List<Note> {
            val a = JSONArray(json)
            return List(a.length()) { parse(a.getJSONObject(it)) }
        }
    }
}

data class Line(
    val kind: String,
    val text: String,
    val marker: String,
    val indent: Int,
    val checked: Boolean,
    val line: Int,
) {
    companion object {
        fun parse(o: JSONObject) = Line(
            o.getString("kind"), o.optString("text"), o.optString("marker"),
            o.optInt("indent"), o.optBoolean("checked"), o.optInt("line"),
        )

        fun parseList(a: JSONArray) = List(a.length()) { parse(a.getJSONObject(it)) }

        fun of(markdown: String) = parseList(JSONArray(Pogocore.linesJSON(markdown)))
    }
}

data class WidgetView(
    val state: String, // note, empty, locked
    val note: Note?,
    val bg: Color,
    val ink: Color,
    val accent: Color,
    val lines: List<Line>,
    val index: Int,
    val count: Int,
) {
    companion object {
        fun parse(json: String): WidgetView {
            val o = JSONObject(json)
            return WidgetView(
                o.getString("state"),
                o.optJSONObject("note")?.let { Note.parse(it) },
                hex(o.getString("bg")), hex(o.getString("ink")), hex(o.getString("accent")),
                Line.parseList(o.getJSONArray("lines")),
                o.getInt("index"), o.getInt("count"),
            )
        }
    }
}

data class PaletteColor(val name: String, val bg: Color, val ink: Color, val accent: Color)

object Palette {
    val colors: List<PaletteColor> by lazy {
        val a = JSONArray(Pogocore.paletteJSON())
        List(a.length()) {
            val o = a.getJSONObject(it)
            PaletteColor(o.getString("name"), hex(o.getString("bg")), hex(o.getString("ink")), hex(o.getString("accent")))
        }
    }

    fun of(name: String) = colors.firstOrNull { it.name == name } ?: colors.first()
}

data class SyncStatus(
    val enabled: Boolean = false,
    val syncing: Boolean = false,
    val lastSync: Long = 0,
    val lastError: String = "",
    val e2eServer: Boolean = false,
    val e2eLocked: Boolean = false,
    val e2eEnabled: Boolean = false,
) {
    companion object {
        fun parse(json: String): SyncStatus {
            val o = JSONObject(json)
            return SyncStatus(
                o.optBoolean("enabled"), o.optBoolean("syncing"), o.optLong("lastSync"),
                o.optString("lastError"), o.optBoolean("e2eServer"), o.optBoolean("e2eLocked"),
                o.optBoolean("e2eEnabled"),
            )
        }
    }
}

data class Settings(
    val scheme: String = "http",
    val host: String = "",
    val port: String = "8080",
    val token: String = "",
    val enabled: Boolean = false,
    val intervalSec: Int = 30,
) {
    fun toJSON(): String = JSONObject()
        .put("scheme", scheme).put("host", host).put("port", port).put("token", token)
        .put("enabled", enabled).put("intervalSec", intervalSec).toString()

    companion object {
        fun parse(json: String): Settings {
            val o = JSONObject(json)
            return Settings(
                o.optString("scheme", "http"), o.optString("host"), o.optString("port"),
                o.optString("token"), o.optBoolean("enabled"), o.optInt("intervalSec", 30),
            )
        }
    }
}

fun hex(s: String): Color = Color(android.graphics.Color.parseColor(s))

/** Mixes two colors; t=0 gives a, t=1 gives b. */
fun mix(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
)
