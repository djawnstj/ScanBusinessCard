package com.example.ocv

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import com.permissionx.guolindev.PermissionX
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AppData {
    companion object {

        private var debug: Boolean = true
        private var error: Boolean = true

        fun debug(tag: String, msg: String?) { if(debug) Log.d(tag, msg ?: "msg is null") }
        fun error(tag: String, msg: String?) { if(error) Log.e(tag, msg ?: "msg is null") }
        fun error(tag: String, msg: String?, ex: Exception?) { if(error) Log.e(tag, msg ?: "msg is null", ex) }

        var dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREAN)

        fun showToast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        // 권한 체크 함수
        fun checkSelfPermission(context: Context?, @NonNull permission: String): Boolean {
            debug(TAG, "checkSelfPermission() called.")
            return PermissionX.isGranted(context, permission)
        }

        // 이미지 uri 를 bitmap 으로 변환하는 함수
        fun convertUriToBitmap(activity: Activity, imageUri: Uri?): Bitmap? {

            var bitmap: Bitmap? = null

            if(imageUri != null) {

                try {
                    bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(activity.contentResolver, imageUri)).copy(Bitmap.Config.ARGB_8888, true)
                    } else {
                        MediaStore.Images.Media.getBitmap(activity.contentResolver, imageUri).copy(Bitmap.Config.ARGB_8888, true)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    error(activity.localClassName, e.message)
                }

            }

            return bitmap
        }

    }
}