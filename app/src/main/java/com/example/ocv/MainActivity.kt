package com.example.ocv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.ocv.databinding.ActivityMainBinding
import com.permissionx.guolindev.PermissionX
import org.opencv.core.*
import java.io.*

/**
 * # Mat()
 * OpenCV 에서는 Mat 객체에 이미지를 숫자로 저장한다.
 * * 픽셀 : 세 개의 숫자로 이루어진 순서쌍이 들어있는 하나의 작은 사각형.
 * * 세 개의 숫자는 각각 하나의 픽셀을 구성하는 RGB.
 * * 한 채널에 8비트씩해서 각각 0~255 까지의 값을 가질 수 있음.
 * * OpenCV 에서는 메모리상의 Mat 객체에 저장할 때나 HighGUI를 이용해서 화면에 이미지를 보여줄 때 Blue, Green, Red 채널 순으로 처리.
 */
//https://www.charlezz.com/?p=46046
//https://www.charlezz.com/?p=45125

class MainActivity : AppCompatActivity() {

    companion object { private val TAG = "MainActivity" }

    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    lateinit var fragment: Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // 권한 받기
        initPermissions()
        //버튼 초기화
        initButtons()

        initViews()

    }

    private fun initViews() {


    }

    private fun initButtons() {

        // 명함 OCR 버튼
        binding.cardOCRButton.setOnClickListener {
            onFragmentSelected(0)
        }

        // 객체 찾기 버튼
        binding.findObjButton.setOnClickListener {
            onFragmentSelected(1)
        }

    }

    // 프래그먼트 선택 함수
    fun onFragmentSelected(index: Int) {

        when(index) {
            0 -> fragment = CardOCRFragment()
            1 -> fragment = MatchOCRFragment()
        }

        if (this::fragment.isInitialized) supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit()

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

}