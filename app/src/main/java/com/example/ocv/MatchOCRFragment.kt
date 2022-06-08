package com.example.ocv

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.example.ocv.databinding.FragmentMatchOcrBinding
import com.permissionx.guolindev.PermissionX
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.lang.Exception

class MatchOCRFragment : Fragment() {

    companion object { private const val TAG = "MatchOCRFragment" }

    private val binding by lazy { FragmentMatchOcrBinding.inflate(layoutInflater) }

    // 권한 2번 이상 거부시 띄울 대화상자 변수
    var permissionDialog: AlertDialog? = null

    // 찾고자 하는 대상 이미지
    private var objectBitmap: Bitmap? = null
    // 찾을 대상이 포함된 이미지
    private var imageBitmap: Bitmap? = null

    private val objectMat by lazy { Mat() }             // 찾고자 하는 대상 이미지를 변환해서 담을 변수
    private val objectGrayMat by lazy { Mat() }         // 찾고자 하는 대상 이미지를 흑백화 담을 변수
    private val objectBinarySrc by lazy { Mat() }       // 찾고자 하는 대상 이미지를 이진화 시켜 담을 변수

    private val imageSrc by lazy { Mat() }                 // 찾고자 하는 대상을 포함한 이미지를 변환해서 담을 변수
    private val imageGraySrc by lazy { Mat() }          // 찾고자 하는 대상을 포함한 이미지를 흑백화 시켜 담을 변수
    private val imageBinarySrc by lazy { Mat() }        // 찾고자 하는 대상을 포함한 이미지를 이진화 시켜 담을 변수

