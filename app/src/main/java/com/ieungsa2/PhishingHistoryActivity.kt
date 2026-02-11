package com.ieungsa2

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ieungsa2.database.PhishingDatabase
import com.ieungsa2.database.PhishingAlert
import kotlinx.coroutines.launch

class PhishingHistoryActivity : BaseActivity() {
    
    companion object {
        private const val TAG = "PhishingHistoryActivity"
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var adapter: PhishingAlertAdapter
    private lateinit var database: PhishingDatabase
    private lateinit var deleteAllButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phishing_history)

        initViews()
        setupRecyclerView()
        loadPhishingHistory()

        // 알림에서 상세정보 버튼을 눌러서 온 경우
        val showAlertId = intent.getLongExtra("show_alert_id", -1L)
        if (showAlertId != -1L) {
            showAlertDetails(showAlertId)
        }
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_view_alerts)
        emptyState = findViewById(R.id.empty_state)
        deleteAllButton = findViewById(R.id.delete_all_button)
        database = PhishingDatabase.getDatabase(this)
        
        deleteAllButton.setOnClickListener {
            showDeleteAllConfirmation()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = PhishingAlertAdapter(
            onItemClick = { alert ->
                // 클릭시 상세 정보 보기
                showAlertDetails(alert.id)
            },
            onDeleteClick = { alert ->
                // 개별 삭제
                showDeleteConfirmation(alert)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadPhishingHistory() {
        Log.d(TAG, "위험 문자 기록 로딩 시작")
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "데이터베이스 접근 시도")
                val dao = database.phishingAlertDao()
                Log.d(TAG, "DAO 획득 성공")
                
                dao.getAllAlerts().collect { alerts ->
                    Log.d(TAG, "위험 문자 기록 ${alerts.size}개 로드됨")
                    adapter.submitList(alerts)
                    
                    if (alerts.isEmpty()) {
                        Log.d(TAG, "데이터가 비어있음 - 빈 상태 UI 표시")
                        emptyState.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        Log.d(TAG, "데이터 로드 완료, 리스트 UI 표시")
                        emptyState.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "데이터 로드 상세 오류: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                // 오류는 로그로만 처리하고 팝업 제거
                // 빈 리스트 상태로 유지
            }
        }
    }
    
    private fun showAlertDetails(alertId: Long) {
        lifecycleScope.launch {
            try {
                val alert = database.phishingAlertDao().getAlertById(alertId)
                if (alert != null) {
                    // 읽음 처리
                    database.phishingAlertDao().markAsRead(alertId)
                    
                    // 상세 다이얼로그 표시
                    PhishingDetailDialog.show(this@PhishingHistoryActivity, alert)
                }
            } catch (e: Exception) {
                Log.e(TAG, "상세 정보 로드 오류: ${e.message}")
            }
        }
    }
    
    private fun showDeleteConfirmation(alert: PhishingAlert) {
        AlertDialog.Builder(this)
            .setTitle("삭제 확인")
            .setMessage("이 문자를 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    database.phishingAlertDao().deleteAlert(alert.id)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("전체 삭제 확인")
            .setMessage("모든 위험 문자 기록을 삭제하시겠습니까?")
            .setPositiveButton("전체 삭제") { _, _ ->
                lifecycleScope.launch {
                    database.phishingAlertDao().deleteAllAlerts()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}