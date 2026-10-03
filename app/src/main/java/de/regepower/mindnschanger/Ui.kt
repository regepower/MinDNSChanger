package de.regepower.mindnschanger

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

/** Small code-built Material You helpers (no AndroidX/Material libraries). */
internal val Context.dp: Float get() = resources.displayMetrics.density

internal fun Context.px(v: Int): Int = (v * dp).toInt()

internal fun Context.styleButton(b: Button, fill: Int, text: Int) {
    b.setBackgroundResource(R.drawable.bg_btn)
    b.backgroundTintList = ColorStateList.valueOf(getColor(fill))
    b.setTextColor(getColor(text))
    b.isAllCaps = false
    b.stateListAnimator = null
    b.minHeight = 0
    b.minimumHeight = px(40)
}

internal fun Context.button(text: String, help: String?, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    if (help != null) tooltipText = help
    setOnClickListener { onClick() }
    styleButton(this, R.color.md_container, R.color.md_on_container)
}

internal fun Context.header(text: String) = TextView(this).apply {
    this.text = text
    textSize = 13f
    setTextColor(getColor(R.color.md_primary))
    setTypeface(typeface, Typeface.BOLD)
    setPadding(px(4), px(14), 0, px(4))
}

internal fun Context.hint(text: String) = TextView(this).apply {
    this.text = text
    textSize = 12f
    alpha = 0.7f
    setPadding(px(4), px(4), px(4), px(4))
}

internal fun Context.card() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setBackgroundResource(R.drawable.bg_card)
    clipToOutline = true
    setPadding(px(12), px(8), px(12), px(8))
}

internal fun Context.rowRipple(): Int {
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
    return tv.resourceId
}

internal fun Context.fullWidth(topMargin: Int = 0) =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        this.topMargin = px(topMargin)
    }

/** Borderless icon button tinted in the primary color, with tooltip/content description. */
internal fun Context.iconButton(icon: Int, desc: String, onClick: () -> Unit) = ImageButton(this).apply {
    setImageResource(icon)
    imageTintList = ColorStateList.valueOf(getColor(R.color.md_primary))
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
    setBackgroundResource(tv.resourceId)
    contentDescription = desc
    tooltipText = desc
    setOnClickListener { onClick() }
}

/** App header used by all our apps: large bold app name, action icons on the right (save, load, help). */
internal fun Context.appHeader(vararg actions: ImageButton) = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    addView(
        TextView(context).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.md_on_container))
        },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    )
    actions.forEach { addView(it, LinearLayout.LayoutParams(px(44), px(44))) }
}
