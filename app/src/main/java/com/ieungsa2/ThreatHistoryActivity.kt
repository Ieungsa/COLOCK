package com.ieungsa2

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.ieungsa2.database.DataLeakDatabase
import com.ieungsa2.database.FamilyVerifyDatabase
import com.ieungsa2.database.PhishingDatabase
import com.ieungsa2.database.VishingDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ThreatHistoryActivity : BaseActivity() {

    private lateinit var chipVishing: Chip
    private lateinit var chipSmishing: Chip
    private lateinit var chipVerification: Chip
    private lateinit var chipDataLeak: Chip
    private lateinit var threatsRecyclerView: RecyclerView
    private lateinit var emptyStateView: View
    
    private lateinit var phishingAdapter: PhishingAlertAdapter
    private lateinit var vishingAdapter: VishingAlertAdapter
    private lateinit var verificationAdapter: FamilyVerifyLogAdapter
    private lateinit var dataLeakAdapter: DataLeakAlertAdapter
    
    private val phishingDatabase by lazy { PhishingDatabase.getDatabase(this) }
    private val vishingDatabase by lazy { VishingDatabase.getDatabase(this) }
    private val verificationDatabase by lazy { FamilyVerifyDatabase.getDatabase(this) }
    private val dataLeakDatabase by lazy { DataLeakDatabase.getDatabase(this) }

    private var currentFilter = "vishing"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_threat_history)

        initViews()
        setupClickListeners()
        loadThreats()
    }

    private fun initViews() {
        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }

        chipVishing = findViewById(R.id.chip_vishing)
        chipSmishing = findViewById(R.id.chip_smishing)
        chipVerification = findViewById(R.id.chip_verification)
        chipDataLeak = findViewById(R.id.chip_data_leak)

        threatsRecyclerView = findViewById(R.id.threats_recycler_view)
        emptyStateView = findViewById(R.id.empty_state)

        threatsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        phishingAdapter = PhishingAlertAdapter(
            onItemClick = { alert ->
                PhishingDetailDialog.show(this, alert)
            },
            onDeleteClick = { alert ->
                lifecycleScope.launch {
                    phishingDatabase.phishingAlertDao().deleteAlert(alert.id)
                }
            }
        )

        vishingAdapter = VishingAlertAdapter(
            onItemClick = { alert ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("📞 보이스피싱 감지 상세")
                    .setMessage("발신: ${alert.phoneNumber}\n위험도: ${(alert.riskScore * 100).toInt()}%\n분석: ${alert.transcription}")
                    .setPositiveButton("확인", null)
                    .show()
            },
            onDeleteClick = { alert ->
                lifecycleScope.launch {
                    vishingDatabase.vishingAlertDao().delete(alert.id)
                    loadThreats()
                }
            }
        )
        
        verificationAdapter = FamilyVerifyLogAdapter(
            onItemClick = { log -> 
                val statusText = when (log.status) {
                    "PENDING" -> "⏳ 대기 중"
                    "APPROVED" -> "✅ 승인됨"
                    "REJECTED" -> "❌ 거절됨"
                    else -> log.status
                }
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("🔐 사칭 방지 상세")
                    .setMessage("상대방: ${log.targetPhone}\n상태: $statusText\n날짜: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(log.timestamp)}")
                    .setPositiveButton("확인", null)
                    .show()
            },
            onDeleteClick = { log ->
                lifecycleScope.launch {
                    verificationDatabase.familyVerifyLogDao().delete(log.id)
                    loadThreats()
                }
            }
        )

        dataLeakAdapter = DataLeakAlertAdapter(
            onItemClick = { alert ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("🚨 데이터 유출 감지 상세")
                    .setMessage("앱: ${alert.appName}\n사용량: ${"%.2f".format(alert.usageMB)}MB\n임계값: ${alert.thresholdMB}MB\n시각: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(alert.timestamp)}")
                    .setPositiveButton("확인", null)
                    .show()
            },
            onDeleteClick = { alert ->
                lifecycleScope.launch {
                    dataLeakDatabase.dataLeakAlertDao().delete(alert.id)
                    loadThreats()
                }
            }
        )
        
        threatsRecyclerView.adapter = vishingAdapter
    }

    private fun setupClickListeners() {
        chipVishing.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                uncheckOtherChips("vishing")
                currentFilter = "vishing"
                loadThreats()
            }
        }

        chipSmishing.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                uncheckOtherChips("smishing")
                currentFilter = "smishing"
                loadThreats()
            }
        }

        chipVerification.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                uncheckOtherChips("verification")
                currentFilter = "verification"
                loadThreats()
            }
        }

        chipDataLeak.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                uncheckOtherChips("data_leak")
                currentFilter = "data_leak"
                loadThreats()
            }
        }
    }

    private fun uncheckOtherChips(selectedChip: String) {
        if (selectedChip != "vishing") chipVishing.isChecked = false
        if (selectedChip != "smishing") chipSmishing.isChecked = false
        if (selectedChip != "verification") chipVerification.isChecked = false
        if (selectedChip != "data_leak") chipDataLeak.isChecked = false
    }

    private var currentJob: kotlinx.coroutines.Job? = null

    private fun loadThreats() {
        currentJob?.cancel()
        
        when (currentFilter) {
            "vishing" -> {
                threatsRecyclerView.adapter = vishingAdapter
                currentJob = lifecycleScope.launch {
                    val alerts = vishingDatabase.vishingAlertDao().getAllAlerts()
                    if (alerts.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        vishingAdapter.submitList(alerts)
                    }
                }
            }
            "smishing" -> {
                threatsRecyclerView.adapter = phishingAdapter
                currentJob = lifecycleScope.launch {
                    phishingDatabase.phishingAlertDao().getAlertsByType("SMISHING").collectLatest { alerts ->
                        if (alerts.isEmpty()) {
                            showEmptyState()
                        } else {
                            hideEmptyState()
                            phishingAdapter.submitList(alerts)
                        }
                    }
                }
            }
            "verification" -> {
                threatsRecyclerView.adapter = verificationAdapter
                currentJob = lifecycleScope.launch {
                    val logs = verificationDatabase.familyVerifyLogDao().getAllLogs()
                    if (logs.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        verificationAdapter.submitList(logs)
                    }
                }
            }
            "data_leak" -> {
                threatsRecyclerView.adapter = dataLeakAdapter
                currentJob = lifecycleScope.launch {
                    val alerts = dataLeakDatabase.dataLeakAlertDao().getAllAlerts()
                    if (alerts.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        dataLeakAdapter.submitList(alerts)
                    }
                }
            }
        }
    }

    private fun showEmptyState() {
        emptyStateView.visibility = View.VISIBLE
        threatsRecyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        emptyStateView.visibility = View.GONE
        threatsRecyclerView.visibility = View.VISIBLE
    }
}