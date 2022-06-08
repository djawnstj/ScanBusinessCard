package com.example.ocv

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.example.ocv.AppData.Companion.checkSelfPermission
import com.example.ocv.AppData.Companion.convertUriToBitmap
import com.example.ocv.databinding.FragmentCardOcrBinding
import com.permissionx.guolindev.PermissionX
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

class CardOCRFragment : Fragment() {

    companion object { private const val TAG = "CardOCRFragment" }

    private val binding by lazy { FragmentCardOcrBinding.inflate(layoutInflater) }

    // 권한 2번 이상 거부시 띄울 대화상자 변수
    var permissionDialog: AlertDialog? = null

    // 이미지를 받을 bitmap
    private var imageBitmap: Bitmap? = null

    // 카메라 액티비티에서 받은 Uri 결과값을 담을 변수
    private var cameraUri: Uri? = null

    private val imageMat by lazy { Mat() }      // 선택한 이미지를 담을 변수
    private val graySrc by lazy { Mat() }       // 흑백으로 변환시킨 이미지를 담을 변수
    private val binarySrc by lazy { Mat() }     // 이진화 시킨 이미지를 담을 변수

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

    @RequiresApi(Build.VERSION_CODES.N)
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

    @RequiresApi(Build.VERSION_CODES.N)
    private fun initButtons() {

        AppData.debug(TAG, "initButtons() called.")

        // 사진찍기 버튼 클릭 리스너
        binding.cameraButton.setOnClickListener {

            // 카메라 권한이 있으면 카메라 함수 호출
            if (checkSelfPermission(context, Manifest.permission.CAMERA)) {
                takePicture()
            } else {
                context?.let { AppData.showToast(it, "권환을 확인해주세요.") }
                grantPermissions()
            }
        }

        // 갤러리 버튼 클릭 리스너
        binding.galleryButton.setOnClickListener {

            // 저장소 읽기/쓰기 권한이 있으면 앨범 함수 호출
            if (checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                pickImageInGallery()
            } else {
                context?.let { AppData.showToast(it, "권환을 확인해주세요.") }
                grantPermissions()
            }
        }

        // 흑백으로 변환 버튼
        binding.convertGrayScaleButton.setOnClickListener {
            try {
                convertToGrayScaleImage()
            } catch (e: Exception) {
                e.printStackTrace()
                AppData.error(TAG, "convert  error : ${e.message}")
                context?.let { AppData.showToast(it, "흑백 변환환에 실패했습니다") }
            }
        }

        // 이미지 이진화 버튼
        binding.convertBinaryButton.setOnClickListener {
            try {
                convertToBinaryFile()
            } catch (e: Exception) {
                e.printStackTrace()
                AppData.error(TAG, "convert binary file error : ${e.message}")
                context?.let { AppData.showToast(it, "이미지 이진화에 실패했습니다") }
            }
        }

        // 윤곽선 찾기 버튼
        binding.findContourLineButton.setOnClickListener {
            try {
                findContourLine()
            } catch (e: Exception) {
                e.printStackTrace()
                AppData.error(TAG, "find contour line error : ${e.message}")
                context?.let { AppData.showToast(it, "윤곽선 찾기에 실패했습니다") }
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

    // 카메라 호출하는 함수
    private fun takePicture() {

        AppData.debug(TAG, "takePicture() called.")

        val values = ContentValues()

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraUri = requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
        resultLauncher.launch(cameraIntent)
    }

    // 갤러리에서 이미지 선택하는 함수
    private fun pickImageInGallery() {

        AppData.debug(TAG, "pickImageInGallery() called.")

        val photoPickerIntent = Intent(Intent.ACTION_PICK)
        photoPickerIntent.type = "image/*"
        pickGallery.launch(photoPickerIntent)

    }

    // 다른 액티비티(카메라)에서 결과값 받기
    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            imageBitmap = activity?.let { convertUriToBitmap(it, cameraUri) }

            binding.ocrImageOutput.setImageBitmap(imageBitmap)
        }
    }

    // 다른 액티비티(갤러리)에서 결과값 받기
    private val pickGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            result.data?.let { it ->

                // 갤러리에서 받아온 이미지 Uri
                val imageUri = it.data

                imageBitmap = activity?.let { it1 -> convertUriToBitmap(it1, imageUri) }

                binding.ocrImageOutput.setImageBitmap(imageBitmap)

            }
        }
    }

    // 흑백 영상으로 변환
    private fun convertToGrayScaleImage(): Mat {

        AppData.debug(TAG, "convertToBlackAndWhiteImage() called")

        // bitmap 이미지를 Mat 형식으로 변환
        Utils.bitmapToMat(imageBitmap, imageMat)

        // 흑백영상으로 전환
        Imgproc.cvtColor(imageMat, graySrc, Imgproc.COLOR_BGR2GRAY)

        val bitmap = Bitmap.createBitmap(graySrc.cols(), graySrc.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(graySrc, bitmap)

        binding.ocrImageOutput.setImageBitmap(bitmap)

        return graySrc

    }

    // 이미지 이진화
    private fun convertToBinaryFile() {

        Imgproc.threshold(graySrc, binarySrc, 0.0, 255.0, Imgproc.THRESH_OTSU)

        val bitmap = Bitmap.createBitmap(graySrc.cols(), graySrc.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(graySrc, bitmap)

        binding.ocrImageOutput.setImageBitmap(bitmap)
    }

    // 윤곽선 찾기기
    @RequiresApi(Build.VERSION_CODES.N)
    private fun findContourLine() {

        AppData.debug(TAG, "findContourLine called.")

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            binarySrc,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_NONE
        )

        AppData.debug(TAG, "Imgproc.findContours() called.")

        // 1-1 가장 면적이 큰 윤곽선 찾기
        var biggestContour: MatOfPoint? = null
        var biggestContourArea: Double = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > biggestContourArea) {
                biggestContour = contour
                biggestContourArea = area
            }
        }

        AppData.debug(TAG, "findBiggestContours called")

        // 윤곽선을 못찾은 경우
        if (biggestContour == null) {
            AppData.error(TAG, "no Contour")
            context?.let { AppData.showToast(it, "외곽선이 없습니다.") }

            return
        }
        // 윤곽선이 너무 작은 경우
        if (biggestContourArea < 400) {
            AppData.error(TAG, "rectangle is too small.")
            context?.let { AppData.showToast(it, "사각형이 너무 작습니다.") }

            return
        }

        // 1-2 근사화 작업(도형의 꼭지점을 분명하게 함)
        val candidate2f = MatOfPoint2f(*biggestContour.toArray())
        val approxCandidate = MatOfPoint2f()
        Imgproc.approxPolyDP(
            candidate2f,
            approxCandidate,
            Imgproc.arcLength(candidate2f, true) * 0.02,
            true
        )

        AppData.debug(TAG, "Imgproc.approxPolyDP() called")

        // 사각형인지 판별
        if (approxCandidate.rows() != 4) {

            AppData.error(TAG, "It's not rectangle")
            context?.let { AppData.showToast(it, "사각형이 아닙니다.") }

            return
        }

        // 컨벡스(볼록한 도형)인지 판별
        if (!Imgproc.isContourConvex(MatOfPoint(*approxCandidate.toArray()))) {

            AppData.error(TAG, "It's not ContourConvex")
            context?.let { AppData.showToast(it, "컨벡스가 아닙니다.") }

            return
        }

        // 1-3 좌상단부터 시계 반대 방향으로 정점을 정렬한다.
        val points = arrayListOf(
            Point(approxCandidate.get(0, 0)[0], approxCandidate.get(0, 0)[1]),
            Point(approxCandidate.get(1, 0)[0], approxCandidate.get(1, 0)[1]),
            Point(approxCandidate.get(2, 0)[0], approxCandidate.get(2, 0)[1]),
            Point(approxCandidate.get(3, 0)[0], approxCandidate.get(3, 0)[1]),
        )
        points.sortBy { it.x } // x좌표 기준으로 먼저 정렬

        if (points[0].y > points[1].y) {
            val temp = points[0]
            points[0] = points[1]
            points[1] = temp
        }

        if (points[2].y < points[3].y) {
            val temp = points[2]
            points[2] = points[3]
            points[3] = temp
        }

        AppData.debug(TAG, "points.sortByX called")

        // 원본 영상 내 정점들
        val srcQuad = MatOfPoint2f().apply { fromList(points) }

        val maxSize = calculateMaxWidthHeight(
            tl = points[0],
            bl = points[1],
            br = points[2],
            tr = points[3]
        )
        val dw = maxSize.width
        val dh = dw * maxSize.height/maxSize.width
        val dstQuad = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(0.0, dh),
            Point(dw, dh),
            Point(dw, 0.0)
        )

        // 투시변환 매트릭스 구하기
        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)

        AppData.debug(TAG, "getPerspectiveTransform called")

        // 투시변환 된 결과 영상 얻기
        val dst = Mat()
        Imgproc.warpPerspective(imageMat, dst, perspectiveTransform, Size(dw, dh))

        AppData.debug(TAG, "warpPerspective called")

        val bitmap = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, bitmap)

        binding.ocrImageOutput.setImageBitmap(bitmap)

    }

    // 사각형 꼭짓점 정보로 사각형 최대 사이즈 구하기
    // 평면상 두 점 사이의 거리는 직각삼각형의 빗변길이 구하기와 동일
    @RequiresApi(Build.VERSION_CODES.N)
    private fun calculateMaxWidthHeight(tl: Point, tr: Point, br: Point, bl: Point): Size {
        // Calculate width
        val widthA = sqrt((tl.x - tr.x) * (tl.x - tr.x) + (tl.y - tr.y) * (tl.y - tr.y))
        val widthB = sqrt((bl.x - br.x) * (bl.x - br.x) + (bl.y - br.y) * (bl.y - br.y))
        val maxWidth = java.lang.Double.max(widthA, widthB)
        // Calculate height
        val heightA = sqrt((tl.x - bl.x) * (tl.x - bl.x) + (tl.y - bl.y) * (tl.y - bl.y))
        val heightB = sqrt((tr.x - br.x) * (tr.x - br.x) + (tr.y - br.y) * (tr.y - br.y))
        val maxHeight = java.lang.Double.max(heightA, heightB)

        return Size(maxWidth, maxHeight)
    }

}