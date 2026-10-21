package com.filemanager.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.filemanager.R
import com.filemanager.databinding.ItemFileBinding
import com.filemanager.model.FileInfo
import com.filemanager.util.Formatters

/**
 * 文件列表适配器。
 *
 * - 使用 [ListAdapter] + DiffUtil，避免 `notifyDataSetChanged` 导致的整表闪烁；
 * - 图标容器通过「文件夹 / 文件 / 上级目录」三种视觉语义区分，替代过去靠系统菜单图标凑数；
 * - 主次文本由 [Formatters] 统一生成，保证列表内格式一致。
 */
class FilesAdapter(
    private val onClick: (FileInfo) -> Unit,
    private val onMoreClick: (FileInfo, View) -> Unit
) : ListAdapter<FileInfo, FilesAdapter.FileViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: FileInfo) {
            val context = binding.root.context
            binding.tvName.text = if (file.isParentLink) context.getString(R.string.action_parent) else file.name
            binding.tvMeta.text = buildMeta(context, file)

            when {
                file.isParentLink -> {
                    binding.vIconTile.setBackgroundResource(R.drawable.bg_icon_tile_parent)
                    binding.vIconTile.backgroundTintList = null
                    binding.ivIcon.setImageResource(R.drawable.ic_arrow_upward)
                    binding.ivIcon.imageTintList = colorState(context, R.color.color_outline)
                    binding.ivIcon.contentDescription = context.getString(R.string.cd_parent_icon)
                    binding.btnMore.visibility = View.INVISIBLE
                }
                file.isDirectory -> {
                    binding.vIconTile.setBackgroundResource(R.drawable.bg_icon_tile_folder)
                    binding.vIconTile.backgroundTintList = null
                    binding.ivIcon.setImageResource(R.drawable.ic_folder)
                    binding.ivIcon.imageTintList = colorState(context, R.color.color_on_secondary_container)
                    binding.ivIcon.contentDescription = context.getString(R.string.cd_folder_icon)
                    binding.btnMore.visibility = View.VISIBLE
                }
                else -> {
                    binding.vIconTile.setBackgroundResource(R.drawable.bg_icon_tile_file)
                    binding.vIconTile.backgroundTintList = null
                    binding.ivIcon.setImageResource(R.drawable.ic_file)
                    binding.ivIcon.imageTintList = colorState(context, R.color.color_on_surface_variant)
                    binding.ivIcon.contentDescription = context.getString(R.string.cd_file_icon)
                    binding.btnMore.visibility = View.VISIBLE
                }
            }

            binding.root.setOnClickListener { onClick(file) }
            binding.btnMore.setOnClickListener { onMoreClick(file, binding.btnMore) }
        }

        private fun buildMeta(context: Context, file: FileInfo): String {
            if (file.isParentLink) return context.getString(R.string.label_parent)
            return Formatters.formatMeta(
                isDirectory = file.isDirectory,
                size = if (file.hasKnownSize) file.size else FileInfo.SIZE_UNKNOWN,
                lastModified = file.lastModified,
                directoryLabel = context.getString(R.string.label_directory),
                fileLabel = context.getString(R.string.label_file)
            )
        }

        private fun colorState(context: Context, colorRes: Int): ColorStateList =
            ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FileInfo>() {
            override fun areItemsTheSame(oldItem: FileInfo, newItem: FileInfo): Boolean =
                oldItem.name == newItem.name && oldItem.isParentLink == newItem.isParentLink

            override fun areContentsTheSame(oldItem: FileInfo, newItem: FileInfo): Boolean = oldItem == newItem
        }
    }
}
