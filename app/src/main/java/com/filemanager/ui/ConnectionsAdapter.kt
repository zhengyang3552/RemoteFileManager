package com.filemanager.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.filemanager.R
import com.filemanager.databinding.ItemConnectionBinding
import com.filemanager.model.ConnectionState
import com.filemanager.model.ProtocolType
import com.filemanager.model.RemoteConnection

/**
 * 抽屉中的连接列表。
 * 状态点用语义色表达连接健康度（已连接=success，连接中=warning，异常=error，未连接=outline），
 * 当前激活的连接用主色高亮名称，避免只靠一个圆点区分。
 */
class ConnectionsAdapter(
    private val onClick: (RemoteConnection) -> Unit,
    private val onMoreClick: (RemoteConnection, View) -> Unit
) : ListAdapter<RemoteConnection, ConnectionsAdapter.ConnectionViewHolder>(DIFF) {

    private var activeId: String? = null

    fun setActiveId(id: String?) {
        if (activeId == id) return
        val previous = activeId
        activeId = id
        currentList.indexOfFirst { it.id == previous }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnectionViewHolder {
        val binding = ItemConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConnectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConnectionViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).id == activeId)
    }

    inner class ConnectionViewHolder(private val binding: ItemConnectionBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(connection: RemoteConnection, isActive: Boolean) {
            val context = binding.root.context
            binding.tvName.text = connection.name
            binding.tvDetail.text = "${context.getString(connection.type.labelRes)} · ${connection.endpoint}"

            binding.ivProtocol.setImageResource(
                when (connection.type) {
                    ProtocolType.SFTP -> R.drawable.ic_lock
                    ProtocolType.SMB -> R.drawable.ic_folder
                    ProtocolType.WEBDAV -> R.drawable.ic_cloud
                    ProtocolType.FTP, ProtocolType.FTPS -> R.drawable.ic_link
                }
            )

            val nameColor = if (isActive) R.color.color_primary else R.color.color_on_surface
            binding.tvName.setTextColor(ContextCompat.getColor(context, nameColor))

            val statusColor = when (connection.connectionState) {
                ConnectionState.CONNECTED -> R.color.color_success
                ConnectionState.CONNECTING -> R.color.color_warning
                ConnectionState.ERROR -> R.color.color_error
                ConnectionState.DISCONNECTED -> R.color.color_outline
            }
            binding.vStatus.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, statusColor))

            binding.root.setOnClickListener { onClick(connection) }
            binding.btnMore.setOnClickListener { onMoreClick(connection, binding.btnMore) }
            binding.root.setOnLongClickListener {
                onMoreClick(connection, binding.root)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RemoteConnection>() {
            override fun areItemsTheSame(oldItem: RemoteConnection, newItem: RemoteConnection): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: RemoteConnection, newItem: RemoteConnection): Boolean =
                oldItem.name == newItem.name &&
                    oldItem.host == newItem.host &&
                    oldItem.port == newItem.port &&
                    oldItem.type == newItem.type &&
                    oldItem.connectionState == newItem.connectionState
        }
    }
}
