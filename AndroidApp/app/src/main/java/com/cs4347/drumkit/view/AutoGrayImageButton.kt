package com.cs4347.drumkit.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton


class AutoGrayImageButton: ImageButton {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    override fun setEnabled(enabled: Boolean) {
        if (this.isEnabled != enabled) {
            this.imageAlpha = if (enabled) 0xFF else 0x3F
        }
        super.setEnabled(enabled)
    }
}