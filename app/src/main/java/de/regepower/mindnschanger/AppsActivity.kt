package de.regepower.mindnschanger

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/** App selector from BootDelay (search, selected/available lists), without drag sorting, plus filter mode. */
class AppsActivity : Activity() {
    private class AppItem(val label: String, val pkg: String, val icon: Drawable, var selected: Boolean)

    private lateinit var prefs: Prefs
    private lateinit var selectedHeader: TextView
    private val selectedAdapter = AppAdapter()
    private val availableAdapter = AppAdapter()
    private var all: List<AppItem> = emptyList()
    private val chosen = mutableSetOf<String>()
    private var query = ""
    private var changed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val pad = px(8)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            fitsSystemWindows = true
        }

        val black = RadioButton(this).apply {
            id = ID_BLACK
            text = getString(R.string.mode_blacklist)
        }
        val white = RadioButton(this).apply {
            id = ID_WHITE
            text = getString(R.string.mode_whitelist)
        }
        val mode = RadioGroup(this).apply {
            addView(black)
            addView(white)
            check(if (prefs.whitelist) ID_WHITE else ID_BLACK)
            setOnCheckedChangeListener { _, id ->
                prefs.whitelist = id == ID_WHITE
                changed = true
            }
        }
        // No window title bar (theme): own bold title like the main screen.
        root.addView(
            TextView(this).apply {
                text = getString(R.string.header_apps)
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.md_on_container))
                setPadding(px(4), px(4), 0, px(4))
            }
        )
        root.addView(mode)
        root.addView(hint(getString(R.string.hint_apps)))

        val search = EditText(this).apply {
            hint = getString(R.string.search_hint)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty().trim().lowercase(Locale.getDefault())
                    refreshLists()
                }
            })
        }
        root.addView(search)

        selectedHeader = header(getString(R.string.header_selected, 0))
        root.addView(selectedHeader)
        root.addView(appList(selectedAdapter), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(header(getString(R.string.header_available)))
        root.addView(
            appList(availableAdapter),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.4f)
        )
        setContentView(root)
        loadApps()
    }

    override fun onPause() {
        save()
        // Apply new filter to a running tunnel.
        if (changed && DnsVpnService.running) DnsVpnService.start(this)
        changed = false
        super.onPause()
    }

    private fun appList(a: AppAdapter) = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = a
        setBackgroundResource(R.drawable.bg_card)
        clipToOutline = true
        val p = dp.toInt()
        setPadding(p, p, p, p)
        clipToPadding = true
    }

    private fun toggle(item: AppItem) {
        item.selected = !item.selected
        if (item.selected) chosen.add(item.pkg) else chosen.remove(item.pkg)
        changed = true
        save()
        refreshLists()
    }

    private fun refreshLists() {
        val byLabel = compareBy<AppItem> { it.label.lowercase(Locale.getDefault()) }
        selectedAdapter.set(all.filter { it.selected }.sortedWith(byLabel))
        selectedHeader.text = getString(R.string.header_selected, selectedAdapter.itemCount)
        availableAdapter.set(
            all.filter { !it.selected }
                .filter {
                    query.isEmpty() || it.label.lowercase(Locale.getDefault()).contains(query) || it.pkg.contains(query)
                }
                .sortedWith(byLabel)
        )
    }

    private fun save() {
        if (all.isNotEmpty()) prefs.packages = chosen.toList()
    }

    private fun loadApps() {
        chosen.clear()
        chosen.addAll(prefs.packages)
        val selected = chosen.toSet()
        Thread {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val items = packageManager.queryIntentActivities(launcher, 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .map {
                    AppItem(
                        it.loadLabel(packageManager).toString(),
                        it.activityInfo.packageName,
                        it.loadIcon(packageManager),
                        it.activityInfo.packageName in selected
                    )
                }
            runOnUiThread {
                all = items
                chosen.retainAll(items.map { it.pkg }.toSet())
                refreshLists()
            }
        }.start()
    }

    private inner class AppAdapter : RecyclerView.Adapter<AppAdapter.Holder>() {
        private val items = mutableListOf<AppItem>()

        inner class Holder(
            row: LinearLayout,
            val icon: ImageView,
            val name: TextView,
            val pkg: TextView,
            val check: CheckBox
        ) : RecyclerView.ViewHolder(row)

        @Suppress("NotifyDataSetChanged")
        fun set(newItems: List<AppItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            val p = px(6)
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(p, p, p, p)
                layoutParams = RecyclerView.LayoutParams(-1, -2)
                isClickable = true
                isFocusable = true
                setBackgroundResource(rowRipple())
            }
            val size = px(36)
            val icon = ImageView(ctx)
            row.addView(icon, LinearLayout.LayoutParams(size, size))
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val name = TextView(ctx).apply { textSize = 15f }
            val pkg = TextView(ctx).apply {
                textSize = 10f
                alpha = 0.6f
            }
            texts.addView(name)
            texts.addView(pkg)
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(10) })
            val check = CheckBox(ctx).apply {
                isClickable = false
                isFocusable = false
            }
            row.addView(check)
            val holder = Holder(row, icon, name, pkg, check)
            row.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos >= 0) toggle(items[pos])
            }
            return holder
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.name.text = item.label
            holder.pkg.text = item.pkg
            holder.check.isChecked = item.selected
        }
    }

    companion object {
        private const val ID_BLACK = 101
        private const val ID_WHITE = 102
    }
}
