package com.padmapper

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var info: TextView
    private lateinit var mappingsView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        root.addView(label("PadMapper", 26f))

        info = label("", 16f)
        root.addView(info)

        root.addView(
            label(
                "Setup (once):\n" +
                    "1. Tap button 1, find PadMapper in the list, turn it ON.\n" +
                    "   If Android says \"Restricted setting\", tap button 1b, open the three-dots " +
                    "menu (top right) and choose \"Allow restricted settings\", then try again.\n" +
                    "2. Pair your DualShock 4 in Bluetooth settings (hold PS + Share until the " +
                    "light bar flashes).\n\n" +
                    "For each game:\n" +
                    "3. Tap button 2, then open your game.\n" +
                    "4. In the panel at the top: tap \"+ Add\", press a controller button, then touch " +
                    "(or drag) the spot on the screen where that button should act.\n" +
                    "5. Repeat for every button, then tap \"Done\".",
                14f
            )
        )

        root.addView(button("1. Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("1b. Open app info (Allow restricted settings)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })

        val sw = Switch(this)
        sw.text = "Controller mapping enabled"
        sw.isChecked = Prefs.enabled(this)
        sw.setOnCheckedChangeListener { _, on -> Prefs.setEnabled(this, on) }
        root.addView(sw)

        root.addView(button("2. Edit buttons (then open your game)") {
            val service = PadService.instance
            if (service == null) {
                toast("First turn PadMapper ON in Accessibility settings (step 1)")
            } else {
                service.startEdit()
                toast("Open your game now. Use the panel at the top of the screen.")
                moveTaskToBack(true)
            }
        })

        root.addView(label("Saved mappings", 18f))
        mappingsView = label("", 13f)
        root.addView(mappingsView)

        root.addView(button("Clear ALL mappings") {
            Prefs.clearAll(this)
            refresh()
        })

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        info.text = if (PadService.instance != null) {
            "Service: ON (ready)"
        } else {
            "Service: OFF (do step 1)"
        }

        val sb = StringBuilder()
        val pkgs = Prefs.packages(this)
        if (pkgs.isEmpty()) sb.append("Nothing saved yet.")
        for (p in pkgs) {
            val list = Prefs.load(this, p)
            sb.append(p).append("  (").append(list.size).append(" buttons)\n")
            for (m in list) {
                sb.append("   ").append(Prefs.keyName(m.keyCode)).append(" -> ")
                if (m.swipe) {
                    sb.append("swipe ")
                        .append(m.x.toInt()).append(",").append(m.y.toInt())
                        .append(" to ")
                        .append(m.x2.toInt()).append(",").append(m.y2.toInt())
                } else {
                    sb.append("tap ").append(m.x.toInt()).append(",").append(m.y.toInt())
                }
                sb.append("\n")
            }
            sb.append("\n")
        }
        mappingsView.text = sb.toString()
    }

    private fun label(s: String, size: Float): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = size
        val p = (6 * resources.displayMetrics.density).toInt()
        tv.setPadding(0, p, 0, p)
        return tv
    }

    private fun button(t: String, onClick: () -> Unit): View {
        val b = Button(this)
        b.text = t
        b.setOnClickListener { onClick() }
        return b
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
