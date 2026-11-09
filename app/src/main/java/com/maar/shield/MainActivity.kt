package com.maar.shield

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val phoneDisplay = "+44 7440 445929"
    private val waLink = "https://wa.me/447440445929"
    private lateinit var status: TextView
    private lateinit var onBtn: Button
    private lateinit var offBtn: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 140, 56, 56)
            setBackgroundColor(0xFF0F0F1A.toInt())
        }
        fun tv(t: String, size: Float, color: Int) = TextView(this).apply {
            text = t; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        root.addView(tv("MAAR Shield", 30f, 0xFFFFFFFF.toInt()))
        root.addView(tv("Blocks pornography and 18+ websites on this device.", 15f, 0xFFB0B0C8.toInt()))
        status = tv("", 22f, 0xFF4ADE80.toInt()).also { root.addView(it) }
        onBtn = Button(this).apply { text = "Turn ON"; setOnClickListener { confirmOn() } }
        offBtn = Button(this).apply { text = "Turn OFF"; setOnClickListener { showUnlock() } }
        val lock = Button(this).apply {
            text = "Lock it down (Always-on VPN)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
        }
        root.addView(onBtn); root.addView(offBtn); root.addView(lock)
        root.addView(tv("Tip: in VPN settings tap the gear next to MAAR Shield and enable " +
            "\"Always-on VPN\" and \"Block connections without VPN\".", 13f, 0xFF8888A8.toInt()))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val on = Unlock.enabled(this)
        status.text = if (on) "Protection is ON" else "Protection is OFF"
        status.setTextColor(if (on) 0xFF4ADE80.toInt() else 0xFFF87171.toInt())
        onBtn.isEnabled = !on
        offBtn.isEnabled = on
    }

    private fun confirmOn() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Read before turning on")
            .setMessage("Be careful: as soon as you turn this on, it blocks every single 18+ / pornographic website.\n\n" +
                "To turn it off you must contact $phoneDisplay on WhatsApp and you will need 5 different one-time passwords, given one by one.\n\n" +
                "While it is on, no other VPN can be connected.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I understand, turn ON") { _, _ ->
                val i = VpnService.prepare(this)
                if (i != null) startActivityForResult(i, 1) else startShield()
            }.show()
    }

    @Deprecated("fine for a framework Activity")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK) startShield()
    }

    private fun startShield() {
        Unlock.setEnabled(this, true)
        startService(Intent(this, BlockerVpnService::class.java))
        refresh()
    }

    private fun showUnlock() {
        val stage = Unlock.stage(this)
        if (Unlock.complete(this)) { stopShield(); return }
        val box = EditText(this).apply {
            hint = "Password ${stage + 1} of ${Unlock.TOTAL}"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF888888.toInt())
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Turn off: step ${stage + 1} of ${Unlock.TOTAL}")
            .setMessage("To turn this off, message $phoneDisplay on WhatsApp. " +
                "You will be given 5 one-time passwords, one at a time.")
            .setView(box)
            .setNeutralButton("Open WhatsApp") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waLink)))
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Submit") { _, _ ->
                if (Unlock.submit(this, box.text.toString())) {
                    if (Unlock.complete(this)) stopShield()
                    else { Toast.makeText(this, "Correct. Next password needed.", Toast.LENGTH_SHORT).show(); showUnlock() }
                } else {
                    Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show(); showUnlock()
                }
            }.show()
    }

    private fun stopShield() {
        startService(Intent(this, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_STOP))
        Unlock.setEnabled(this, false)
        Toast.makeText(this, "Protection turned off", Toast.LENGTH_LONG).show()
        refresh()
    }
}
