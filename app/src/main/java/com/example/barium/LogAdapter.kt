package com.example.barium

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val logs = mutableListOf<LogItem>()

    data class LogItem(val id: String, val message: String, var status: String)

    fun addLog(id: String, log: String, status: String) {
        logs.add(LogItem(id, log, status))
        notifyItemInserted(logs.size - 1)
    }

    fun updateLogStatus(id: String, status: String) {
        for (log in logs) {
            if (log.id == id) {
                log.status = status
                notifyItemChanged(logs.indexOf(log))
                break
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.log_item, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logTextView: TextView = itemView.findViewById(R.id.logTextView)

        fun bind(log: LogItem) {
            logTextView.text = log.message
            when (log.status) {
                "sent" -> logTextView.setBackgroundColor(Color.YELLOW)
                "acknowledged" -> logTextView.setBackgroundColor(Color.GREEN)
                "received" -> logTextView.setBackgroundColor(Color.GRAY)
                else -> logTextView.setBackgroundColor(Color.WHITE)
            }
        }
    }
}
