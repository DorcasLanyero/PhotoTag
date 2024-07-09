package com.sdgsystems.collector.photos.ui.fragment

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.TextInputEditText
import com.sdgsystems.blueloggerclient.SDGLog
import com.sdgsystems.collector.photos.Constants
import com.sdgsystems.collector.photos.R

class AdminPINDialogFragment: AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val v = inflater.inflate(R.layout.pin_dialog, null)

        val builder = AlertDialog.Builder(context)
        builder.setView(v)

        builder.setPositiveButton("Submit", null)
        builder.setNegativeButton("Cancel") { dialogInterface: DialogInterface, _: Int ->
            listener.onFail()
            requireActivity().finish()
        }
        isCancelable = false
        val a = builder.create()
        a.setCanceledOnTouchOutside(false)

        return a
    }

    override fun onStart() {
        super.onStart()
        val d = dialog!! as AlertDialog
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val pinInput = d.findViewById<TextInputEditText>(R.id.txtPin)
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            SDGLog.d(tag, "Entered password: ${pinInput.text}")
            if (pinInput.text!!.isNotEmpty() && pinInput.text.toString() == prefs.getString(Constants.PREF_ADMIN_PIN, "")) {
                dialog!!.dismiss()
                listener.onSuccess()
                SDGLog.d(tag, "Good PIN")
            }
            else {
                pinInput.setHint("Invalid PIN")
                pinInput.setText("")
                listener.onFail()
                SDGLog.d(tag, "Bad PIN")
            }
        }

        SDGLog.d(tag, "Overwrite onclick")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        attachListener(context)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        attachListener(activity)
    }

    private fun attachListener(context: Context) {
        if(context is PINDialogFragmentListener) {
            listener = context
        }
        else {
            throw IllegalStateException("Launched AdminPINDialog from invalid host")
        }
    }

    private lateinit var listener: PINDialogFragmentListener

    companion object {
        const val tag: String = "PINDialog"
    }
}

interface PINDialogFragmentListener {
    fun onSuccess()
    fun onFail()
}