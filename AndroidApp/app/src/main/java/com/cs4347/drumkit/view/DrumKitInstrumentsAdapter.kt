package com.cs4347.drumkit.view

import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.cs4347.drumkit.R
import java.lang.IllegalStateException


class DrumKitRowViewHolder(viewItem: View): RecyclerView.ViewHolder(viewItem) {
    val nameTextView: TextView = viewItem.findViewById(R.id.instrument_name_textview)
    val beatsRecyclerView: RecyclerView = viewItem.findViewById(R.id.instrument_beats_rv)
}

interface RowSelectionListener {
    fun onRowSelected(row: Int)
}

/**
 * Expected to have stable ids
 * No views are recycled
 */
class DrumKitInstrumentsAdapter(private val instrumentNames: List<Pair<String, Int>>,
                                private val listener: RowSelectionListener):
        RecyclerView.Adapter<DrumKitRowViewHolder>() {

    private var prevBoldView: TextView? = null

    companion object {
        const val COLUMNS = 16
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrumKitRowViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val instrumentView = inflater.inflate(R.layout.view_instrument_row, parent, false)
        val vh = DrumKitRowViewHolder(instrumentView)

        vh.beatsRecyclerView.apply {
            this.adapter = BeatsAdapter(COLUMNS)
            this.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        instrumentView.setOnClickListener {
            val rowSelected = vh.itemId.toInt()
            listener.onRowSelected(rowSelected)
            prevBoldView?.let {
                toggleSelectionEffect(it, false)
            }
            toggleSelectionEffect(vh.nameTextView, true)
            prevBoldView = vh.nameTextView
        }

        return vh
    }

    private fun toggleSelectionEffect(textView: TextView, turnOn: Boolean) {
        textView.setAllCaps(turnOn)
    }

    override fun getItemCount(): Int {
        return instrumentNames.size
    }

    override fun onBindViewHolder(holder: DrumKitRowViewHolder, position: Int) {
        val (name, colorId) = instrumentNames[position]
        holder.nameTextView.text = name
        holder.nameTextView.background = ContextCompat.getDrawable(holder.nameTextView.context, colorId)
    }

    override fun onViewRecycled(holder: DrumKitRowViewHolder?) {
        super.onViewRecycled(holder)
        throw IllegalStateException("Row in Instruments RecycleView is recycled! This shouldn't happen!")
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        // hack to make all view items 'unique'
        // unique items are never recycled
        return position
    }
}

