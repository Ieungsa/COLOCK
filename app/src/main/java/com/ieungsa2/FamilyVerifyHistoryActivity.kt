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
import com.ieungsa2.database.FamilyVerifyDatabase
import kotlinx.coroutines.launch

class FamilyVerifyHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: FamilyVerifyLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_verify_history)

        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }

        recyclerView = findViewById(R.id.family_verify_history_recycler)
        emptyView = findViewById(R.id.empty_view)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FamilyVerifyLogAdapter(
            onItemClick = { log ->
                showLogDetail(log)
            },
            onDeleteClick = { log ->
                showDeleteConfirmation(log.id)
            }
        )
        recyclerView.adapter = adapter

        loadLogs()
    }

    private fun loadLogs() {
        lifecycleScope.launch {
            val db = FamilyVerifyDatabase.getDatabase(applicationContext)
            val logs = db.familyVerifyLogDao().getAllLogs()

            if (logs.isEmpty()) {
                emptyView.visibility = TextView.VISIBLE
                recyclerView.visibility = RecyclerView.GONE
            } else {
                emptyView.visibility = TextView.GONE
                recyclerView.visibility = RecyclerView.VISIBLE
                adapter.submitList(logs)
            }
        }
    }

    private fun showLogDetail(log: com.ieungsa2.database.FamilyVerifyLog) {
        val typeText = if (log.requestType == "SENT") "보낸 요청" else "받은 요청"
        val statusText = when (log.status) {
            "PENDING" -> "⏳ 대기 중"
            "APPROVED" -> "✅ 승인됨"
            "REJECTED" -> "❌ 거절됨"
            else -> log.status
        }

        val message = """
            요청 유형: $typeText
            내 번호: ${log.myPhone}
            상대방 번호: ${log.targetPhone}
            상태: $statusText
            시각: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(log.timestamp)}
            요청 ID: ${log.requestId}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("🔐 인증 요청 상세")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showDeleteConfirmation(logId: Long) {
        AlertDialog.Builder(this)
            .setTitle("기록 삭제")
            .setMessage("이 인증 기록을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                deleteLog(logId)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteLog(logId: Long) {
        lifecycleScope.launch {
            val db = FamilyVerifyDatabase.getDatabase(applicationContext)
            db.familyVerifyLogDao().delete(logId)
            Toast.makeText(this@FamilyVerifyHistoryActivity, "삭제되었습니다", Toast.LENGTH_SHORT).show()
            loadLogs()
        }
    }

    override fun onResume() {
        super.onResume()
        loadLogs()
    }
}
