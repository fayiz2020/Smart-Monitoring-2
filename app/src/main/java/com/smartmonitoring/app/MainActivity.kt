package com.smartmonitoring.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    lateinit var prefs: SharedPreferences
    lateinit var cfg: JSONObject
    lateinit var body: LinearLayout
    lateinit var logTv: TextView
    lateinit var logCard: LinearLayout
    lateinit var navButtons: List<TextView>
    var logText = ""
    var currentPage = 0

    private val bg = Color.rgb(246, 248, 252)
    private val ink = Color.rgb(27, 39, 58)
    private val muted = Color.rgb(111, 125, 145)
    private val blue = Color.rgb(45, 105, 220)
    private val green = Color.rgb(25, 156, 112)
    private val red = Color.rgb(220, 75, 82)

    val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val ph = cfg.getString("phone")
            if (ph.isBlank()) return
            for (m in Telephony.Sms.Intents.getMessagesFromIntent(i)) {
                val a = m.originatingAddress ?: ""
                if (a.takeLast(9) == ph.takeLast(9)) addLog("← ${m.messageBody}")
            }
        }
    }

    fun defCfg(): JSONObject {
        val o = JSONObject(); o.put("phone", ""); o.put("pin", "1234"); o.put("alertNum", ""); o.put("alertType", 0)
        val r = JSONArray(); r.put(JSONObject().put("name", "Light").put("pin", 13)); o.put("relays", r)
        val c = JSONArray()
        for (i in 0..5) c.put(JSONObject().put("en", false).put("name", "Sensor $i").put("unit", "")
            .put("mode", 1).put("dir", 0).put("lo", 0.0).put("hi", 100.0).put("scale", 1.0)
            .put("off", 0.0).put("cut", 13).put("delay", 60).put("dunit", 0))
        o.put("ch", c); return o
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = Color.rgb(246, 248, 252)
        window.navigationBarColor = Color.rgb(246, 248, 252)
        if (Build.VERSION.SDK_INT >= 23) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        prefs = getSharedPreferences("sm", MODE_PRIVATE)
        cfg = JSONObject(prefs.getString("cfg", null) ?: defCfg().toString())

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(24, 48, 88), Color.rgb(37, 91, 166))).apply { cornerRadii = floatArrayOf(0f,0f,dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(),0f,0f) }
        }
        val brandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply { text = "SMART MONITORING"; textSize = 12f; letterSpacing = .16f; setTextColor(Color.rgb(190, 215, 255)); setTypeface(null, Typeface.BOLD) }
        brandRow.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val live = TextView(this).apply { text = "●  SMS CONTROL"; textSize = 10f; setTextColor(Color.WHITE); setPadding(dp(10), dp(6), dp(10), dp(6)); background = pill(Color.argb(45,255,255,255)) }
        brandRow.addView(live)
        header.addView(brandRow)
        val title = TextView(this).apply { text = "Control centre"; textSize = 27f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); setPadding(0, dp(12), 0, dp(3)) }
        header.addView(title)
        val sub = TextView(this).apply { text = "Manage relays, sensors and device settings"; textSize = 13f; setTextColor(Color.rgb(213, 226, 246)) }
        header.addView(sub)
        root.addView(header)

        val nav = LinearLayout(this).apply { setPadding(dp(16), dp(14), dp(16), dp(8)); gravity = Gravity.CENTER; background = android.graphics.drawable.ColorDrawable(bg) }
        val names = listOf("Relays", "Monitoring", "Settings")
        navButtons = names.mapIndexed { index, name ->
            val v = TextView(this).apply {
                text = name; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD)
                setPadding(dp(8), dp(12), dp(8), dp(12)); isClickable = true; isFocusable = true
                setOnClickListener { show(index) }
            }
            nav.addView(v, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = if (index < 2) dp(7) else 0 })
            v
        }
        root.addView(nav)

        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(18)) }
        val sv = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(body) }
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))

        logCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)); background = rounded(Color.WHITE, dp(16)) }
        val logHead = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        logHead.addView(TextView(this).apply { text = "MESSAGE LOG"; textSize = 10f; letterSpacing = .12f; setTextColor(muted); setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        logHead.addView(TextView(this).apply { text = "LIVE"; textSize = 9f; setTextColor(green); setTypeface(null, Typeface.BOLD) })
        logCard.addView(logHead)
        logTv = TextView(this).apply { textSize = 11f; setTextColor(muted); maxLines = 2; setPadding(0, dp(5), 0, 0); text = "No messages yet" }
        logCard.addView(logTv)
        val logWrap = LinearLayout(this).apply { setPadding(dp(16), dp(0), dp(16), dp(10)); addView(logCard) }
        root.addView(logWrap)
        setContentView(root)
        requestPermissions(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS), 1)
        val f = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_EXPORTED) else registerReceiver(rx, f)
        show(0)
    }

    override fun onDestroy() { super.onDestroy(); try { unregisterReceiver(rx) } catch (_: Exception) {} }
    fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    fun rounded(color: Int, radius: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat(); if (stroke != 0) setStroke(dp(1), stroke) }
    fun pill(color: Int) = rounded(color, dp(30))
    fun btn(t: String, c: () -> Unit) = Button(this).apply {
        text = t; textSize = 13f; isAllCaps = false; setTextColor(ink); setTypeface(null, Typeface.BOLD)
        background = rounded(Color.rgb(239, 243, 249), dp(12)); stateListAnimator = null
        setOnClickListener { c() }
    }
    fun primaryBtn(t: String, c: () -> Unit) = Button(this).apply {
        text = t; textSize = 13f; isAllCaps = false; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        background = rounded(blue, dp(12)); stateListAnimator = null; setOnClickListener { c() }
    }
    fun addLog(t: String) { logText = t + "\n" + logText; logTv.text = logText }
    fun save() { prefs.edit().putString("cfg", cfg.toString()).apply() }

    @Suppress("DEPRECATION")
    fun smsMgr(): SmsManager = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
    fun sms(t: String) {
        val ph = cfg.getString("phone")
        if (ph.isBlank()) { Toast.makeText(this, "Set Arduino number in Settings", Toast.LENGTH_LONG).show(); return }
        val full = "${cfg.getString("pin")} $t"
        try { smsMgr().sendTextMessage(ph, null, full, null, null); addLog("→ $full") }
        catch (e: Exception) { addLog("Send failed: ${e.message}") }
    }
    fun code(p: Int, on: Boolean): String { val s = if (on) 1 else 0; return if (p <= 9) "${p * 1000 + p * 10 + s}" else "${1100 + (p - 10) * 10 + s}" }

    fun form(title: String, f: List<Pair<String, String>>, ok: (List<String>) -> Unit, del: (() -> Unit)? = null) {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val eds = f.map { (label, v) ->
            l.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(muted); setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, dp(4)) })
            EditText(this).apply { setText(v); textSize = 15f; setSingleLine(true); setPadding(dp(12), dp(9), dp(12), dp(9)); background = rounded(Color.rgb(244,247,251), dp(10)); l.addView(this) }
        }
        val sv = ScrollView(this).apply { addView(l) }
        val b = AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton("Save") { _, _ -> ok(eds.map { it.text.toString().trim() }) }.setNegativeButton("Cancel", null)
        if (del != null) b.setNeutralButton("Delete") { _, _ -> del() }
        b.show()
    }

    fun show(t: Int) {
        currentPage = t
        navButtons.forEachIndexed { i, v ->
            v.background = if (i == t) rounded(blue, dp(12)) else rounded(Color.WHITE, dp(12), Color.rgb(230,235,243))
            v.setTextColor(if (i == t) Color.WHITE else muted)
        }
        body.removeAllViews()
        when (t) { 0 -> relays(); 1 -> monitor(); else -> settings() }
    }
    fun sectionTitle(title: String, sub: String) {
        body.addView(TextView(this).apply { text = title; textSize = 21f; setTextColor(ink); setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, dp(3)) })
        body.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(muted); setPadding(0, 0, 0, dp(14)) })
    }
    fun card(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(15), dp(16), dp(15)); background = rounded(Color.WHITE, dp(17), Color.rgb(232,237,245)) }
    fun addCard(v: View, bottom: Int = 10) { body.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) }) }

    fun relays() {
        val arr = cfg.getJSONArray("relays")
        sectionTitle("Relay controls", "Switch connected appliances using SMS commands")
        val count = TextView(this).apply { text = "${arr.length()}  RELAYS CONFIGURED"; textSize = 10f; letterSpacing = .1f; setTextColor(blue); setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(10)) }
        body.addView(count)
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i); val p = r.getInt("pin")
            val c = card()
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val icon = TextView(this).apply { text = "ϟ"; gravity = Gravity.CENTER; textSize = 24f; setTextColor(blue); background = rounded(Color.rgb(235,242,255), dp(13)); layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)) }
            row.addView(icon)
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            info.addView(TextView(this).apply { text = r.getString("name"); textSize = 16f; setTextColor(ink); setTypeface(null, Typeface.BOLD) })
            info.addView(TextView(this).apply { text = "OUTPUT  •  GPIO $p"; textSize = 10f; setTextColor(muted); setPadding(0, dp(3), 0, 0) })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            val edit = TextView(this).apply { text = "⋮"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(muted); setOnClickListener { form("Edit relay", listOf("Name" to r.getString("name"), "Pin (4-13)" to "$p"), { v -> val np = v[1].toIntOrNull() ?: p; if (np in 4..13) { r.put("name", v[0]); r.put("pin", np); save(); show(0) } }, { arr.remove(i); save(); show(0) }) } }
            row.addView(edit, LinearLayout.LayoutParams(dp(32), dp(44)))
            c.addView(row)
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, 0) }
            val on = Button(this).apply { text = "TURN ON"; textSize = 11f; isAllCaps = false; setTypeface(null, Typeface.BOLD); setTextColor(green); background = rounded(Color.rgb(231,248,241), dp(10)); stateListAnimator = null; setOnClickListener { sms(code(p, true)) } }
            val off = Button(this).apply { text = "TURN OFF"; textSize = 11f; isAllCaps = false; setTypeface(null, Typeface.BOLD); setTextColor(red); background = rounded(Color.rgb(255,239,240), dp(10)); stateListAnimator = null; setOnClickListener { sms(code(p, false)) } }
            actions.addView(on, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) }); actions.addView(off, LinearLayout.LayoutParams(0, dp(44), 1f))
            c.addView(actions); addCard(c)
        }
        addCard(primaryBtn("＋   Add a relay") {
            form("New relay", listOf("Name" to "", "Pin (4-13)" to ""), { v -> val np = v[1].toIntOrNull() ?: 0; if (np in 4..13 && v[0].isNotBlank()) { arr.put(JSONObject().put("name", v[0]).put("pin", np)); save(); show(0) } else Toast.makeText(this, "Enter a name and valid pin", Toast.LENGTH_SHORT).show() })
        }, 8)
        addCard(btn("↻   Request relay status") { sms("STAT") }, 4)
    }

    fun monitor() {
        val ch = cfg.getJSONArray("ch")
        sectionTitle("Sensor monitoring", "Configure thresholds, alerts and cutoff behaviour")
        for (i in 0..5) {
            val c = ch.getJSONObject(i); val enabled = c.getBoolean("en")
            val card = card()
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val badge = TextView(this).apply { text = "A$i"; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(if (enabled) green else muted); background = rounded(if (enabled) Color.rgb(231,248,241) else Color.rgb(241,244,248), dp(11)); layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)) }
            row.addView(badge)
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            info.addView(TextView(this).apply { text = c.getString("name"); textSize = 15f; setTextColor(ink); setTypeface(null, Typeface.BOLD) })
            info.addView(TextView(this).apply { text = "ANALOG INPUT  •  MODE ${c.getInt("mode")}"; textSize = 10f; setTextColor(muted); setPadding(0, dp(3), 0, 0) })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            val status = TextView(this).apply { text = if (enabled) "ENABLED" else "DISABLED"; textSize = 9f; setTypeface(null, Typeface.BOLD); setTextColor(if (enabled) green else muted); background = pill(if (enabled) Color.rgb(231,248,241) else Color.rgb(241,244,248)); setPadding(dp(9), dp(6), dp(9), dp(6)) }
            row.addView(status); card.addView(row)
            val detail = TextView(this).apply { text = "Low ${c.getDouble("lo")}   ·   High ${c.getDouble("hi")}   ${c.getString("unit")}"; textSize = 11f; setTextColor(muted); setPadding(0, dp(12), 0, 0) }
            card.addView(detail)
            card.setOnClickListener { editCh(i) }; addCard(card, 8)
        }
        addCard(primaryBtn("Send all channel settings") { for (i in 0..5) sendCh(i); Toast.makeText(this, "Channel settings sent", Toast.LENGTH_SHORT).show() }, 4)
    }

    val keys = listOf("name", "unit", "en", "mode", "dir", "lo", "hi", "scale", "off", "cut", "delay", "dunit")
    val labels = listOf("Name", "Unit (V, C, bar...)", "Enabled (1/0)", "Mode (1 alert only, 2 alert + cutoff)", "Trigger (0 above High, 1 below Low, 2 outside Low-High)", "Low limit", "High limit", "Scale (reading = raw x scale + offset)", "Offset", "Cutoff relay pin (mode 2)", "Delay between alerts", "Delay unit (0 sec, 1 min, 2 hour)")
    fun editCh(i: Int) {
        val c = cfg.getJSONArray("ch").getJSONObject(i)
        val init = keys.mapIndexed { n, k -> labels[n] to (if (k == "en") (if (c.getBoolean("en")) "1" else "0") else c.get(k).toString()) }
        form("Configure channel A$i", init, { v ->
            keys.forEachIndexed { n, k -> when (k) {
                "name", "unit" -> c.put(k, v[n]); "en" -> c.put(k, v[n] == "1")
                "mode", "dir", "cut", "delay", "dunit" -> c.put(k, v[n].toIntOrNull() ?: 0)
                else -> c.put(k, v[n].toDoubleOrNull() ?: 0.0)
            } }
            save(); sendCh(i); show(1)
        })
    }
    fun sendCh(i: Int) {
        val c = cfg.getJSONArray("ch").getJSONObject(i); val mult = intArrayOf(1, 60, 3600)[c.getInt("dunit").coerceIn(0, 2)]; val en = if (c.getBoolean("en")) 1 else 0
        sms("SET $i $en ${c.getInt("mode")} ${c.getInt("dir")} ${c.getDouble("lo")} ${c.getDouble("hi")} ${c.getDouble("scale")} ${c.getDouble("off")} ${c.getInt("cut")} ${c.getInt("delay") * mult}")
    }

    fun settings() {
        sectionTitle("Device settings", "Connection details and notification preferences")
        val details = listOf("ARDUINO NUMBER" to cfg.getString("phone"), "SECURITY PIN" to cfg.getString("pin"), "ALERT / REPLY NUMBER" to cfg.getString("alertNum").ifBlank { "Use sender number" }, "ALERT TYPE" to listOf("SMS", "Call", "SMS + Call").getOrElse(cfg.getInt("alertType")) { "SMS" })
        details.forEach { (label, value) ->
            val c = card(); c.addView(TextView(this).apply { text = label; textSize = 9f; letterSpacing = .1f; setTextColor(muted); setTypeface(null, Typeface.BOLD) })
            c.addView(TextView(this).apply { text = value.ifBlank { "Not configured" }; textSize = 16f; setTextColor(ink); setTypeface(null, Typeface.BOLD); setPadding(0, dp(5), 0, 0) })
            addCard(c, 8)
        }
        addCard(primaryBtn("Edit device settings") {
            form("Device settings", listOf("Arduino SIM number (with country code)" to cfg.getString("phone"), "PIN (letters/digits, no spaces)" to cfg.getString("pin"), "Fixed number for alerts/replies (empty = reply to sender)" to cfg.getString("alertNum"), "Alert type (0 SMS, 1 Call, 2 Both)" to cfg.getInt("alertType").toString()), { v ->
                cfg.put("phone", v[0]); val oldPin = cfg.getString("pin"); cfg.put("alertNum", v[2]); cfg.put("alertType", (v[3].toIntOrNull() ?: 0).coerceIn(0, 2))
                sms("NUM ${if (v[2].isBlank()) "0" else v[2]} ${cfg.getInt("alertType")}")
                if (v[1].isNotBlank() && v[1] != oldPin) { sms("PWD ${v[1]}"); cfg.put("pin", v[1]) }
                save(); show(2)
            })
        }, 8)
        val note = TextView(this).apply { text = "Changes are saved on this phone. SMS commands are sent to the configured Arduino number."; textSize = 11f; setTextColor(muted); setPadding(dp(4), dp(4), dp(4), dp(10)) }
        body.addView(note)
    }
}