    // baseLoaderCallback 을 이용하여, OnManagerConnected 초기화가 완료되면, ui 스레드에서 콜백이 호출된다.
    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(context) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    AppData.debug("OpenCV", "OpenCV load success.")
//                    imageMat = Mat()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        initButtons()

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        // 콜백을 호출하기 전에 OpenCV 호출 불가.
        // onCreate 에서는 OpenCV 에 관련된 모든 리소스를 불러오기 전이기 때문에 onResume 에서 호출
        if (!OpenCVLoader.initDebug()) OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, context, mLoaderCallback)
        else mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)

    }

    private fun initButtons() {

        // 찾을 대상 버튼
        binding.pickObjectButton.setOnClickListener {

            // 저장소 읽기/쓰기 권한이 있으면 앨범 함수 호출
            if (AppData.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                AppData.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {
                pickObjectImageInGallery()
            } else {
                context?.let { AppData.showToast(it, "권환을 확인해주세요.") }
                grantPermissions()
            }
        }

        // 찾을 대상이 포함된 이미지 선택 버튼
        binding.pickImageButton.setOnClickListener {

            // 저장소 읽기/쓰기 권한이 있으면 앨범 함수 호출
            if (AppData.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                AppData.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {
                pickImageInGallery()
            } else {
                context?.let { AppData.showToast(it, "권환을 확인해주세요.") }
                grantPermissions()
            }

        }

        // 객체 찾기 버튼
        binding.findObjectButton.setOnClickListener {
            try {
                test()
            } catch (e: Exception) {
                e.printStackTrace()
                AppData.error(TAG, "find object error : ${e.message}")
                context?.let { AppData.showToast(it, "이미지 찾기에 실패했습니다.") }
            }
        }

    }

    // 카메라 권한 받기
    private fun grantPermissions() {

        PermissionX.init(this)
            .permissions(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .request { allGranted, grantedList, deniedList ->
//                if (!allGranted) {
                checkPermissionDenyCount(deniedList)
//                }
            }
    }

    // 권한 두번 이상 거부할경우 설정으로 보내는 대화상자 함수
    private fun checkPermissionDenyCount(permissions: MutableList<String>) {
        if (permissionDialog != null && permissionDialog!!.isShowing || permissions.isEmpty() && activity == null && context == null) return

        var someDenied = false
        for (permission in permissions) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)) {
                if (ActivityCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                    someDenied = true
                }
            }
        }
        if (someDenied) {
            val alertDialogBuilder = AlertDialog.Builder(requireContext())
            permissionDialog = alertDialogBuilder.setTitle("권한이 필요합니다.")
                .setMessage("애플리케이션 설정에서 권한을 허용해주세요.")
                .setPositiveButton("설정") { dialog, which ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity?.packageName, null))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
                .setNegativeButton("닫기") { dialog, which -> }
                .setCancelable(false)
                .create()
            permissionDialog!!.show()
        }
    }

    // 찾고자 하는 대상 이미지 선택하는 함수
    private fun pickObjectImageInGallery() {

        AppData.debug(TAG, "pickImageInGallery() called.")

        val photoPickerIntent = Intent(Intent.ACTION_PICK)
        photoPickerIntent.type = "image/*"
        pickObj.launch(photoPickerIntent)

    }

    // 찾을 대상이 포함된 이미지 선택하는 함수
    private fun pickImageInGallery() {

        AppData.debug(TAG, "pickImageInGallery() called.")

        val photoPickerIntent = Intent(Intent.ACTION_PICK)
        photoPickerIntent.type = "image/*"
        pickImg.launch(photoPickerIntent)

    }

    // 찾고자 하는 대상 이미지 결과값 받기
    private val pickObj = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            result.data?.let { it ->

                // 갤러리에서 받아온 이미지 Uri
                val imageUri = it.data

                objectBitmap = activity?.let { it1 -> AppData.convertUriToBitmap(it1, imageUri) }

                binding.image1.setImageBitmap(objectBitmap)

            }
        }
    }

    // 찾을 대상이 포함된 이미지 결과값 받기
    private val pickImg = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            result.data?.let { it ->

                // 갤러리에서 받아온 이미지 Uri
                val imageUri = it.data

                imageBitmap = activity?.let { it1 -> AppData.convertUriToBitmap(it1, imageUri) }

                binding.image2.setImageBitmap(imageBitmap)

            }
        }
    }

    private fun test() {

        //  찾고자하는 대상을 Mat() 으로 변환 후 흑백화
        Utils.bitmapToMat(objectBitmap, objectMat)
        Imgproc.cvtColor(objectMat, objectGrayMat, Imgproc.COLOR_RGB2GRAY)

        // 찾고자하는 대상을 이진화
        Imgproc.threshold(objectGrayMat, objectBinarySrc, 128.0, 255.0, Imgproc.THRESH_BINARY_INV)

        val objContours = ArrayList<MatOfPoint>() // 객체 이미지의 윤곽선 정보
        val objHierarchy = Mat()
        Imgproc.findContours(
            objectBinarySrc,
            objContours,
            objHierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_NONE
        )

        val objPts = objContours.firstOrNull() ?: return //윤곽선을 못찾았으면 종료

        // 객체를 찾을 이미지를 Mat() 으로 변환 후 흑백화
        Utils.bitmapToMat(imageBitmap, imageSrc)
        Imgproc.cvtColor(imageSrc, imageGraySrc, Imgproc.COLOR_BGR2GRAY)

        // 객체를 찾을 이미지를 이진화
        Imgproc.threshold(imageGraySrc, imageBinarySrc, 128.0, 255.0, Imgproc.THRESH_BINARY_INV)

        val bgContours = ArrayList<MatOfPoint>() // 이미지의 윤곽선 정보
        val bgHierarchy = Mat()
        Imgproc.findContours(
            imageBinarySrc,
            bgContours,
            bgHierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_NONE
        )

        bgContours.forEachIndexed { index, pts ->
            if (Imgproc.contourArea(pts) > 1000) {
                val rect = Imgproc.boundingRect(pts)  // 검출한 객체의 감싸는 사각형
                Imgproc.rectangle(imageSrc, rect, Scalar(0.0, 0.0, 255.0), 1) // 파랑색 사각형으로 객체를 감싼다.
                // matchShape는 두 윤곽선의 사이의 거리(차이)를 반환
                val dist = Imgproc.matchShapes(
                    objPts, // 찾고자 하는 객체의 윤곽선
                    pts, // 검출한 객체의 윤곽선
                    Imgproc.CONTOURS_MATCH_I3, // 매칭 방식
                    0.0 // (사용되지 않음)
                )
                // 0.1보다 낮은 차이를 보여줄 때 객체를 찾았다고 판단한다.
                val found = dist < 0.1
                if (found) {
                    // 찾은 객체는 빨간 선으로 두텁께 다시 그린다
                    Imgproc.rectangle(imageSrc, rect,  Scalar(255.0, 0.0, 0.0), 2)
                }
                // dist값을 출력함
                Imgproc.putText(
                    imageSrc,
                    "$dist",
                    Point(rect.x.toDouble(), rect.y.toDouble() - 3),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    1.0,
                    if (found) Scalar(255.0, 0.0, 0.0) else Scalar(0.0, 0.0, 255.0)
                )
            }
        }


        val bitmap = Bitmap.createBitmap(imageSrc.cols(), imageSrc.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(imageSrc, bitmap)

        binding.image1.visibility = View.GONE
        binding.image2.visibility = View.GONE
        binding.image3.setImageBitmap(bitmap)

    }

}