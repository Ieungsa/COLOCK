package com.ieungsa2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ieungsa2.database.DataLeakAlert
import java.text.SimpleDateFormat
import java.util.Locale

class DataLeakAlertAdapter(
    private val onItemClick: (DataLeakAlert) -> Unit,
    private val onDeleteClick: (DataLeakAlert) -> Unit
) : ListAdapter<DataLeakAlert, DataLeakAlertAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_data_leak_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appNameText: TextView = itemView.findViewById(R.id.app_name_text)
        private val usageText: TextView = itemView.findViewById(R.id.usage_text)
        private val timestampText: TextView = itemView.findViewById(R.id.timestamp_text)
        private val screenStatusIcon: TextView = itemView.findViewById(R.id.screen_status_icon)
        private val deleteButton: ImageView = itemView.findViewById(R.id.delete_button)

        fun bind(alert: DataLeakAlert) {
            appNameText.text = alert.appName
            usageText.text = "${"%.2f".format(alert.usageMB)}MB (임계값: ${alert.thresholdMB}MB)"

            val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            timestampText.text = dateFormat.format(alert.timestamp)

            screenStatusIcon.text = if (alert.wasScreenOff) "🚨" else "⚠️"

            itemView.setOnClickListener {
                onItemClick(alert)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(alert)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DataLeakAlert>() {
        override fun areItemsTheSame(oldItem: DataLeakAlert, newItem: DataLeakAlert): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DataLeakAlert, newItem: DataLeakAlert): Boolean {
            return oldItem == newItem
        }
    }
}
