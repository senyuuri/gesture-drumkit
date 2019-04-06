package com.cs4347.drumkit.view

import android.opengl.Visibility
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.cs4347.drumkit.R

class BeatViewHolder(viewItem: View): RecyclerView.ViewHolder(viewItem) {
    val selectionBox: View = viewItem.findViewById(R.id.selection_box)
}

class BeatsAdapter(private val numBeats: Int): RecyclerView.Adapter<BeatViewHolder>() {
    private var selectedCols = BooleanArray(numBeats)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BeatViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val beatView =
                when (viewType > 0 && viewType % 4 == 0) {
                    true -> inflater.inflate(R.layout.view_beat_extra_pad, parent, false)
                    false -> inflater.inflate(R.layout.view_beat, parent, false)
                }
        return BeatViewHolder(beatView)
    }

    override fun getItemCount(): Int {
        return numBeats
    }

    override fun onBindViewHolder(holder: BeatViewHolder, position: Int) {
        holder.selectionBox.visibility = when (selectedCols[position]) {
            true -> View.VISIBLE
            false -> View.GONE
        }
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    fun setColumn(col: Int, selected: Boolean) {
        selectedCols[col] = selected
        notifyItemChanged(col)
    }

    fun clearAll() {
        selectedCols = BooleanArray(numBeats)
        notifyDataSetChanged()
    }

}

