package dev.patchrelay.screens

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Renders an already validated tree using installed Android widgets. */
class NativeScreenRenderer(private val context: Context, private val onAction: (String) -> Unit) {
    fun build(tree: ScreenTree): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        tree.children.forEach { node ->
            val view = when (node) {
                is ScreenNode.Text -> TextView(context).apply {
                    text = node.text
                    textSize = if (node.heading) 28f else 19f
                    setTextColor(0xff152033.toInt())
                    if (node.heading) {
                        if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
                        setTypeface(typeface, 1)
                    }
                }
                is ScreenNode.Button -> Button(context).apply {
                    text = node.text
                    isAllCaps = false
                    setOnClickListener { onAction(node.action) }
                }
            }
            column.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (16 * context.resources.displayMetrics.density).toInt()
            })
        }
        return ScrollView(context).apply { addView(column) }
    }
}
