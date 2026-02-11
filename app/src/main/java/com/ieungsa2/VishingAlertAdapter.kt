package com.ieungsa2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ieungsa2.database.VishingAlert
import java.text.SimpleDateFormat
import java.util.*

class VishingAlertAdapter(
    private val onItemClick: (VishingAlert) -> Unit,
    private val onDeleteClick: (VishingAlert) -> Unit
) : ListAdapter<VishingAlert, VishingAlertAdapter.AlertViewHolder>(AlertDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vishing_alert, parent, false)
        return AlertViewHolder(view, onItemClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlertViewHolder(
        itemView: View,
        private val onItemClick: (VishingAlert) -> Unit,
        private val onDeleteClick: (VishingAlert) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val timeText: TextView = itemView.findViewById(R.id.text_time)
        private val phoneNumberText: TextView = itemView.findViewById(R.id.text_phone_number)
        private val transcriptionPreview: TextView = itemView.findViewById(R.id.text_transcription_preview)
        private val riskLevelText: TextView = itemView.findViewById(R.id.text_risk_level)
        private val keywordsText: TextView = itemView.findViewById(R.id.text_keywords)
        private val unreadIndicator: View = itemView.findViewById(R.id.unread_indicator)
        private val deleteButton: TextView = itemView.findViewById(R.id.delete_button)

        private val timeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        fun bind(alert: VishingAlert) {
            timeText.text = timeFormat.format(alert.timestamp)
            phoneNumberText.text = alert.phoneNumber

            // 받아쓰기 미리보기 (첫 50자)
            transcriptionPreview.text = if (alert.transcription.length > 50) {
                "${alert.transcription.take(50)}..."
            } else {
                alert.transcription
            }

            riskLevelText.text = alert.riskLevel

            // 키워드 표시
            if (alert.detectedKeywords.isNotEmpty()) {
                keywordsText.text = "🔍 ${alert.detectedKeywords}"
                keywordsText.visibility = View.VISIBLE
            } else {
                keywordsText.visibility = View.GONE
            }

            // 위험 등급에 따른 색상 설정
            val riskColor = when (alert.riskLevel) {
                "매우 위험" -> android.graphics.Color.parseColor("#E53E3E")
                "위험" -> android.graphics.Color.parseColor("#F56500")
                "의심" -> android.graphics.Color.parseColor("#D69E2E")
                else -> android.graphics.Color.parseColor("#4A5568")
            }
            riskLevelText.setTextColor(riskColor)

            // 읽지 않은 메시지 표시
            unreadIndicator.visibility = if (!alert.isRead) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onItemClick(alert)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(alert)
            }
        }
    }

    class AlertDiffCallback : DiffUtil.ItemCallback<VishingAlert>() {
        override fun areItemsTheSame(oldItem: VishingAlert, newItem: VishingAlert): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VishingAlert, newItem: VishingAlert): Boolean {
            return oldItem == newItem
        }
    }
}
