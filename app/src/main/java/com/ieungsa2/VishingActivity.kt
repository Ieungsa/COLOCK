package com.ieungsa2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.ieungsa2.voiceguard.CallMonitorService

class VishingActivity : BaseActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
        private const val NOTIFICATION_PERMISSION_REQUEST = 201
    }

    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navigationView: com.google.android.material.navigation.NavigationView
    private lateinit var menuButton: ImageView
    
    // Status card views
    private lateinit var statusIconBg: View
    private lateinit var statusIcon: TextView
    private lateinit var statusIconImage: ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    
    private lateinit var micPermissionButton: Button
    private lateinit var phonePermissionButton: Button
    private lateinit var callLogPermissionButton: Button
    private lateinit var notificationButton: Button
    private lateinit var vishingHistoryButton: Button
    private lateinit var protectionSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vishing)

        initViews()
        setupClickListeners()
        setupDrawer() // 드로어 설정 추가
        updatePermissionStatus()
        
        setupBackPressHandler()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        menuButton = findViewById(R.id.menu_button)
        
        statusIconBg = findViewById(R.id.status_icon_bg)
        statusIcon = findViewById(R.id.status_icon)
        statusIconImage = findViewById(R.id.status_icon_image)
        statusTitle = findViewById(R.id.status_title)
        statusDescription = findViewById(R.id.status_description)
        
        micPermissionButton = findViewById(R.id.mic_permission_button)
        phonePermissionButton = findViewById(R.id.phone_permission_button)
        callLogPermissionButton = findViewById(R.id.call_log_permission_button)
        notificationButton = findViewById(R.id.notification_button)
        vishingHistoryButton = findViewById(R.id.vishing_history_button)
        protectionSwitch = findViewById(R.id.protection_switch)
    }

    private fun setupDrawer() {
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.END)
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_vishing -> {
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
                    true
                }
                R.id.nav_smishing -> {
                    val intent = Intent(this, SmishingActivity::class.java)
                    startActivity(intent)
                    finish() // 현재 액티비티 종료하고 이동
                    true
                }
                R.id.nav_data_leak -> {
                    Toast.makeText(this, "데이터 유출 보호 (개발 예정)", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
                    true
                }
                R.id.nav_family_verify -> {
                    val intent = Intent(this, FamilyVerifyActivity::class.java)
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_about -> {
                    showAboutDialog()
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("COLOCK 정보")
            .setMessage(
                "버전: 2.0 (통합)\n\n" +
                "COLOCK은 스미싱과 보이스피싱으로부터\n" +
                "당신을 안전하게 지켜드립니다.\n\n" +
                "© 2026 KMOU Capstone Team"
            )
            .setPositiveButton("확인") { _, _ -> }
            .show()
    }
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupClickListeners() {
        // backButton 리스너 제거 (menuButton 대체)

        micPermissionButton.setOnClickListener {
            requestPermission(Manifest.permission.RECORD_AUDIO)
        }

        phonePermissionButton.setOnClickListener {
            requestPermission(Manifest.permission.READ_PHONE_STATE)
        }

        callLogPermissionButton.setOnClickListener {
            requestPermission(Manifest.permission.READ_CALL_LOG)
        }

        notificationButton.setOnClickListener {
            requestNotificationPermission()
        }

        vishingHistoryButton.setOnClickListener {
            val intent = Intent(this, VishingHistoryActivity::class.java)
            startActivity(intent)
        }

        setupProtectionSwitchListener()
    }

    private fun setupProtectionSwitchListener() {
        protectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !allPermissionsGranted()) {
                protectionSwitch.setOnCheckedChangeListener(null)
                protectionSwitch.isChecked = false
                setupProtectionSwitchListener()
                Toast.makeText(this, "모든 권한을 허용해주세요", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

            // 토글 상태 저장
            saveVoiceGuardState(isChecked)

            if (isChecked) {
                startVoiceGuardService()
                updateStatusUI(true, true)
            } else {
                stopVoiceGuardService()
                updateStatusUI(true, false)
            }
        }
    }

    private fun requestPermission(permission: String) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }

    private fun updatePermissionStatus() {
        val micGranted = checkPermission(Manifest.permission.RECORD_AUDIO)
        val phoneGranted = checkPermission(Manifest.permission.READ_PHONE_STATE)
        val callLogGranted = checkPermission(Manifest.permission.READ_CALL_LOG)
        val notificationGranted = checkNotificationPermission()

        micPermissionButton.isEnabled = !micGranted
        micPermissionButton.text = if (micGranted) "마이크 권한 ✓" else "마이크 권한 설정"

        phonePermissionButton.isEnabled = !phoneGranted
        phonePermissionButton.text = if (phoneGranted) "전화 상태 권한 ✓" else "전화 상태 권한 설정"

        callLogPermissionButton.isEnabled = !callLogGranted
        callLogPermissionButton.text = if (callLogGranted) "통화 기록 권한 ✓" else "통화 기록 권한 설정"

        notificationButton.isEnabled = !notificationGranted
        notificationButton.text = if (notificationGranted) "알림 권한 ✓" else "알림 권한 설정"

        val allGranted = micGranted && phoneGranted && callLogGranted && notificationGranted
        protectionSwitch.isEnabled = allGranted
        
        // 현재 저장된 상태 반영
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isEnabled = sharedPref.getBoolean("voice_guard_enabled", false)
        
        // 리스너 잠시 해제 (중복 토스트 방지)
        protectionSwitch.setOnCheckedChangeListener(null)

        // 권한이 없으면 강제로 끔
        if (!allGranted && isEnabled) {
            protectionSwitch.isChecked = false
            saveVoiceGuardState(false)
            stopVoiceGuardService()
        } else {
            protectionSwitch.isChecked = isEnabled
            
            // 만약 켜져있어야 하는데 서비스가 안돌고 있다면 조용히 시작
            if (isEnabled && allGranted && !isServiceRunning(CallMonitorService::class.java)) {
                val intent = Intent(this, CallMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }
        
        // 리스너 재등록
        setupProtectionSwitchListener()

        updateStatusUI(allGranted, protectionSwitch.isChecked)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun updateStatusUI(allGranted: Boolean, isEnabled: Boolean) {
        when {
            !allGranted -> {
                statusIconImage.visibility = View.GONE
                statusIcon.visibility = View.VISIBLE
                statusIconBg.setBackgroundResource(R.drawable.status_circle_red)
                statusIcon.text = "⚠️"
                statusTitle.text = "권한 설정 필요"
                statusTitle.setTextColor(ContextCompat.getColor(this, R.color.status_danger))
                statusDescription.text = "원활한 보호를 위해 권한을 허용해주세요"
            }
            isEnabled -> {
                statusIcon.visibility = View.GONE
                statusIconImage.visibility = View.VISIBLE
                statusIconImage.setImageResource(R.drawable.ic_app_logo)
                statusIconBg.setBackgroundResource(R.drawable.status_circle_white)
                statusTitle.text = "보이스 가드 활성화"
                statusTitle.setTextColor(ContextCompat.getColor(this, R.color.primary_blue))
                statusDescription.text = "실시간 통화 분석이 작동 중입니다"
            }
            else -> {
                statusIconImage.visibility = View.GONE
                statusIcon.visibility = View.VISIBLE
                statusIconBg.setBackgroundResource(R.drawable.status_circle_yellow)
                statusIcon.text = "🔌"
                statusTitle.text = "보호 중지됨"
                statusTitle.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
                statusDescription.text = "보이스피싱 보호 기능이 꺼져 있습니다"
            }
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return checkPermission(Manifest.permission.RECORD_AUDIO) &&
               checkPermission(Manifest.permission.READ_PHONE_STATE) &&
               checkPermission(Manifest.permission.READ_CALL_LOG) &&
               checkNotificationPermission()
    }

    private fun startVoiceGuardService() {
        val intent = Intent(this, CallMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        Toast.makeText(this, "VoiceGuard가 시작되었습니다", Toast.LENGTH_SHORT).show()
    }

    private fun stopVoiceGuardService() {
        val intent = Intent(this, CallMonitorService::class.java)
        stopService(intent)
        Toast.makeText(this, "VoiceGuard가 중지되었습니다", Toast.LENGTH_SHORT).show()
    }

    private fun saveVoiceGuardState(enabled: Boolean) {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        with (sharedPref.edit()) {
            putBoolean("voice_guard_enabled", enabled)
            apply()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
}