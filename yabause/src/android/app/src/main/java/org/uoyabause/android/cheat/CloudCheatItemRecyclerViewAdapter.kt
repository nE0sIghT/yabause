/*  Copyright 2019 devMiyax(smiyaxdev@gmail.com)

    This file is part of YabaSanshiro.

    YabaSanshiro is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    YabaSanshiro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with YabaSanshiro; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/
package org.uoyabause.android.cheat

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.switchmaterial.SwitchMaterial
import org.devmiyax.yabasanshiro.R

/**
 * [RecyclerView.Adapter] that can display a [CheatItem] and makes a call to the
 * specified [OnListFragmentInteractionListener].
 */
class CloudCheatItemRecyclerViewAdapter(
    private val mValues: List<CheatItem?>?,
    private var mListener: OnItemClickListener?
) : RecyclerView.Adapter<CloudCheatItemRecyclerViewAdapter.ViewHolder>() {
    private var focusedItem = 0

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        mListener = listener
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int, item: CheatItem?, v: View?, isLikeButton: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_cloudcheatitem, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        holder.mItem = mValues?.get(position)
        holder.mIdView.text = mValues?.get(position)?.description
        holder.mContentView.text = mValues?.get(position)?.cheat_code
        holder.itemView.isSelected = focusedItem == position
        
        if (focusedItem == position) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(
                holder.itemView.context,
                R.color.colorPrimaryDark
            ))
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(
                holder.itemView.context,
                R.color.halfTransparent
            ))
        }

        holder.mLikeCount.text = holder.mItem?.star_count?.toString() ?: "0"
        
        // Set enable state
        holder.mSwitchEnable.isChecked = holder.mItem?.enable == true

        holder.mSwitchEnable.setOnCheckedChangeListener { _ , isChecked ->
            if (null != mListener) {
                if( isChecked != holder.mItem?.enable ) {
                    mListener!!.onItemClick(position, holder.mItem, holder.mView, false)
                }
            }
        }

        holder.mLikeButton.setOnClickListener {
            if (null != mListener) {
                mListener!!.onItemClick(position, holder.mItem, holder.mView, true)
            }
        }

        holder.mView.setOnClickListener {
            focusedItem = position
            
            // ダイアログを作成して表示
            val context = holder.mView.context
            val enableText = if (holder.mSwitchEnable.isChecked == true) "無効化する" else "有効化する"
            val options = arrayOf("評価する/しない", enableText)
            
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(holder.mItem?.description)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> { // 評価する/しない
                            if (null != mListener) {
                                mListener!!.onItemClick(position, holder.mItem, holder.mView, true)
                            }
                        }
                        1 -> { // 有効化する/しない
                            if (null != mListener) {
                                mListener!!.onItemClick(position, holder.mItem, holder.mView, false)
                            }
                        }
                    }
                }
                .show()
        }
    }

    override fun getItemCount(): Int {
        return if (mValues == null) 0 else mValues.size
    }

    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        val mIdView: TextView = mView.findViewById(R.id.id)
        val mContentView: TextView = mView.findViewById(R.id.content)
        val mLikeButton: ImageButton = mView.findViewById(R.id.button_like)
        val mLikeCount: TextView = mView.findViewById(R.id.text_like_count)
        val mSwitchEnable: MaterialSwitch = mView.findViewById(R.id.switch_enable)
        var mItem: CheatItem? = null

        override fun toString(): String {
            return super.toString() + " '" + mContentView.text + "'"
        }
    }
}
