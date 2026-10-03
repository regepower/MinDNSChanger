package de.regepower.mindnschanger

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.method.DigitsKeyListener
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var servers: RadioGroup
    private lateinit var appsBtn: Button
    private lateinit var appsMode: TextView
    private val listener: () -> Unit = { renderState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(12), px(16), px(16))
        }

        // Status + on/off
        val statusCard = card()
        status = TextView(this).apply { textSize = 18f }
        statusCard.addView(status)
        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        statusCard.addView(toggle, fullWidth(8))
        content.addView(statusCard, fullWidth())

        // DNS servers
        content.addView(header(getString(R.string.header_server)))
        val serverCard = card()
        servers = RadioGroup(this)
        serverCard.addView(servers)
        content.addView(serverCard, fullWidth())
        content.addView(hint(getString(R.string.hint_custom)))
        content.addView(button(getString(R.string.btn_add_server), null) { addServerDialog() }, fullWidth(4))

        // App filter
        content.addView(header(getString(R.string.header_apps)))
        appsMode = hint("")
        content.addView(appsMode)
        appsBtn = button("", null) { startActivity(Intent(this, AppsActivity::class.java)) }
        content.addView(appsBtn, fullWidth(4))

        // Options
        content.addView(header(getString(R.string.header_options)))
        val autostart = Switch(this).apply {
            text = getString(R.string.autostart)
            textSize = 15f
            isChecked = prefs.autostart
            setPadding(px(4), px(4), px(4), px(4))
            setOnCheckedChangeListener { _, on -> prefs.autostart = on }
        }
        content.addView(autostart, fullWidth())
        content.addView(
            button(getString(R.string.btn_always_on), null) { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) },
            fullWidth(8)
        )
        content.addView(hint(getString(R.string.hint_always_on)))
        content.addView(hint(getString(R.string.hint_tile)))

        setContentView(
            ScrollView(this).apply {
                fitsSystemWindows = true
                addView(content)
            }
        )

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
        }
        fillServers()
    }

    override fun onResume() {
        super.onResume()
        DnsVpnService.listeners.add(listener)
        renderState()
        val count = prefs.packages.size
        appsBtn.text = getString(R.string.btn_apps, count)
        appsMode.text = getString(if (prefs.whitelist) R.string.mode_whitelist else R.string.mode_blacklist)
    }

    override fun onPause() {
        DnsVpnService.listeners.remove(listener)
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_VPN) return
        if (resultCode == RESULT_OK) {
            DnsVpnService.start(this)
        } else {
            Toast.makeText(this, R.string.err_vpn_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onToggle() {
        if (DnsVpnService.running) {
            DnsVpnService.stop(this)
            return
        }
        val consent = VpnService.prepare(this)
        if (consent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(consent, REQ_VPN)
        } else {
            DnsVpnService.start(this)
        }
    }

    private fun renderState() {
        val active = DnsVpnService.active
        if (active != null) {
            status.text = getString(R.string.state_on, active.name)
            toggle.text = getString(R.string.btn_stop)
            styleButton(toggle, R.color.md_container, R.color.md_on_container)
        } else {
            status.text = getString(R.string.state_off)
            toggle.text = getString(R.string.btn_start)
            styleButton(toggle, R.color.md_primary, R.color.md_on_primary)
        }
    }

    private fun fillServers() {
        servers.setOnCheckedChangeListener(null)
        servers.removeAllViews()
        val selected = prefs.current().name
        prefs.servers().forEach { s ->
            val label = "${s.name}\n${s.addresses}"
            val text = SpannableString(label).apply {
                setSpan(RelativeSizeSpan(0.8f), s.name.length + 1, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                this.text = text
                textSize = 15f
                tag = s.name
                setPadding(px(8), px(6), 0, px(6))
                if (s.custom) {
                    setOnLongClickListener {
                        deleteServerDialog(s)
                        true
                    }
                }
            }
            servers.addView(rb)
            if (s.name == selected) servers.check(rb.id)
        }
        servers.setOnCheckedChangeListener { group, checkedId ->
            val name = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            prefs.selected = name
            if (DnsVpnService.running) DnsVpnService.start(this)
        }
    }

    private fun addServerDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(8), px(20), 0)
        }
        fun field(hintRes: Int, ip: Boolean) = EditText(this).apply {
            hint = getString(hintRes)
            setSingleLine()
            if (ip) {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                keyListener = DigitsKeyListener.getInstance("0123456789.")
            } else {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            form.addView(this)
        }
        val name = field(R.string.field_name, false)
        val primary = field(R.string.field_primary, true)
        val secondary = field(R.string.field_secondary, true)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dlg_add_title)
            .setView(form)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        // Validate before closing.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = name.text.toString().trim()
            val p = primary.text.toString().trim()
            val s = secondary.text.toString().trim()
            val names = prefs.servers().map { it.name.lowercase() }
            var ok = true
            if (n.isEmpty() || n.contains('\t') || n.lowercase() in names) {
                name.error = getString(R.string.err_name)
                ok = false
            }
            if (!DnsServer.isIpv4(p)) {
                primary.error = getString(R.string.err_ip)
                ok = false
            }
            if (s.isNotEmpty() && !DnsServer.isIpv4(s)) {
                secondary.error = getString(R.string.err_ip)
                ok = false
            }
            if (!ok) return@setOnClickListener
            prefs.custom = prefs.custom + DnsServer(n, p, s.ifEmpty { null }, true)
            fillServers()
            dialog.dismiss()
        }
    }

    private fun deleteServerDialog(s: DnsServer) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dlg_delete_title, s.name))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                val wasSelected = prefs.current().name == s.name
                prefs.custom = prefs.custom.filter { it.name != s.name }
                if (wasSelected) {
                    prefs.selected = DnsServer.PRESETS[0].name
                    if (DnsVpnService.running) DnsVpnService.start(this)
                }
                fillServers()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val REQ_VPN = 1
        private const val REQ_NOTIFY = 2
    }
}
