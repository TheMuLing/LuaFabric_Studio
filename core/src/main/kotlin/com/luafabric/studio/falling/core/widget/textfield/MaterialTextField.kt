package com.luafabric.studio.falling.core.widget.textfield

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.Keep
import java.lang.CharSequence
import android.text.TextWatcher
import android.widget.FrameLayout
import android.content.res.ColorStateList
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.luafabric.studio.falling.core.R

@Keep
class MaterialTextField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val view = LayoutInflater.from(context).inflate(R.layout.textfield_layout, null)
    private val layout = view.findViewById<TextInputLayout>(R.id.textfield_layout)
    val edit = view.findViewById<TextInputEditText>(R.id.textfield_edit)

    var boxCornerRadii: Float? = null
        set(value) {
            value?.let {
                layout.setBoxCornerRadii(it, it, it, it)
            }
        }
        
    var endIconMode: Int? = null
        set(value) {
            value?.let {
                layout.setEndIconMode(it)
            }
        }
        
    init {
        addView(view)

        val attributes = context.obtainStyledAttributes(
            attrs, R.styleable.MaterialTextField
        )
        layout.hint = attributes.getString(R.styleable.MaterialTextField_android_hint)
        edit.setSingleLine(attributes.getBoolean(R.styleable.MaterialTextField_android_singleLine, false))
        boxCornerRadii = attributes.getFloat(R.styleable.MaterialTextField_boxCornerRadii, 16f)
        endIconMode = attributes.getInt(R.styleable.MaterialTextField_endIconMode, 0)
        attributes.recycle()
    }

    fun getEditText(): TextInputEditText {
        return edit
    }
    
    fun getLayout(): TextInputLayout {
        return layout
    }

    fun setError(str: String?) {
        layout.error = str
    }
    
    fun setError(str: Int?) {
        layout.error = str?.let { resources.getString(it) }
    }

    fun setHint(str: String) {
        layout.hint = str
    }
    
    fun setTextSize(str: Float) {
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, str)
    }
    
    fun setHelperText(helperText: String?) {
        layout.helperText = helperText
    }
    
    fun setSelection(start: Int, end: Int) {
        edit.setSelection(start, end)
    }
    
    override fun clearFocus() {
        super.clearFocus()
        edit.clearFocus()
    }
    
    override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean {
        return edit.requestFocus(direction, previouslyFocusedRect)
    }
    
    fun setEndIconMode(mode: Int) {
        layout.endIconMode = mode
    }
    
    fun setBoxCornerRadii(radius: Float) {
        layout.setBoxCornerRadii(radius, radius, radius, radius)
    }
    
    fun setText(text: String) {
        edit.setText(text)
    }
    
    fun setSingleLine(singleLine: Boolean) {
        edit.setSingleLine(singleLine)
    }
    
    fun setErrorEnabled(enabled: Boolean) {
        layout.isErrorEnabled = enabled
    }
    
    fun addTextChangedListener(listener: TextWatcher) {
        edit.addTextChangedListener(listener)
    }
    
    fun removeTextChangedListener(listener: TextWatcher) {
        edit.removeTextChangedListener(listener)
    }
    
    fun setStartIconTintList(color: Int) {
        layout.setStartIconTintList(ColorStateList.valueOf(color))
    }
    
    fun setStartIconDrawable(drawable: Drawable) {
        layout.setStartIconDrawable(drawable)
    }

    fun setStartIconDrawable(resId: Int) {
        layout.setStartIconDrawable(resId)
    }
        
    fun getText(): String {
        return edit.text.toString()
    }
}
