package com.luafabric.console.debug

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.luafabric.console.ui.dp

/**
 * 结构化参数表单：每参数 键 + 类型下拉（string/number/boolean/table，排除 userdata/thread/function）+ 值编辑。
 * table 类型以树状行编辑，可折叠展开。确认回调返回类型化 Map（→ JSON extra 注入 Lua 全局 debugParams）。
 */
class ParamFormDialog(
    private val context: Context,
    private val title: String,
    private val onConfirm: (Map<String, Any?>) -> Unit
) {

    companion object {
        private val TYPES = listOf("string", "number", "boolean", "table")
    }

    private val rowsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val rootRows = mutableListOf<ParamRow>()

    fun show() {
        addRow(rootRows, rowsContainer, "param1")
        val addButton = TextView(context).apply {
            text = "+ 添加参数"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, context.dp(8), 0, 0)
            setOnClickListener { addRow(rootRows, rowsContainer, "param${rootRows.size + 1}") }
        }
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                ScrollView(context).apply {
                    addView(
                        rowsContainer,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    )
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(360))
            )
            addView(addButton)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(wrapper, context.dp(16), context.dp(8), context.dp(16), context.dp(8))
            .setPositiveButton("调起") { _, _ -> onConfirm(buildMap(rootRows)) }
            .setNegativeButton("取消", null)
            .create()
            .show()
    }

    private fun addRow(owner: MutableList<ParamRow>, container: LinearLayout, keyHint: String): ParamRow {
        val row = ParamRow(owner, keyHint)
        owner.add(row)
        container.addView(row.root)
        return row
    }

    private fun buildMap(rows: List<ParamRow>): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        for (r in rows) {
            val key = r.keyEdit.text?.toString()?.trim().orEmpty()
            if (key.isEmpty()) continue
            map[key] = buildValue(r)
        }
        return map
    }

    private fun buildValue(r: ParamRow): Any? = when (r.currentType()) {
        "number" -> r.numberEdit.text?.toString()?.trim().orEmpty().let { s ->
            if (s.isEmpty()) null else s.toLongOrNull() ?: s.toDoubleOrNull()
        }
        "boolean" -> r.boolSpinner.selectedItemPosition == 0
        "table" -> buildMap(r.children)
        else -> r.stringEdit.text?.toString().orEmpty()
    }

    private inner class ParamRow(val owner: MutableList<ParamRow>, keyHint: String) {

        val keyEdit = EditText(context).apply {
            textSize = 13f
            hint = keyHint
            setText(keyHint)
        }
        val typeSpinner = Spinner(context)
        val stringEdit = EditText(context).apply {
            textSize = 13f
            hint = "值"
        }
        val numberEdit = EditText(context).apply {
            textSize = 13f
            hint = "数值"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val boolSpinner = Spinner(context)
        private val childToggle = TextView(context).apply {
            text = "收起"
            textSize = 12f
            visibility = View.GONE
            setOnClickListener {
                childContainer.visibility = if (childContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                text = if (childContainer.visibility == View.VISIBLE) "收起" else "展开"
            }
        }
        val childContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 0, 0, 0)
        }
        val children = mutableListOf<ParamRow>()
        private val removeBtn = TextView(context).apply {
            text = "✕"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(context.dp(8), 0, 0, 0)
            setOnClickListener {
                owner.remove(this@ParamRow)
                (root.parent as? ViewGroup)?.removeView(root)
            }
        }

        val root: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(keyEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(typeSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            addView(stringEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                            addView(numberEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                            addView(boolSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                    )
                    addView(removeBtn)
                }
            )
            addView(childToggle)
            addView(childContainer)
        }

        init {
            typeSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, TYPES).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            boolSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("true", "false")).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                    updateValueVisibility()

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            updateValueVisibility()
        }

        fun currentType(): String = TYPES[typeSpinner.selectedItemPosition]

        private fun updateValueVisibility() {
            val type = currentType()
            val str = type == "string"
            val num = type == "number"
            val bool = type == "boolean"
            val table = type == "table"
            stringEdit.visibility = if (str) View.VISIBLE else View.GONE
            numberEdit.visibility = if (num) View.VISIBLE else View.GONE
            boolSpinner.visibility = if (bool) View.VISIBLE else View.GONE
            childToggle.visibility = if (table) View.VISIBLE else View.GONE
            if (table) {
                if (children.isEmpty()) addRow(children, childContainer, "k1")
                childContainer.visibility = View.VISIBLE
                childToggle.text = "收起"
            }
        }
    }
}
