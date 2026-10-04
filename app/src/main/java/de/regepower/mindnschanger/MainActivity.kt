package de.regepower.mindnschanger

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.method.DigitsKeyListener
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.IOException

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var serverName: TextView
    private lateinit var serverAddr: TextView
    private lateinit var deleteBtn: ImageButton
    private lateinit var appsBtn: Button
    private lateinit var appsMode: TextView
    private val listener: () -> Unit = { renderState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(8), px(16), px(16))
        }

        content.addView(
            appHeader(
                iconButton(R.drawable.ic_save, getString(R.string.cfg_save)) { saveConfig() },
                iconButton(R.drawable.ic_load, getString(R.string.cfg_load)) { loadConfig() },
                iconButton(R.drawable.ic_help, getString(R.string.help)) { showHelp() }
            ),
            fullWidth()
        )

        // Status + on/off
        val statusCard = card()
        status = TextView(this).apply { textSize = 18f }
        statusCard.addView(status)
        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        statusCard.addView(toggle, fullWidth(8))
        content.addView(statusCard, fullWidth(8))

        // DNS server: only the active one; tap = choose, + = add, - = delete own entry
        content.addView(header(getString(R.string.header_server)))
        val serverCard = card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(4), px(4), px(4))
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(6), px(8), px(6))
            setBackgroundResource(rowRipple())
            tooltipText = getString(R.string.hint_server)
            setOnClickListener { pickServer() }
        }
        serverName = TextView(this).apply {
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        serverAddr = TextView(this).apply {
            textSize = 13f
            setTextColor(getColor(R.color.md_on_surface_variant))
        }
        info.addView(serverName)
        info.addView(serverAddr)
        serverCard.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        serverCard.addView(
            iconButton(R.drawable.ic_add, getString(R.string.btn_add_server)) { addServerDialog() },
            LinearLayout.LayoutParams(px(44), px(44))
        )
        deleteBtn = iconButton(R.drawable.ic_remove, getString(R.string.btn_delete)) {
            deleteServerDialog(prefs.current())
        }
        serverCard.addView(deleteBtn, LinearLayout.LayoutParams(px(44), px(44)))
        content.addView(serverCard, fullWidth())
        content.addView(hint(getString(R.string.hint_server)))

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
        content.addView(optionSwitch(R.string.opt_mobile, prefs.onMobile) { prefs.onMobile = it })
        content.addView(optionSwitch(R.string.opt_wifi, prefs.onWifi) { prefs.onWifi = it })
        content.addView(optionSwitch(R.string.opt_captive, prefs.pauseCaptive) { prefs.pauseCaptive = it })
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
        }
        renderServer()
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
        when (requestCode) {
            REQ_VPN -> if (resultCode == RESULT_OK) {
                DnsVpnService.start(this)
            } else {
                toast(R.string.err_vpn_denied)
            }
            REQ_SAVE -> data?.data?.takeIf { resultCode == RESULT_OK }?.let { writeConfig(it) }
            REQ_LOAD -> data?.data?.takeIf { resultCode == RESULT_OK }?.let { readConfig(it) }
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun saveConfig() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(ConfigIO.MIME)
            .putExtra(Intent.EXTRA_TITLE, "${getString(R.string.app_name)}.json")
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_SAVE)
    }

    private fun loadConfig() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(ConfigIO.MIME, "text/plain", "application/octet-stream"))
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_LOAD)
    }

    private fun writeConfig(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(ConfigIO.toJson(prefs.sp, getString(R.string.app_name)).toByteArray())
            }
            toast(R.string.cfg_saved)
        } catch (e: IOException) {
            Log.w("MinDNS", "save config", e)
            toast(R.string.cfg_error)
        }
    }

    private fun readConfig(uri: Uri) {
        val json = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            Log.w("MinDNS", "load config", e)
            null
        }
        // Files saved before the rename carry the old app name.
        val ok = json != null &&
            (
                ConfigIO.fromJson(prefs.sp, json, getString(R.string.app_name)) ||
                    ConfigIO.fromJson(prefs.sp, json, OLD_NAME)
                )
        if (!ok) {
            toast(R.string.cfg_invalid)
            return
        }
        toast(R.string.cfg_loaded)
        if (DnsVpnService.running) DnsVpnService.start(this)
        recreate()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.help)
            .setMessage(getText(R.string.help_text))
            .setPositiveButton(R.string.help_ok, null)
            .show()
    }

    /** Switch that stores [save] and re-applies the rules to a running service. */
    private fun optionSwitch(textRes: Int, checked: Boolean, save: (Boolean) -> Unit) = Switch(this).apply {
        text = getString(textRes)
        textSize = 15f
        isChecked = checked
        setPadding(px(4), px(4), px(4), px(4))
        layoutParams = fullWidth()
        setOnCheckedChangeListener { _, on ->
            save(on)
            if (DnsVpnService.running) DnsVpnService.start(this@MainActivity)
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
        val paused = DnsVpnService.pausedReason
        if (active != null || paused != null) {
            status.text = if (active != null) {
                getString(R.string.state_on, active.name)
            } else {
                getString(R.string.state_paused, getString(paused!!))
            }
            toggle.text = getString(R.string.btn_stop)
            styleButton(toggle, R.color.md_container, R.color.md_on_container)
        } else {
            status.text = getString(R.string.state_off)
            toggle.text = getString(R.string.btn_start)
            styleButton(toggle, R.color.md_primary, R.color.md_on_primary)
        }
    }

    private fun renderServer() {
        val s = prefs.current()
        serverName.text = s.name
        serverAddr.text = s.addresses
        deleteBtn.isEnabled = s.custom
        deleteBtn.alpha = if (s.custom) 1f else 0.3f
    }

    private fun selectServer(name: String) {
        prefs.selected = name
        renderServer()
        if (DnsVpnService.running) DnsVpnService.start(this)
    }

    /** List of all servers in a dialog; "Add" opens the form. */
    private fun pickServer() {
        val list = prefs.servers()
        // Name on the first line, both addresses smaller and dimmed below.
        val labels = list.map { srv ->
            val text = "${srv.name}\n${srv.addresses}"
            SpannableString(text).apply {
                val start = srv.name.length + 1
                setSpan(RelativeSizeSpan(0.75f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(
                    ForegroundColorSpan(getColor(R.color.md_on_surface_variant)),
                    start,
                    text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }.toTypedArray<CharSequence>()
        val checked = list.indexOfFirst { it.name == prefs.current().name }
        AlertDialog.Builder(this)
            .setTitle(R.string.header_server)
            .setSingleChoiceItems(labels, checked) { d, which ->
                selectServer(list[which].name)
                d.dismiss()
            }
            .setNeutralButton(R.string.btn_add_server) { _, _ -> addServerDialog() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
            selectServer(n)
            dialog.dismiss()
        }
    }

    private fun deleteServerDialog(s: DnsServer) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dlg_delete_title, s.name))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                prefs.custom = prefs.custom.filter { it.name != s.name }
                selectServer(DnsServer.PRESETS[0].name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val OLD_NAME = "MinDNSChanger"
        private const val REQ_VPN = 1
        private const val REQ_NOTIFY = 2
        private const val REQ_SAVE = 3
        private const val REQ_LOAD = 4
    }
}
