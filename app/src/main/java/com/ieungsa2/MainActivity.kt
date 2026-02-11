package com.ieungsa2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.ieungsa2.voiceguard.CallMonitorService
import kotlinx.coroutines.launch

/**
 * Main entrance of the application. 
 * Displays overall security status and provides navigation to specific protection modules.
 */
class MainActivity : BaseActivity() {
    
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var menuButton: ImageView
    
    private lateinit var statusIconContainer: View
    private lateinit var statusIconEmoji: TextView
    private lateinit var statusIconImage: ImageView
    private lateinit var statusTitleText: TextView
    private lateinit var statusDescText: TextView

    private lateinit var vishingStatusText: TextView
    private lateinit var smishingStatusText: TextView
    private lateinit var scannedMessagesCount: TextView
    private lateinit var blockedThreatsCount: TextView

    private val callStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                updateVoiceGuardUI()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        NotificationHelper.createNotificationChannel(this)
        setupBackPressHandler()

        registerReceiver(callStateReceiver, android.content.IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(callStateReceiver)
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        menuButton = findViewById(R.id.menu_button)
        
        statusIconContainer = findViewById(R.id.status_icon_container)
        statusIconEmoji = findViewById(R.id.status_icon_emoji)
        statusIconImage = findViewById(R.id.status_icon_image)
        statusTitleText = findViewById(R.id.status_title_text)
        statusDescText = findViewById(R.id.status_desc_text)
        
        vishingStatusText = findViewById(R.id.vishing_status_text)
        smishingStatusText = findViewById(R.id.smishing_status_text)
        scannedMessagesCount = findViewById(R.id.scanned_messages_count)
        blockedThreatsCount = findViewById(R.id.blocked_threats_count)

        setupDrawer()
    }

    override fun onResume() {
        super.onResume()
        updateStatusCard()
        updateVoiceGuardUI()
        updateSmishingUI()
        updateStats()

        val size = navigationView.menu.size()
        for (i in 0 until size) {
            navigationView.menu.getItem(i).isChecked = false
        }
    }

    private fun updateStats() {
        val phishingDatabase = com.ieungsa2.database.PhishingDatabase.getDatabase(this)
        val vishingDatabase = com.ieungsa2.database.VishingDatabase.getDatabase(this)
        val verifyDatabase = com.ieungsa2.database.FamilyVerifyDatabase.getDatabase(this)
        val dataLeakDatabase = com.ieungsa2.database.DataLeakDatabase.getDatabase(this)
        
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val scannedCount = sharedPref.getInt("scanned_messages_count", 0)
        scannedMessagesCount.text = "${scannedCount}건"

        lifecycleScope.launch {
            val smishingCount = phishingDatabase.phishingAlertDao().getCountByType("SMISHING")
            val vishingCount = vishingDatabase.vishingAlertDao().getAllAlerts().size
            val verifyCount = verifyDatabase.familyVerifyLogDao().getAllLogs().size
            val dataLeakCount = dataLeakDatabase.dataLeakAlertDao().getAllAlerts().size
            
            val totalCount = smishingCount + vishingCount + verifyCount + dataLeakCount
            blockedThreatsCount.text = "${totalCount}건"
        }
    }

    private fun updateSmishingUI() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isEnabled = sharedPref.getBoolean("protection_enabled", false)
        
        if (isEnabled) {
            smishingStatusText.text = "실시간 모니터링 활성화 중"
            smishingStatusText.setTextColor(ContextCompat.getColor(this, R.color.primary_blue))
        } else {
            smishingStatusText.text = "보호 비활성화"
            smishingStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }
    
    private fun updateVoiceGuardUI() {
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isEnabled = sharedPref.getBoolean("voice_guard_enabled", false)
        
        if (isEnabled) {
            val tm = getSystemService(android.content.Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            if (tm.callState == android.telephony.TelephonyManager.CALL_STATE_OFFHOOK) {
                vishingStatusText.text = "실시간 분석 중"
            } else {
                vishingStatusText.text = "실시간 분석 대기 중"
            }
            vishingStatusText.setTextColor(ContextCompat.getColor(this, R.color.primary_blue))
        } else {
            vishingStatusText.text = "실시간 분석 중지됨"
            vishingStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun updateStatusCard() {
        val smsGranted = checkSmsPermissions()
        val voiceGranted = checkVoiceGuardPermissions()
        
        val sharedPref = getSharedPreferences("app_settings", MODE_PRIVATE)
        val smishingEnabled = sharedPref.getBoolean("protection_enabled", false)
        val voiceGuardEnabled = sharedPref.getBoolean("voice_guard_enabled", false)
        
        val isSafe = (smsGranted && smishingEnabled) || (voiceGranted && voiceGuardEnabled)

        if (!isSafe) {
             statusIconImage.visibility = View.GONE
             statusIconEmoji.visibility = View.VISIBLE
             if (!smishingEnabled && !voiceGuardEnabled) {
                 statusIconContainer.setBackgroundResource(R.drawable.status_circle_yellow)
                 statusIconEmoji.text = "🔌"
                 statusTitleText.text = "보호 중지됨"
                 statusTitleText.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
                 statusDescText.text = "보호 기능이 비활성화되었습니다"
             } else {
                 statusIconContainer.setBackgroundResource(R.drawable.status_circle_red)
                 statusIconEmoji.text = "⚠️"
                 statusTitleText.text = "권한 필요"
                 statusTitleText.setTextColor(ContextCompat.getColor(this, R.color.status_danger))
                 statusDescText.text = "시스템 작동을 위해 권한이 필요합니다"
             }
        } else {
            statusIconContainer.setBackgroundResource(R.drawable.status_circle_white)
            statusIconEmoji.visibility = View.GONE
            statusIconImage.visibility = View.VISIBLE
            statusIconImage.setImageResource(R.drawable.ic_app_logo)
            statusTitleText.text = "시스템 활성화"
            statusTitleText.setTextColor(ContextCompat.getColor(this, R.color.primary_blue))
            statusDescText.text = "실시간 보호 시스템이 작동 중입니다"
        }
    }

    private fun checkSmsPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun checkVoiceGuardPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val basicGranted = permissions.all { 
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
        }

        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        return basicGranted && storageGranted
    }

    private fun setupClickListeners() {
        findViewById<androidx.cardview.widget.CardView>(R.id.smishing_card).setOnClickListener {
            startActivity(Intent(this, SmishingActivity::class.java))
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.vishing_card).setOnClickListener {
            startActivity(Intent(this, VishingActivity::class.java))
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.user_verification_card).setOnClickListener {
            startActivity(Intent(this, FamilyVerifyActivity::class.java))
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.data_leak_card).setOnClickListener {
            startActivity(Intent(this, DataLeakActivity::class.java))
        }

        findViewById<View>(R.id.blocked_threats_container).setOnClickListener {
            startActivity(Intent(this, ThreatHistoryActivity::class.java))
        }
    }

    private fun setupDrawer() {
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.END)
            when (menuItem.itemId) {
                R.id.nav_home -> true
                R.id.nav_vishing -> {
                    startActivity(Intent(this, VishingActivity::class.java))
                    true
                }
                R.id.nav_smishing -> {
                    startActivity(Intent(this, SmishingActivity::class.java))
                    true
                }
                R.id.nav_family_verify -> {
                    startActivity(Intent(this, FamilyVerifyActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.nav_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("COLOCK Information")
            .setMessage("Version: 2.0\n\n© 2026 KMOU Capstone Team")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END)
                } else {
                    finish()
                }
            }
        })
    }
}
