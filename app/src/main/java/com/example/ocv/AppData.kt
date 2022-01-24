package com.example.ocv

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

class AppData {
    companion object {

        private var debug: Boolean = true
        private var error: Boolean = true

        var dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREAN)

        fun showToast(context: Context, message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        fun debug(tag: String, msg: String) {
            if(debug) {
                Log.d(tag, msg)
            }
        }

        fun error(tag: String, msg: String) {
            if(error) {
                Log.e(tag,msg)
            }
        }

    }
}