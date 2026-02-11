package com.ieungsa2

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.ieungsa2.network.NetworkUsageTracker
import kotlinx.coroutines.launch

class DataLeakActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DataLeakActivity"
    }

    private lateinit var protectionSwitch: SwitchCompat
    private lateinit var statusText: TextView
    private lateinit var permissionButton: Button
    private lateinit var whitelistButton: Button
    private lateinit var historyButton: Button
    private lateinit var monitoredAppsContainer: LinearLayout
    private lateinit var settingsCard: View
    private lateinit var thresholdSeekBar: SeekBar
    private lateinit var thresholdValueText: TextView

    private var allInstalledApps: List<AppInfo> = listOf()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var monitoringRunnable: Runnable
    private var isMonitoring = false
    private val monitoredApps = mutableMapOf<String, Long>() // packageName -> bytes

    data class AppInfo(val name: String, val packageName: String, val uid: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_leak)

        initViews()
        setupClickListeners()
        setupMonitoringRunnable()
        updateUI()
    }

    private fun initViews() {
        protectionSwitch = findViewById(R.id.data_leak_protection_switch)
        statusText = findViewById(R.id.data_leak_status_text)
        permissionButton = findViewById(R.id.permission_button)
        whitelistButton = findViewById(R.id.whitelist_button)
        historyButton = findViewById(R.id.data_leak_history_button)
        monitoredAppsContainer = findViewById(R.id.monitored_apps_container)
        settingsCard = findViewById(R.id.settings_card)
        thresholdSeekBar = findViewById(R.id.threshold_seekbar)
        thresholdValueText = findViewById(R.id.threshold_value_text)

        // SeekBar 설정 (1MB ~ 500MB)
        thresholdSeekBar.max = 499 // 0~499 (1MB~500MB)
        val savedThreshold = getSharedPreferences("data_leak_settings", MODE_PRIVATE)
            .getInt("threshold_mb", 50) // MB 단위로 변경
        thresholdSeekBar.progress = savedThreshold - 1
        thresholdValueText.text = "${savedThreshold}MB"

        thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 1 // 1MB부터 시작
                thresholdValueText.text = "${value}MB"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = (seekBar?.progress ?: 0) + 1
                getSharedPreferences("data_leak_settings", MODE_PRIVATE)
                    .edit()
                    .putInt("threshold_mb", value)
                    .apply()
                Toast.makeText(this@DataLeakActivity, "임계값 설정: ${value}MB", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }

        protectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasUsageStatsPermission()) {
                    startMonitoring()
                } else {
                    protectionSwitch.isChecked = false
                    Toast.makeText(this, "사용 정보 접근 권한이 필요합니다", Toast.LENGTH_SHORT).show()
                }
            } else {
                stopMonitoring()
            }
        }

        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        whitelistButton.setOnClickListener {
            showWhitelistDialog()
        }

        historyButton.setOnClickListener {
            val intent = Intent(this, DataLeakHistoryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupMonitoringRunnable() {
        monitoringRunnable = Runnable {
            Log.d(TAG, "===== 데이터 사용량 모니터링 시작 =====")

            loadAllApps()
            val tracker = NetworkUsageTracker(this)

            val endTime = System.currentTimeMillis()

            // 금일 00시 00분 00초 계산
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis // 오늘 00시부터

            val threshold = getSharedPreferences("data_leak_settings", MODE_PRIVATE)
                .getInt("threshold_mb", 50) * 1024L * 1024L // MB to Bytes

            val whitelistPrefs = getSharedPreferences("data_leak_whitelist", MODE_PRIVATE)
            val whitelist = whitelistPrefs.getStringSet("packages", emptySet()) ?: emptySet()

            monitoredApps.clear()

            allInstalledApps.forEach { app ->
                try {
                    val usage = tracker.queryNetworkUsage(app.uid, startTime, endTime)
                    val usageMB = usage / (1024.0 * 1024.0)

                    // 화이트리스트 앱 여부 확인
                    val isWhitelisted = whitelist.contains(app.packageName)

                    // 모든 앱의 사용량을 로그에 표시 (0MB인 앱도 포함)
                    val prefix = if (isWhitelisted) "✅ [제외]" else "📱"

                    // INFO 레벨로 출력
                    Log.i(TAG, "$prefix ${app.name} (${app.packageName}): ${"%.2f".format(usageMB)}MB 사용")

                    // 화이트리스트에 없고 임계값 초과한 앱만 경고 대상에 추가
                    if (!isWhitelisted && usage > threshold) {
                        monitoredApps[app.packageName] = usage
                        Log.w(TAG, "⚠️ ${app.name} (${app.packageName}): ${"%.2f".format(usageMB)}MB 사용 (임계값 초과)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "앱 ${app.name} 검사 중 오류: ${e.message}")
                }
            }

            runOnUiThread {
                updateMonitoredAppsList()
            }

            if (isMonitoring) {
                handler.postDelayed(monitoringRunnable, 30 * 1000) // 30초마다
            }
        }
    }

    private fun updateMonitoredAppsList() {
        monitoredAppsContainer.removeAllViews()

        if (monitoredApps.isEmpty()) {
            val emptyView = layoutInflater.inflate(android.R.layout.simple_list_item_1, monitoredAppsContainer, false) as TextView
            emptyView.text = "현재 모니터링 중인 의심 앱이 없습니다"
            emptyView.textSize = 14f
            emptyView.setPadding(16, 16, 16, 16)
            monitoredAppsContainer.addView(emptyView)
            return
        }

        monitoredApps.forEach { (packageName, bytes) ->
            val appInfo = allInstalledApps.find { it.packageName == packageName }
            val appName = appInfo?.name ?: packageName

            val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_2, monitoredAppsContainer, false)
            val text1 = itemView.findViewById<TextView>(android.R.id.text1)
            val text2 = itemView.findViewById<TextView>(android.R.id.text2)

            val usageMB = bytes / (1024.0 * 1024.0)
            text1.text = "⚠️ $appName"
            text2.text = "데이터 사용량: ${"%.2f".format(usageMB)}MB"

            monitoredAppsContainer.addView(itemView)
        }
    }

    private fun startMonitoring() {
        isMonitoring = true

        // 포그라운드 서비스 시작 (화면 꺼져도 동작)
        val serviceIntent = Intent(this, DataLeakMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // UI 업데이트용 로컬 모니터링도 시작
        handler.post(monitoringRunnable)

        getSharedPreferences("data_leak_settings", MODE_PRIVATE)
            .edit()
            .putBoolean("monitoring_enabled", true)
            .apply()

        updateUI()
        Toast.makeText(this, "데이터 유출 모니터링이 시작되었습니다 (백그라운드 실행)", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        isMonitoring = false

        // 포그라운드 서비스 중지
        val serviceIntent = Intent(this, DataLeakMonitorService::class.java)
        stopService(serviceIntent)

        handler.removeCallbacks(monitoringRunnable)
        monitoredApps.clear()
        updateMonitoredAppsList()

        getSharedPreferences("data_leak_settings", MODE_PRIVATE)
            .edit()
            .putBoolean("monitoring_enabled", false)
            .apply()

        updateUI()
        Toast.makeText(this, "데이터 유출 모니터링이 중지되었습니다", Toast.LENGTH_SHORT).show()
    }

    private fun loadAllApps() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // 모든 앱을 포함 (런처 앱이 아니어도 백그라운드에서 실행될 수 있음)
        allInstalledApps = apps
            .filter { appInfo ->
                !appInfo.packageName.isNullOrEmpty()
            }
            .map { AppInfo(it.loadLabel(pm).toString(), it.packageName, it.uid) }

        Log.d(TAG, "앱 목록 로드 완료: 총 ${allInstalledApps.size}개")
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showWhitelistDialog() {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                val isUserApp = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
                val isUpdatedSystemApp = (app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                isUserApp || isUpdatedSystemApp
            }
            .map { app ->
                val label = app.loadLabel(pm).toString()
                val pkg = app.packageName
                Pair(label, pkg)
            }
            .sortedBy { it.first }

        val appNames = installedApps.map { it.first }.toTypedArray()
        val packageNames = installedApps.map { it.second }.toTypedArray()

        val prefs = getSharedPreferences("data_leak_whitelist", MODE_PRIVATE)
        val savedSet = prefs.getStringSet("packages", emptySet()) ?: emptySet()
        val checkedItems = BooleanArray(installedApps.size) { i ->
            savedSet.contains(packageNames[i])
        }

        AlertDialog.Builder(this)
            .setTitle("모니터링 제외 앱 선택")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("저장") { _, _ ->
                val newSet = mutableSetOf<String>()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) newSet.add(packageNames[i])
                }
                prefs.edit().putStringSet("packages", newSet).apply()
                Toast.makeText(this, "화이트리스트 설정 저장 완료", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateUI()

        // 모니터링이 켜져있었다면 재개
        val wasEnabled = getSharedPreferences("data_leak_settings", MODE_PRIVATE)
            .getBoolean("monitoring_enabled", false)
        if (wasEnabled && hasUsageStatsPermission()) {
            protectionSwitch.isChecked = true
        }
    }

    private fun updateUI() {
        val hasPermission = hasUsageStatsPermission()

        if (!hasPermission) {
            statusText.text = "⚠️ 사용 정보 접근 권한이 필요합니다"
            permissionButton.visibility = View.VISIBLE
            settingsCard.visibility = View.GONE
        } else {
            permissionButton.visibility = View.GONE
            settingsCard.visibility = View.VISIBLE

            if (isMonitoring) {
                statusText.text = "✅ 데이터 사용량을 실시간으로 모니터링 중입니다"
            } else {
                statusText.text = "데이터 유출 보호가 비활성화되어 있습니다"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitoringRunnable)
    }
}
