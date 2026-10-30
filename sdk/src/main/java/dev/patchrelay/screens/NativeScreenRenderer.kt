package dev.patchrelay.screens

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Keeps keyed native views alive across events, including input focus and selection. */
class NativeScreenRenderer(private val context: Context, private val onAction: (String, String?) -> Unit) {
    private val views = mutableMapOf<String, Pair<String, View>>()
    private var rendering = false
    private val column = layout()
    private val scroll = ScrollView(context).apply { isFillViewport = true; addView(column) }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun layout() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }

    fun build(tree: ScreenTree): View {
        rendering = true
        val used = mutableSetOf<String>()
        fun render(node: ScreenNode): View {
            used.add(node.id)
            val view = views[node.id]?.takeIf { it.first == node.type }?.second ?: when (node.type) {
                "column", "row", "card" -> layout().apply {
                    if (node.type == "row") orientation = LinearLayout.HORIZONTAL
                    if (node.type == "card") background = GradientDrawable().apply {
                        setColor(0xfff0f3f8.toInt()); cornerRadius = dp(12).toFloat()
                    }
                }
                "text" -> TextView(context)
                "button" -> Button(context).apply { isAllCaps = false; minHeight = dp(48) }
                "toggle" -> CheckBox(context).apply { minHeight = dp(48) }
                "input" -> EditText(context).apply {
                    isSingleLine = true
                    filters = arrayOf(InputFilter.LengthFilter(1024))
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            if (!rendering) onAction(tag as String, s.toString())
                        }
                        override fun afterTextChanged(s: Editable?) {}
                    })
                }
                else -> error("Unsupported validated widget")
            }.also { views[node.id] = node.type to it }
            view.isEnabled = node.enabled
            when (view) {
                is LinearLayout -> reconcile(view, node.children.map(::render))
                is EditText -> {
                    view.tag = node.action; view.hint = node.text; view.contentDescription = node.text
                    if (view.text.toString() != node.value) {
                        val cursor = view.selectionStart.coerceAtLeast(0).coerceAtMost(node.value.length)
                        view.setText(node.value); view.setSelection(cursor)
                    }
                }
                is CheckBox -> {
                    view.text = node.text; view.isChecked = node.checked
                    view.setOnCheckedChangeListener { _, checked -> if (!rendering) onAction(node.action, checked.toString()) }
                }
                is Button -> { view.text = node.text; view.setOnClickListener { onAction(node.action, null) } }
                is TextView -> {
                    view.text = node.text
                    view.textSize = when (node.style) { "title" -> 28f; "caption" -> 14f; else -> 18f }
                    view.setTextColor(if (node.style == "error") 0xffb3261e.toInt() else 0xff152033.toInt())
                    view.setTypeface(null, if (node.style == "title") Typeface.BOLD else Typeface.NORMAL)
                    if (android.os.Build.VERSION.SDK_INT >= 28) view.isAccessibilityHeading = node.style == "title"
                }
            }
            return view
        }
        try {
            reconcile(column, tree.children.map(::render))
            views.keys.retainAll(used)
            return scroll
        } finally { rendering = false }
    }

    private fun reconcile(parent: LinearLayout, children: List<View>) {
        for (index in parent.childCount - 1 downTo 0) {
            if (parent.getChildAt(index) !in children) parent.removeViewAt(index)
        }
        children.forEachIndexed { index, child ->
            if (parent.getChildAt(index) !== child) {
                (child.parent as? ViewGroup)?.removeView(child)
                parent.addView(child, index)
            }
            child.layoutParams = LinearLayout.LayoutParams(
                if (parent.orientation == LinearLayout.HORIZONTAL) 0 else -1, -2,
                if (parent.orientation == LinearLayout.HORIZONTAL) 1f else 0f).apply { bottomMargin = dp(12) }
        }
    }
}
