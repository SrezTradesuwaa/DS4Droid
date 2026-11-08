package com.padmapper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One gamepad button -> one tap (or one swipe) on the screen. */
data class Mapping(
    val keyCode: Int,
    val x: Float,
    val y: Float,
    val x2: Float,
    val y2: Float,
    val swipe: Boolean
)

/** Saves mappings per game (per app package name) on the phone. */
object Prefs {

    private fun sp(c: Context) = c.getSharedPreferences("padmapper", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)

    fun setEnabled(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("enabled", value).apply()
    }

    fun load(c: Context, pkg: String): MutableList<Mapping> {
        val out = mutableListOf<Mapping>()
        try {
            val arr = JSONArray(sp(c).getString("map_$pkg", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Mapping(
                        o.getInt("k"),
                        o.getDouble("x").toFloat(),
                        o.getDouble("y").toFloat(),
                        o.getDouble("x2").toFloat(),
                        o.getDouble("y2").toFloat(),
                        o.getBoolean("s")
                    )
                )
            }
        } catch (ignored: Exception) {
            // Broken data: treat as empty.
        }
        return out
    }

    fun save(c: Context, pkg: String, list: List<Mapping>) {
        val arr = JSONArray()
        for (m in list) {
            arr.put(
                JSONObject()
                    .put("k", m.keyCode)
                    .put("x", m.x.toDouble())
                    .put("y", m.y.toDouble())
                    .put("x2", m.x2.toDouble())
                    .put("y2", m.y2.toDouble())
                    .put("s", m.swipe)
            )
        }
        sp(c).edit().putString("map_$pkg", arr.toString()).apply()
    }

    fun clear(c: Context, pkg: String) {
        sp(c).edit().remove("map_$pkg").apply()
    }

    fun clearAll(c: Context) {
        val editor = sp(c).edit()
        for (p in packages(c)) editor.remove("map_$p")
        editor.apply()
    }

    fun packages(c: Context): List<String> =
        sp(c).all.keys.filter { it.startsWith("map_") }.map { it.removePrefix("map_") }

    fun keyName(code: Int): String = android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
}
