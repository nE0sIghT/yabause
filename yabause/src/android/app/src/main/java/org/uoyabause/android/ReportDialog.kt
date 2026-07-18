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
package org.uoyabause.android

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RatingBar
import android.widget.RatingBar.OnRatingBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.semantics.text
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.devmiyax.yabasanshiro.R

class ReportDialog(private val activity: Context, val productionNumber: String) : BottomSheetDialogFragment() {
    var _emulationRating: RatingBar? = null
    var _gameRating: RatingBar? = null
    var _rateText: TextView? = null
    var _gameRateText: TextView? = null
    var _edt: EditText? = null
    var _chk: CheckBox? = null
    private var sendButton: ImageButton? = null

    interface OnReportFinishedListener {
        fun onFinishReport(rating: Int, message: String?, screenshot: Boolean)
    }

    private var onReportFinishedListener: OnReportFinishedListener? = null

    fun setOnReportFinishedListener(listener: (rating: Int, message: String?, screenshot: Boolean) -> Unit) {
        this.onReportFinishedListener = object : OnReportFinishedListener {
            override fun onFinishReport(rating: Int, message: String?, screenshot: Boolean) {
                listener(rating, message, screenshot)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.report, container, false)

        val reviewNoticeTextView = view.findViewById<TextView>(R.id.review_notice)
        reviewNoticeTextView.text = Html.fromHtml(getString(R.string.report_notice), Html.FROM_HTML_MODE_LEGACY)
        reviewNoticeTextView.movementMethod = LinkMovementMethod.getInstance()

        _gameRating = view.findViewById(R.id.game_ratingBar)
        _gameRating?.apply {
            numStars = 5
            rating = 3.0f
            stepSize = 1.0f
            onRatingBarChangeListener = OnRatingBarChangeListener { ratingBar, rating, _ ->
                val iRate = rating.toInt()
                if (rating == 0f) {
                    ratingBar.rating = 1f
                }
                when (iRate) {
                    1 -> _gameRateText?.setText(R.string.game_report_message_1)
                    2 -> _gameRateText?.setText(R.string.game_report_message_2)
                    3 -> _gameRateText?.setText(R.string.game_report_message_3)
                    4 -> _gameRateText?.setText(R.string.game_report_message_4)
                    5 -> _gameRateText?.setText(R.string.game_report_message_5)
                    else -> {}
                }
            }
        }


        _emulationRating = view.findViewById(R.id.emulation_ratingBar)
        _emulationRating?.apply {
            numStars = 5
            rating = 3.0f
            stepSize = 1.0f
            onRatingBarChangeListener = OnRatingBarChangeListener { ratingBar, rating, _ ->
                val iRate = rating.toInt()
                if (rating == 0f) {
                    ratingBar.rating = 1f
                }
                when (iRate) {
                    1 -> _rateText?.setText(R.string.report_message_1)
                    2 -> _rateText?.setText(R.string.report_message_2)
                    3 -> _rateText?.setText(R.string.report_message_3)
                    4 -> _rateText?.setText(R.string.report_message_4)
                    5 -> _rateText?.setText(R.string.report_message_5)
                    else -> {}
                }
            }
        }
        
        _edt = view.findViewById(R.id.report_message)
        //_chk = view.findViewById(R.id.report_Screenshot)
        _rateText = view.findViewById(R.id.emulation_rateString)
        _rateText?.setText(R.string.report_message_3)
        _gameRateText = view.findViewById(R.id.game_rateString)
        _gameRateText?.setText(R.string.game_report_message_3)

        sendButton = view.findViewById(R.id.send_button)
        
        //onRatingChanged(_emulationRating!!, 3.0f, false)
        
        sendButton?.setOnClickListener {
            handleSendClick()
        }

        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    private fun handleSendClick() {
        if (FirebaseAuth.getInstance().currentUser == null) {
            return
        }

        val emulationRating = _emulationRating!!.rating.toInt()
        val gameRating = _gameRating!!.rating.toInt()
        val message = _edt!!.text.toString()
        
        // Get production number from YabauseRunnable
        //val productionNumber = YabauseRunnable.getCurrentGameCode()
        
        // Initialize Firestore
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser!!
        val userId = currentUser.uid
        
        // Create rating document data
        val ratingData = hashMapOf(
            "rating" to gameRating,
            "emulation_rating" to emulationRating,
            "comment" to message,
            "uid" to userId,
            "display_name" to currentUser.displayName,
            "photo_url" to currentUser.photoUrl.toString(),
            "platform" to "android",
            "version" to YabauseApplication.getVersionName(),
            "version_code" to YabauseApplication.getVersionCode(),
            "timestamp" to FieldValue.serverTimestamp(),
            "isVisible" to true
        )
        
        // 1. Search in games collection by production_number
        db.collection("games")
            .whereEqualTo("product_number", productionNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Game not found, create a new game document first
                    val gameData = hashMapOf(
                        "product_number" to productionNumber,
                        "created_at" to FieldValue.serverTimestamp()
                    )
                    
                    db.collection("games")
                        .add(gameData)
                        .addOnSuccessListener { gameDocRef ->
                            // 3. Add rating to the ratings subcollection with userId as document ID
                            gameDocRef.collection("ratings")
                                .document(userId)
                                .set(ratingData)
                                .addOnSuccessListener {
                                    // Report submitted successfully
                                    Toast.makeText(activity, R.string.report_sent_success, Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    // Handle error
                                    Toast.makeText(activity, R.string.report_sent_failed, Toast.LENGTH_SHORT).show()
                                    Log.e("ReportDialog", "Error adding rating document", e)
                                }
                        }
                        .addOnFailureListener { e ->
                            // Handle error
                            Toast.makeText(activity, R.string.report_sent_failed, Toast.LENGTH_SHORT).show()
                            Log.e("ReportDialog", "Error creating game document", e)
                        }
                } else {
                    // Game exists, add rating to its ratings subcollection with userId as document ID
                    val gameDoc = documents.documents[0]
                    gameDoc.reference.collection("ratings")
                        .document(userId)
                        .set(ratingData)
                        .addOnSuccessListener {
                            // Report submitted successfully
                            Toast.makeText(activity, R.string.report_sent_success, Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            // Handle error
                            Toast.makeText(activity, R.string.report_sent_failed, Toast.LENGTH_SHORT).show()
                            Log.e("ReportDialog", "Error adding rating document", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                // Handle error
                Toast.makeText(activity, R.string.report_sent_failed, Toast.LENGTH_SHORT).show()
                Log.e("ReportDialog", "Error querying games collection", e)
            }

        onReportFinishedListener?.onFinishReport(gameRating, message, false)
/*
        activity?.doReportCurrentGame(
            rating = 1,
            message = "sss",
            screenshot = false
        )
*/
        dismiss()
    }

}
