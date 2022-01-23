package com.example.ocv

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ocv.databinding.ActivityMainBinding
import com.googlecode.tesseract.android.TessBaseAPI
import com.permissionx.guolindev.PermissionX
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.*
import java.lang.Double.max
import kotlin.math.sqrt
import org.opencv.core.Mat

import org.opencv.android.LoaderCallbackInterface

import org.opencv.android.BaseLoaderCallback
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var binding: ActivityMainBinding

    private val REQUEST_IMAGE_CAPTURE = 2
    private val GALLERY = 1

    // 카메라 원본이미지 Uri를 저장할 변수
    var imageUri: Uri? = null

    lateinit var tess : TessBaseAPI //Tesseract API 객체 생성
    var dataPath : String = "" //데이터 경로 변수 선언
    lateinit var imageMat: Mat
    lateinit var imageBitmap: Bitmap

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 권한 받기
        initPermissions()
        //버튼 초기화
        initButtons()
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d(
                "OpenCV",
                "Internal OpenCV library not found. Using OpenCV Manager for initialization"
            )
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback)
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun initButtons() {

        binding.cameraButton.setOnClickListener {
            if(checkPermission()) {
                showCamera()
            } else {
                AppData.showToast(this, "권환을 확인해주세요.")
                initPermissions()
            }
        }

        binding.galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, GALLERY)
        }


        binding.convertButton.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            imageMat = Mat()

            Utils.bitmapToMat(imageBitmap, imageMat)
            convertBinaryFile(imageMat)
            binding.progressBar.visibility = View.GONE
        }

    }


    @RequiresApi(Build.VERSION_CODES.N)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(resultCode == Activity.RESULT_OK) {
            if(requestCode == REQUEST_IMAGE_CAPTURE) {
                if (imageUri != null) {
                    imageBitmap = loadBitmapFromMediaStore(imageUri!!)?.copy(Bitmap.Config.ARGB_8888, true)!!

                    binding.ocrImageOutput.setImageBitmap(imageBitmap)
                    imageUri = null
                }

            } else if(requestCode == GALLERY) {
                imageUri = data?.data

                try {
                    imageBitmap = loadBitmapFromMediaStore(imageUri!!)?.copy(Bitmap.Config.ARGB_8888, true)!!

                    binding.ocrImageOutput.setImageBitmap(imageBitmap)
                    imageUri = null
                } catch(e: Exception) {
                    AppData.error(TAG, "gallery error : $e ")
                }
            }
        }
    }

    fun loadBitmapFromMediaStore(imageUri: Uri): Bitmap? {
        var image: Bitmap? = null
        try {
            image = if (Build.VERSION.SDK_INT > 27) { // Api 버전별 이미지 처리
                val source: ImageDecoder.Source =
                    ImageDecoder.createSource(this.contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
            }
        } catch (e: IOException) {
            AppData.error(TAG, "비트맵 변환중 에러($e)")
        }
        return image
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun convertBinaryFile(imageMat: Mat) {

        // 흑백영상으로 전환
        val graySrc = Mat()
        Imgproc.cvtColor(imageMat, graySrc, Imgproc.COLOR_BGR2GRAY)

        // 이진화
        val binarySrc = Mat()
        Imgproc.threshold(graySrc, binarySrc, 0.0, 255.0, Imgproc.THRESH_OTSU)

        // 윤곽선 찾기
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            binarySrc,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_NONE
        )

        // 가장 면적이 큰 윤곽선 찾기
        var biggestContour: MatOfPoint? = null
        var biggestContourArea: Double = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > biggestContourArea) {
                biggestContour = contour
                biggestContourArea = area
            }
        }

        if (biggestContour == null) {
            AppData.showToast(this, "외곽선이 없습니다.")
            return
        }
        // 너무 작아도 안됨
        if (biggestContourArea < 400) {
            AppData.showToast(this, "사각형이 너무 작습니다.")
            return
        }

        // 근사화 작업(도형의 꼭지점을 분명하게 함)
        val candidate2f = MatOfPoint2f(*biggestContour.toArray())
        val approxCandidate = MatOfPoint2f()
        Imgproc.approxPolyDP(
            candidate2f,
            approxCandidate,
            Imgproc.arcLength(candidate2f, true) * 0.02,
            true
        )

        // 사각형 판별
        if (approxCandidate.rows() != 4) {
            AppData.showToast(this, "사각형이 아닙니다.")
            return
        }

        // 컨벡스(볼록한 도형)인지 판별
        if (!Imgproc.isContourConvex(MatOfPoint(*approxCandidate.toArray()))) {
            AppData.showToast(this, "컨벡스가 아닙니다.")
            return
        }

        // 좌상단부터 시계 반대 방향으로 정점을 정렬한다.
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

        // 투시변환 된 결과 영상 얻기
        val dst = Mat()
        Imgproc.warpPerspective(imageMat, dst, perspectiveTransform, Size(dw, dh))

        val bitmap = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, bitmap)


        binding.ocrImageOutput.setImageBitmap(bitmap)

        dataPath = "$filesDir/tesseract/" //언어데이터의 경로 미리 지정

        checkFile(File("${dataPath}tessdata/"),"kor") //사용할 언어파일의 이름 지정
        checkFile(File("${dataPath}tessdata/"),"eng")

        var lang : String = "kor+eng"
        tess = TessBaseAPI() //api준비
        tess.init(dataPath,lang) //해당 사용할 언어데이터로 초기화

        processImage(dst) //이미지 가공후 텍스트뷰에 띄우기

    }

    fun processImage(imageMat : Mat){


        AppData.showToast(this, "잠시 기다려 주세요")
        var ocrResult : String? = null

        val dst = Mat()

        Imgproc.cvtColor(imageMat, dst, Imgproc.COLOR_BGR2RGB)
        val bitmap = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, bitmap)

        tess.setImage(bitmap)
        ocrResult = tess.utF8Text
        AppData.debug(TAG, "해독 결과 : $ocrResult")
        binding.ocrOutput.text = ""
        binding.ocrOutput.text = ocrResult
    }

    fun checkFile(dir : File, lang : String){

        AppData.debug(TAG, "체크파일 호출")
        //파일의 존재여부 확인 후 내부로 복사
        if(!dir.exists()&&dir.mkdirs()){
            copyFile(lang)
        }

        if(dir.exists()){
            var datafilePath : String = dataPath+"/tessdata/"+lang+".traineddata"
            var dataFile : File = File(datafilePath)
            if(!dataFile.exists()){
                copyFile(lang)
            }
        }

    }

    fun copyFile(lang : String){

        AppData.debug(TAG, "copyFile 호출")
        try{
            //언어데이타파일의 위치
            var filePath : String = dataPath+"/tessdata/"+lang+".traineddata"

            //byte 스트림을 읽기 쓰기용으로 열기
            var inputStream : InputStream = assets.open("tessdata/"+lang+".traineddata")
            var outStream : OutputStream = FileOutputStream(filePath)


            //위에 적어둔 파일 경로쪽으로 해당 바이트코드 파일을 복사한다.
            var buffer : ByteArray = ByteArray(1024)

            var read : Int = 0
            read = inputStream.read(buffer)
            while(read!=-1){
                outStream.write(buffer,0,read)
                read = inputStream.read(buffer)
            }
            outStream.flush()
            outStream.close()
            inputStream.close()

        } catch(e : FileNotFoundException) {
            AppData.error(TAG, "$e")
        } catch (e : IOException) {
            AppData.error(TAG, "$e")
        }
    }

    // 사각형 꼭짓점 정보로 사각형 최대 사이즈 구하기
    // 평면상 두 점 사이의 거리는 직각삼각형의 빗변길이 구하기와 동일
    @RequiresApi(Build.VERSION_CODES.N)
    private fun calculateMaxWidthHeight(tl: Point, tr: Point, br: Point, bl: Point): Size {
        // Calculate width
        val widthA = sqrt((tl.x - tr.x) * (tl.x - tr.x) + (tl.y - tr.y) * (tl.y - tr.y))
        val widthB = sqrt((bl.x - br.x) * (bl.x - br.x) + (bl.y - br.y) * (bl.y - br.y))
        val maxWidth = max(widthA, widthB)
        // Calculate height
        val heightA = sqrt((tl.x - bl.x) * (tl.x - bl.x) + (tl.y - bl.y) * (tl.y - bl.y))
        val heightB = sqrt((tr.x - br.x) * (tr.x - br.x) + (tr.y - br.y) * (tr.y - br.y))
        val maxHeight = max(heightA, heightB)

        return Size(maxWidth, maxHeight)
    }

    private fun showCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val date = AppData.dateFormat.format(System.currentTimeMillis())
        createImageUri("file_$date", "image/png")?.let { uri ->
            imageUri = uri
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    fun createImageUri(filename: String, mimeType: String) : Uri? {
        var values = ContentValues()
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    fun initPermissions() {
        // 위험 권한 부여하기
        PermissionX.init(this)
            .permissions(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            )
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {
                    AppData.showToast(this, "모든 권한이 부여 됨")
                } else {
                    AppData.showToast(this, "권한 중에 거부된 권한들 : $deniedList")
                }
            }
    }

    private fun checkPermission(): Boolean{
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    Log.i("OpenCV", "OpenCV loaded successfully")
                    imageMat = Mat()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

}