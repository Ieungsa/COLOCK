package com.ieungsa2

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.ieungsa2.database.VishingAlert
import java.text.SimpleDateFormat
import java.util.*

object VishingDetailDialog {

    fun show(context: Context, alert: VishingAlert) {
        val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss", Locale.getDefault())

        val message = buildString {
            appendLine("📅 감지 시간: ${dateFormat.format(alert.timestamp)}")
            appendLine()
            appendLine("📞 전화번호: ${alert.phoneNumber}")
            appendLine()
            appendLine("🚨 위험 등급: ${alert.riskLevel}")
            appendLine("📊 위험도: ${(alert.riskScore * 100).toInt()}%")
            appendLine()
            if (alert.detectedKeywords.isNotEmpty()) {
                appendLine("🔍 감지된 키워드:")
                appendLine(alert.detectedKeywords)
                appendLine()
            }
            appendLine("💬 통화 내용 (STT):")
            append(alert.transcription)
        }

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_vishing_detail, null)
        val contentText = view.findViewById<TextView>(R.id.detail_content)
        val btnCopyTranscription = view.findViewById<Button>(R.id.btn_copy_transcription)
        val btnReport = view.findViewById<Button>(R.id.btn_report)
        val btnConfirm = view.findViewById<Button>(R.id.btn_confirm)

        contentText.text = message

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        btnCopyTranscription.setOnClickListener {
            copyToClipboard(context, alert.transcription)
        }

        btnReport.setOnClickListener {
            reportVishing(context, alert.phoneNumber, alert.transcription)
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // 다이얼로그 최대 높이 제한 (화면 높이의 80%)
        dialog.window?.let { window ->
            val displayMetrics = context.resources.displayMetrics
            val maxHeight = (displayMetrics.heightPixels * 0.8).toInt()
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            // 높이가 maxHeight를 초과하면 제한
            view.post {
                if (view.height > maxHeight) {
                    window.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        maxHeight
                    )
                }
            }
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("COLOCK", text)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(context, "클립보드에 복사되었습니다", Toast.LENGTH_SHORT).show()
    }

    private fun reportVishing(context: Context, phoneNumber: String, transcription: String) {
        Toast.makeText(context, "보이스피싱 신고가 접수되었습니다", Toast.LENGTH_SHORT).show()
        android.util.Log.d("VishingDetailDialog", "보이스피싱 신고: 전화번호=$phoneNumber")
    }
}
