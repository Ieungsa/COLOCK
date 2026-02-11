package com.ieungsa2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ieungsa2.database.FamilyVerifyLog
import java.text.SimpleDateFormat
import java.util.Locale

class FamilyVerifyLogAdapter(
    private val onItemClick: (FamilyVerifyLog) -> Unit,
    private val onDeleteClick: (FamilyVerifyLog) -> Unit
) : ListAdapter<FamilyVerifyLog, FamilyVerifyLogAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_family_verify_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeIcon: TextView = itemView.findViewById(R.id.type_icon)
        private val phoneText: TextView = itemView.findViewById(R.id.phone_text)
        private val statusText: TextView = itemView.findViewById(R.id.status_text)
        private val timestampText: TextView = itemView.findViewById(R.id.timestamp_text)
        private val deleteButton: ImageView = itemView.findViewById(R.id.delete_button)

        fun bind(log: FamilyVerifyLog) {
            // 요청 유형에 따라 아이콘 변경
            typeIcon.text = if (log.requestType == "SENT") "📤" else "📥"

            // 전화번호 표시
            val phoneLabel = if (log.requestType == "SENT") "받는 사람" else "보낸 사람"
            val phoneNumber = if (log.requestType == "SENT") log.targetPhone else log.myPhone
            phoneText.text = "$phoneLabel: $phoneNumber"

            // 상태 표시
            statusText.text = when (log.status) {
                "PENDING" -> "⏳ 대기 중"
                "APPROVED" -> "✅ 승인됨"
                "REJECTED" -> "❌ 거절됨"
                else -> log.status
            }

            statusText.setTextColor(
                when (log.status) {
                    "APPROVED" -> itemView.context.getColor(R.color.status_success)
                    "REJECTED" -> itemView.context.getColor(R.color.status_danger)
                    else -> itemView.context.getColor(R.color.status_warning)
                }
            )

            val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            timestampText.text = dateFormat.format(log.timestamp)

            itemView.setOnClickListener {
                onItemClick(log)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(log)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<FamilyVerifyLog>() {
        override fun areItemsTheSame(oldItem: FamilyVerifyLog, newItem: FamilyVerifyLog): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FamilyVerifyLog, newItem: FamilyVerifyLog): Boolean {
            return oldItem == newItem
        }
    }
}
