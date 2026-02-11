package com.ieungsa2

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ieungsa2.database.VishingDatabase
import com.ieungsa2.database.VishingAlert
import kotlinx.coroutines.launch

class VishingHistoryActivity : BaseActivity() {

    companion object {
        private const val TAG = "VishingHistoryActivity"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var adapter: VishingAlertAdapter
    private lateinit var database: VishingDatabase
    private lateinit var deleteAllButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vishing_history)

        initViews()
        setupRecyclerView()
        loadVishingHistory()
    }

    private fun initViews() {
        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }

        recyclerView = findViewById(R.id.recycler_view_vishing_alerts)
        emptyState = findViewById(R.id.empty_state)
        deleteAllButton = findViewById(R.id.delete_all_button)
        database = VishingDatabase.getDatabase(this)

        deleteAllButton.setOnClickListener {
            showDeleteAllConfirmation()
        }
    }

    private fun setupRecyclerView() {
        adapter = VishingAlertAdapter(
            onItemClick = { alert ->
                // 클릭시 상세 정보 보기
                showAlertDetails(alert)
            },
            onDeleteClick = { alert ->
                // 개별 삭제
                showDeleteConfirmation(alert)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadVishingHistory() {
        Log.d(TAG, "보이스피싱 기록 로딩 시작")

        lifecycleScope.launch {
            try {
                val dao = database.vishingAlertDao()
                val alerts = dao.getAllAlerts()

                Log.d(TAG, "보이스피싱 기록 ${alerts.size}개 로드됨")
                adapter.submitList(alerts)

                if (alerts.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    deleteAllButton.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    deleteAllButton.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "데이터 로드 오류: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun showAlertDetails(alert: VishingAlert) {
        lifecycleScope.launch {
            try {
                // 읽음 처리
                if (!alert.isRead) {
                    val updatedAlert = alert.copy(isRead = true)
                    database.vishingAlertDao().update(updatedAlert)
                }

                // 상세 다이얼로그 표시
                VishingDetailDialog.show(this@VishingHistoryActivity, alert)

                // 리스트 갱신
                loadVishingHistory()
            } catch (e: Exception) {
                Log.e(TAG, "상세 정보 로드 오류: ${e.message}")
            }
        }
    }

    private fun showDeleteConfirmation(alert: VishingAlert) {
        AlertDialog.Builder(this)
            .setTitle("삭제 확인")
            .setMessage("이 보이스피싱 기록을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    database.vishingAlertDao().delete(alert.id)
                    loadVishingHistory()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("전체 삭제 확인")
            .setMessage("모든 보이스피싱 기록을 삭제하시겠습니까?")
            .setPositiveButton("전체 삭제") { _, _ ->
                lifecycleScope.launch {
                    database.vishingAlertDao().deleteAll()
                    loadVishingHistory()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
