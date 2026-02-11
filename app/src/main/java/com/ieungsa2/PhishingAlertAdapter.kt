package com.ieungsa2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ieungsa2.database.PhishingAlert
import java.text.SimpleDateFormat
import java.util.*

class PhishingAlertAdapter(
    private val onItemClick: (PhishingAlert) -> Unit,
    private val onDeleteClick: (PhishingAlert) -> Unit
) : ListAdapter<PhishingAlert, PhishingAlertAdapter.AlertViewHolder>(AlertDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phishing_alert, parent, false)
        return AlertViewHolder(view, onItemClick, onDeleteClick)
    }
    
    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class AlertViewHolder(
        itemView: View,
        private val onItemClick: (PhishingAlert) -> Unit,
        private val onDeleteClick: (PhishingAlert) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val timeText: TextView = itemView.findViewById(R.id.text_time)
        private val senderText: TextView = itemView.findViewById(R.id.text_sender)
        private val urlText: TextView = itemView.findViewById(R.id.text_url)
        private val riskLevelText: TextView = itemView.findViewById(R.id.text_risk_level)
        private val messagePreview: TextView = itemView.findViewById(R.id.text_message_preview)
        private val unreadIndicator: View = itemView.findViewById(R.id.unread_indicator)
        private val deleteButton: TextView = itemView.findViewById(R.id.delete_button)
        
        private val timeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        
        fun bind(alert: PhishingAlert) {
            timeText.text = timeFormat.format(alert.timestamp)
            senderText.text = alert.sender
            urlText.text = alert.detectedUrl
            riskLevelText.text = alert.riskLevel
            
            // 메시지 미리보기 (첫 50자)
            messagePreview.text = if (alert.messageBody.length > 50) {
                "${alert.messageBody.take(50)}..."
            } else {
                alert.messageBody
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
    
    class AlertDiffCallback : DiffUtil.ItemCallback<PhishingAlert>() {
        override fun areItemsTheSame(oldItem: PhishingAlert, newItem: PhishingAlert): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: PhishingAlert, newItem: PhishingAlert): Boolean {
            return oldItem == newItem
        }
    }
}