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

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.lang.Exception
import java.util.ArrayList
import java.util.Collections
import org.devmiyax.yabasanshiro.R

/**
 * A fragment representing a list of Items.
 *
 *
 * Activities containing this fragment MUST implement the [OnListFragmentInteractionListener]
 * interface.
 */
class CloudCheatItemFragment
/**
 * Mandatory empty constructor for the fragment manager to instantiate the
 * fragment (e.g. upon screen orientation changes).
 */
    : Fragment(), CloudCheatItemRecyclerViewAdapter.OnItemClickListener {
    private var mColumnCount = 1
    private var mListener: OnListFragmentInteractionListener? = null
    private var _items: ArrayList<CheatItem?>? = null
    private var mGameCode: String? = null
    var database_: DatabaseReference? = null
    var root_view_: View? = null
    var listview_: RecyclerView? = null
    var adapter_: CloudCheatItemRecyclerViewAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mColumnCount = requireArguments().getInt(ARG_COLUMN_COUNT)
        mGameCode = requireArguments().getString(ARG_GAME_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_cloudcheatitem_list, container, false)
        listview_ = view.findViewById<View>(R.id.list) as RecyclerView
        root_view_ = view
        updateCheatList()
        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnListFragmentInteractionListener) {
            mListener = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    val tabCheatFragmentInstance: TabCheatFragment?
        get() {
            var xFragment: TabCheatFragment? = null
            for (fragment in requireFragmentManager().fragments) {
                if (fragment is TabCheatFragment) {
                    xFragment = fragment
                    break
                }
            }
            return xFragment
        }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments](http://developer.android.com/training/basics/fragments/communicating.html) for more information.
     */
    internal interface OnListFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onListFragmentInteraction(item: CheatItem?)
    }

    fun updateCheatList() {
        _items = ArrayList()
        val baseref = FirebaseDatabase.getInstance().reference
        val baseurl = "/shared-cheats/$mGameCode"
        database_ = baseref.child(baseurl)
        if (database_ == null) {
            return
        }
        adapter_ = CloudCheatItemRecyclerViewAdapter(_items, this@CloudCheatItemFragment)
        listview_!!.adapter = adapter_
        val DataListener: ValueEventListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.hasChildren()) {
                    _items!!.clear()
                    for (child in dataSnapshot.children) {
                        try {
                            val newitem = child.getValue(CheatItem::class.java)
                            newitem!!.key = child.key!!
                            val frag = tabCheatFragmentInstance
                            if (frag != null) {
                                newitem.enable = frag.isActive(newitem.cheat_code)
                            }
                            _items!!.add(newitem)
                        } catch (e: Exception) {
                        }
                    }
                    _items?.let { Collections.reverse(it) }
                    adapter_ =
                        CloudCheatItemRecyclerViewAdapter(_items, this@CloudCheatItemFragment)
                    listview_!!.adapter = adapter_
                    listview_!!.post {
                        adapter_!!.notifyDataSetChanged()
                    }
                } else {
                    Log.e(TAG, "Bad Data " + dataSnapshot.key)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e(TAG, "onCancelled " + databaseError.message)
            }
        }
        database_!!.orderByChild("star_count").addValueEventListener(DataListener)
    }

    override fun onItemClick(position: Int, item: CheatItem?, v: View?, isLikeButton: Boolean) {
        if (item == null) return

        if (isLikeButton) {
            toggleLike(item)
        } else {
            toggleEnable(item)
        }
    }

    private fun toggleEnable(item: CheatItem) {
        item.enable = !item.enable
        val frag = tabCheatFragmentInstance
        if (frag != null) {
            if (item.enable) {
                frag.AddActiveCheat(item.cheat_code)
            } else {
                frag.RemoveActiveCheat(item.cheat_code)
            }
        }
        
        // Switchからの呼び出しの場合はUIを更新しない（無限ループを防ぐため）
        listview_?.post {
            adapter_?.notifyDataSetChanged()
        }
    }

    private fun toggleLike(item: CheatItem) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            return
        }
        val userId = auth.currentUser!!.uid
        val likeRef = database_!!.child(item.key).child("like_users").child(userId)
        val itemRef = database_!!.child(item.key)

        likeRef.get().addOnSuccessListener { dataSnapshot ->
            if (dataSnapshot.exists()) {
                // Unlike
                likeRef.removeValue()
                itemRef.child("star_count").runTransaction(object : Transaction.Handler {
                    override fun doTransaction(mutableData: com.google.firebase.database.MutableData): Transaction.Result {
                        val value = (mutableData.value as? Long ?: 0).toInt()
                        mutableData.value = Math.max(0, value - 1)
                        return Transaction.success(mutableData)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        listview_?.post {
                            adapter_?.notifyDataSetChanged()
                        }
                    }
                })
            } else {
                // Like
                likeRef.setValue(true)
                itemRef.child("star_count").runTransaction(object : Transaction.Handler {
                    override fun doTransaction(mutableData: com.google.firebase.database.MutableData): Transaction.Result {
                        val value = (mutableData.value as? Long ?: 0).toInt()
                        mutableData.value = value + 1
                        return Transaction.success(mutableData)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        listview_?.post {
                            adapter_?.notifyDataSetChanged()
                        }
                    }
                })
            }
        }
    }

    companion object {
        private const val TAG = "CloudCheatItemFragment"
        private const val ARG_COLUMN_COUNT = "column-count"
        private const val ARG_GAME_ID = "game_id"
        @JvmStatic
        fun newInstance(gameid: String?, columnCount: Int): CloudCheatItemFragment {
            val fragment = CloudCheatItemFragment()
            val args = Bundle()
            args.putString(ARG_GAME_ID, gameid)
            args.putInt(ARG_COLUMN_COUNT, columnCount)
            fragment.arguments = args
            return fragment
        }
    }
}
