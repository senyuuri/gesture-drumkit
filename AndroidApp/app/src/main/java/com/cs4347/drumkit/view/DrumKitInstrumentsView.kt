package com.cs4347.drumkit.view

import android.content.Context
import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import android.widget.SeekBar
import com.cs4347.drumkit.R


class DrumKitInstrumentsView: ConstraintLayout {

    lateinit var seekBar: SeekBar
    private lateinit var seekLine: View
    lateinit var instrumentsRecycler: RecyclerView

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {

        LayoutInflater.from(context).inflate(R.layout.view_drumkit_instruments_view, this, true)

        seekLine = findViewById(R.id.seek_line)
        seekBar = findViewById(R.id.seek_bar)
        instrumentsRecycler = findViewById(R.id.instruments_rv)

        // put a larger max, so small updates can be seen
        // set a dummy listener so the seek line follows the seekbar thumb
        this.setSeekBarOnChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }

    fun setSeekBarOnChangeListener(listener: SeekBar.OnSeekBarChangeListener) {
        seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                val width = seekBar.width - seekBar.paddingLeft - seekBar.paddingRight
                // offset of 3 somehow makes it look better
                val thumbPos = seekBar.x + seekBar.paddingStart + width * seekBar.progress.toFloat() / seekBar.max - 3
                seekLine.x = thumbPos
                listener.onProgressChanged(p0, p1, p2)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                listener.onStartTrackingTouch(p0)
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                listener.onStopTrackingTouch(p0)
            }
        })
    }

}