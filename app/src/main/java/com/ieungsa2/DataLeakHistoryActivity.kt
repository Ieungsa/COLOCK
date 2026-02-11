package com.ieungsa2

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ieungsa2.database.DataLeakDatabase
import kotlinx.coroutines.launch

class DataLeakHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: DataLeakAlertAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_leak_history)

        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }

        recyclerView = findViewById(R.id.data_leak_history_recycler)
        emptyView = findViewById(R.id.empty_view)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DataLeakAlertAdapter(
            onItemClick = { alert ->
                // 상세 정보 표시
                showAlertDetail(alert)
            },
            onDeleteClick = { alert ->
                // 삭제 확인
                showDeleteConfirmation(alert.id)
            }
        )
        recyclerView.adapter = adapter

        loadAlerts()
    }

    private fun loadAlerts() {
        lifecycleScope.launch {
            val db = DataLeakDatabase.getDatabase(applicationContext)
            val alerts = db.dataLeakAlertDao().getAllAlerts()

            if (alerts.isEmpty()) {
                emptyView.visibility = TextView.VISIBLE
                recyclerView.visibility = RecyclerView.GONE
            } else {
                emptyView.visibility = TextView.GONE
                recyclerView.visibility = RecyclerView.VISIBLE
                adapter.submitList(alerts)
            }
        }
    }

    private fun showAlertDetail(alert: com.ieungsa2.database.DataLeakAlert) {
        val message = """
            앱 이름: ${alert.appName}
            패키지명: ${alert.packageName}
            데이터 사용량: ${"%.2f".format(alert.usageMB)}MB
            임계값: ${alert.thresholdMB}MB
            화면 상태: ${if (alert.wasScreenOff) "꺼짐" else "켜짐"}
            발생 시각: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(alert.timestamp)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("⚠️ 데이터 유출 경고 상세")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showDeleteConfirmation(alertId: Long) {
        AlertDialog.Builder(this)
            .setTitle("기록 삭제")
            .setMessage("이 경고 기록을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                deleteAlert(alertId)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteAlert(alertId: Long) {
        lifecycleScope.launch {
            val db = DataLeakDatabase.getDatabase(applicationContext)
            db.dataLeakAlertDao().delete(alertId)
            Toast.makeText(this@DataLeakHistoryActivity, "삭제되었습니다", Toast.LENGTH_SHORT).show()
            loadAlerts()
        }
    }

    override fun onResume() {
        super.onResume()
        loadAlerts()
    }
}
