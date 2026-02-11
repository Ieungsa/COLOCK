package com.ieungsa2

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat

class SmishingActivity : BaseActivity() {

    companion object {
        private const val TAG = "SmishingActivity"
        private const val SMS_PERMISSION_REQUEST = 100
        private const val NOTIFICATION_PERMISSION_REQUEST = 101
    }

    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navigationView: com.google.android.material.navigation.NavigationView
    private lateinit var menuButton: ImageView
    
    // New status card views
    private lateinit var statusIconBg: View
    private lateinit var statusIcon: TextView
    private lateinit var statusIconImage: ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    
    private lateinit var smsPermissionButton: Button
    private lateinit var overlayPermissionButton: Button
    private lateinit var accessibilityButton: Button
    private lateinit var notificationButton: Button
    private lateinit var defaultBrowserButton: Button
    private lateinit var protectionSwitch: Switch
    private lateinit var historyButton: Button

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smishing)

        initViews()
        setupClickListeners()
        setupDrawer() // 드로어 설정 추가
        updatePermissionStatus()

        // 알림 채널 생성
        NotificationHelper.createNotificationChannel(this)

        // 인텐트에서 전달된 데이터 처리
        handleIntent(intent)

        // 저장된 보호 토글 상태 복원
        restoreProtectionState()
        
        setupBackPressHandler()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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
        
        smsPermissionButton = findViewById(R.id.sms_permission_button)
        overlayPermissionButton = findViewById(R.id.overlay_permission_button)
        accessibilityButton = findViewById(R.id.accessibility_button)
        notificationButton = findViewById(R.id.notification_button)
        defaultBrowserButton = findViewById(R.id.default_browser_button)
        protectionSwitch = findViewById(R.id.protection_switch)
        historyButton = findViewById(R.id.history_button)
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
                    val intent = Intent(this, VishingActivity::class.java)
                    startActivity(intent)
                    finish() // 현재 액티비티 종료하고 이동
                    true
                }
                R.id.nav_smishing -> {
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
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
        // backButton 리스너 제거됨 (menuButton으로 대체)

        smsPermissionButton.setOnClickListener {
            requestSmsPermission()
        }

        overlayPermissionButton.setOnClickListener {
            requestOverlayPermission()
        }

        accessibilityButton.setOnClickListener {
            requestAccessibilityPermission()
        }

        notificationButton.setOnClickListener {
            requestNotificationPermission()
        }

        defaultBrowserButton.setOnClickListener {
            requestDefaultBrowser()
        }

        historyButton.setOnClickListener {
            openPhishingHistory()
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
            saveProtectionState(isChecked)

            if (isChecked) {
                startProtectionService()
                updateStatusUI(true, true)
            } else {
                stopProtectionService()
                updateStatusUI(true, false)
            }
        }
    }

    private fun requestSmsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )

            ActivityCompat.requestPermissions(this, permissions, SMS_PERMISSION_REQUEST)
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
        }
    }

    private fun requestAccessibilityPermission() {
        // 바로 설정 화면으로 이동 (다이얼로그 생략)
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "설치된 앱에서 COLOCK을 찾아 활성화해주세요", Toast.LENGTH_LONG).show()
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

    private fun requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                if (roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                    Toast.makeText(this, "이미 기본 브라우저로 설정되어 있습니다", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
                    startActivityForResult(intent, 999)
                }
            }
        } else {
            // Android 9 이하: 설정 화면으로 유도
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            try {
                startActivity(intent)
                Toast.makeText(this, "브라우저 앱을 COLOCK으로 설정해주세요", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(appSettingsIntent)
            }
        }
    }

    private fun isDefaultBrowser(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            return roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
        } else {
            // Android 9 이하 확인 로직 (간소화)
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
            val resolveInfo = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            return resolveInfo?.activityInfo?.packageName == packageName
        }
    }

    private fun updatePermissionStatus() {
        val smsGranted = checkSmsPermissions()
        val overlayGranted = checkOverlayPermission()
        val accessibilityGranted = checkAccessibilityPermission()
        val notificationGranted = checkNotificationPermission()
        val isDefault = isDefaultBrowser()

        smsPermissionButton.isEnabled = !smsGranted
        smsPermissionButton.text = if (smsGranted) "SMS 권한 ✓" else "SMS 권한 설정"

        overlayPermissionButton.isEnabled = !overlayGranted
        overlayPermissionButton.text = if (overlayGranted) "오버레이 권한 ✓" else "오버레이 권한 설정"

        accessibilityButton.isEnabled = !accessibilityGranted
        accessibilityButton.text = if (accessibilityGranted) "접근성 서비스 ✓" else "접근성 서비스 설정"

        notificationButton.isEnabled = !notificationGranted
        notificationButton.text = if (notificationGranted) "알림 권한 ✓" else "알림 권한 설정"

        defaultBrowserButton.isEnabled = !isDefault
        defaultBrowserButton.text = if (isDefault) "기본 브라우저 ✓" else "기본 브라우저 설정"

        val allGranted = smsGranted && overlayGranted && accessibilityGranted && notificationGranted && isDefault
        protectionSwitch.isEnabled = allGranted

        // 현재 저장된 상태 반영
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isEnabled = sharedPref.getBoolean("protection_enabled", false)
        
        // 리스너 잠시 해제 (중복 토스트 방지)
        protectionSwitch.setOnCheckedChangeListener(null)

        if (!allGranted && isEnabled) {
            protectionSwitch.isChecked = false
            saveProtectionState(false)
            stopProtectionService()
        } else {
            protectionSwitch.isChecked = isEnabled
            
            // 서비스가 꺼져있을 때만 시작
            if (isEnabled && allGranted && !isServiceRunning(PhishingProtectionService::class.java)) {
                val intent = Intent(this, PhishingProtectionService::class.java)
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
                statusTitle.text = "스미싱 가드 활성화"
                statusTitle.setTextColor(ContextCompat.getColor(this, R.color.primary_blue))
                statusDescription.text = "실시간으로 위협을 감지하고 있습니다"
            }
            else -> {
                statusIconImage.visibility = View.GONE
                statusIcon.visibility = View.VISIBLE
                statusIconBg.setBackgroundResource(R.drawable.status_circle_yellow)
                statusIcon.text = "🔌"
                statusTitle.text = "보호 중지됨"
                statusTitle.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
                statusDescription.text = "피싱 보호 기능이 꺼져 있습니다"
            }
        }
    }

    private fun checkSmsPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun checkAccessibilityPermission(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )

        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            val serviceName = "${packageName}/${OverlayAccessibilityService::class.java.name}"
            return services?.contains(serviceName) == true
        }

        return false
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return checkSmsPermissions() && checkOverlayPermission() && checkAccessibilityPermission() && checkNotificationPermission() && isDefaultBrowser()
    }

    private fun startProtectionService() {
        val intent = Intent(this, PhishingProtectionService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(this, "피싱 보호가 시작되었습니다", Toast.LENGTH_SHORT).show()
    }

    private fun stopProtectionService() {
        val intent = Intent(this, PhishingProtectionService::class.java)
        stopService(intent)

        Toast.makeText(this, "피싱 보호가 중지되었습니다", Toast.LENGTH_SHORT).show()
    }

    private fun handleIntent(intent: Intent) {
        when {
            intent.hasExtra("detected_url") -> {
                showPhishingAlert(intent)
            }
            intent.hasExtra("warning_url") -> {
                showWarningDetails(intent)
            }
            intent.getBooleanExtra("emergency_alert", false) -> {
                showEmergencyAlert(intent)
            }
        }
    }

    private fun showPhishingAlert(intent: Intent) {
        val url = intent.getStringExtra("detected_url") ?: return
        val sender = intent.getStringExtra("sender") ?: "알 수 없음"
        val riskScore = intent.getFloatExtra("risk_score", 0f)

        AlertDialog.Builder(this)
            .setTitle("⚠️ 피싱 링크 감지")
            .setMessage("발신자: $sender\n위험도: ${(riskScore * 100).toInt()}%\n링크: $url")
            .setPositiveButton("확인") { _, _ -> }
            .setNegativeButton("상세정보") { _, _ ->
                showDetailFromDatabase(url, sender)
            }
            .show()
    }

    private fun showWarningDetails(intent: Intent) {
        val url = intent.getStringExtra("warning_url") ?: return
        val riskScore = intent.getFloatExtra("risk_score", 0f)

        AlertDialog.Builder(this)
            .setTitle("링크 위험도 상세 정보")
            .setMessage("URL: $url\n위험도: ${(riskScore * 100).toInt()}%\n\n이 링크를 클릭하지 않는 것을 권장합니다.")
            .setPositiveButton("확인") { _, _ -> }
            .setNegativeButton("강제 열기") { _, _ ->
                openUrlWithWarning(url)
            }
            .show()
    }

    private fun showDetailFromDatabase(url: String, sender: String) {
        Thread {
            try {
                val database = com.ieungsa2.database.PhishingDatabase.getDatabase(this@SmishingActivity)
                val dao = database.phishingAlertDao()

                val alert = kotlinx.coroutines.runBlocking {
                    dao.getAlertBySender(sender) ?: dao.getLatestAlert()
                }

                runOnUiThread {
                    if (alert != null) {
                        val intent = Intent(this@SmishingActivity, PhishingHistoryActivity::class.java)
                        intent.putExtra("show_alert_id", alert.id)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@SmishingActivity, "위험 문자 기록이 없습니다", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "상세정보 조회 실패: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@SmishingActivity, "상세정보를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun openUrlWithWarning(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "링크를 열 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            SMS_PERMISSION_REQUEST -> {
                updatePermissionStatus()
            }
            NOTIFICATION_PERMISSION_REQUEST -> {
                updatePermissionStatus()
            }
        }
    }

    private fun showEmergencyAlert(intent: Intent) {
        val url = intent.getStringExtra("detected_url") ?: return
        val sender = intent.getStringExtra("sender") ?: "알 수 없음"
        val riskScore = intent.getFloatExtra("risk_score", 0f)

        val riskLevel = when {
            riskScore > 0.7f -> "매우 위험"
            riskScore > 0.5f -> "위험"
            else -> "의심"
        }

        AlertDialog.Builder(this)
            .setTitle("🚨 긴급! 위험 링크 감지")
            .setMessage(
                "방금 받은 문자에서 위험한 링크가 감지되었습니다!\n\n" +
                "📱 발신자: $sender\n" +
                "⚠️ 위험도: $riskLevel (${(riskScore * 100).toInt()}%)\n" +
                "🔗 링크: ${url.take(50)}${if(url.length > 50) "..." else ""}\n\n" +
                "❌ 절대 이 링크를 클릭하지 마세요!\n" +
                "💰 개인정보나 금융정보를 요구할 수 있습니다."
            )
            .setPositiveButton("확인했습니다") { _, _ ->
                finish()
            }
            .setNegativeButton("상세정보") { _, _ ->
                showDetailFromDatabase(url, sender)
            }
            .setCancelable(false)
            .show()
    }

    private fun openPhishingHistory() {
        val intent = Intent(this, PhishingHistoryActivity::class.java)
        startActivity(intent)
    }

    private fun saveProtectionState(enabled: Boolean) {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        with (sharedPref.edit()) {
            putBoolean("protection_enabled", enabled)
            apply()
        }
    }

    private fun restoreProtectionState() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val wasEnabled = sharedPref.getBoolean("protection_enabled", false)
        if (wasEnabled && allPermissionsGranted()) {
            // 리스너 해제 후 상태 변경
            protectionSwitch.setOnCheckedChangeListener(null)
            protectionSwitch.isChecked = true
            
            // 실제 서비스 재확인 (토스트 없이)
            val intent = Intent(this, PhishingProtectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            updateStatusUI(true, true)
            
            // 리스너 다시 연결
            setupProtectionSwitchListener()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
}