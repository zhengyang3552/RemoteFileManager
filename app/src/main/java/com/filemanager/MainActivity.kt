package com.filemanager

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.SpannedString
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filemanager.manager.ConnectionManager
import com.filemanager.model.*
import com.google.android.material.navigation.NavigationView
import java.io.File
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var navView: NavigationView
    private lateinit var recyclerFiles: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var etPath: EditText
    private lateinit var tvStatus: TextView
    
    private lateinit var fileAdapter: FileAdapter
    private lateinit var connectionAdapter: ConnectionAdapter
    
    private val connectionManager = ConnectionManager()
    private var selectedConnection: RemoteConnection? = null
    private var localDownloadDir: String = Environment.getExternalStorageDirectory().absolutePath + "/RemoteFiles"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupConnectionList()
        setupNavigation()
        setupPermission()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        toolbar = findViewById(R.id.toolbar)
        navView = findViewById(R.id.nav_view)
        recyclerFiles = findViewById(R.id.recycler_files)
        etPath = findViewById(R.id.et_path)
        tvStatus = findViewById(R.id.tv_status)

        findViewById<ImageButton>(R.id.btn_home)?.setOnClickListener { navigateToPath("/") }
        findViewById<ImageButton>(R.id.btn_parent)?.setOnClickListener { navigateParent() }
        findViewById<ImageButton>(R.id.btn_go)?.setOnClickListener { navigateToPath(etPath.text.toString()) }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
        
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.nav_open, R.string.nav_open
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter { file ->
            if (file.isDirectory) {
                val path = if (etPath.text.toString() == "/") "/${file.name}" else "${etPath.text.toString()}/${file.name}"
                if (connectionManager.changeDirectory(selectedConnection!!, path)) {
                    loadFiles(path)
                }
            } else {
                showDownloadDialog(file)
            }
        }

        recyclerFiles.layoutManager = LinearLayoutManager(this)
        recyclerFiles.adapter = fileAdapter
        recyclerFiles.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        
        // 设置长按菜单 - use ItemTouchHelper for swipe and long press
        recyclerFiles.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    val view = rv.findChildViewUnder(e.x, e.y)
                    if (e.action == MotionEvent.ACTION_DOWN && view != null) {
                        // Long press
                        rv.postDelayed({
                            val position = rv.getChildAdapterPosition(view)
                            if (position > 0) {
                                showFileContextMenu(position - 1)
                            }
                        }, 500)
                    }
                    return false
                }
            }
        )
    }

    private fun setupConnectionList() {
        // 初始化连接列表
        val connections = connectionManager.getConnections()
        if (connections.isNotEmpty()) {
            selectedConnection = connections[0]
            updateStatus("已选择连接: ${selectedConnection!!.name}")
            loadFiles("/")
        }
    }

    private fun setupNavigation() {
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_add_connection -> {
                    showNewConnectionDialog()
                }
                R.id.nav_refresh -> {
                    selectedConnection?.let { loadFiles(connectionManager.getCurrentDirectory(it)) }
                }
                R.id.nav_download -> {
                    if (selectedConnection != null) {
                        showDownloadDialog()
                    } else {
                        Toast.makeText(this, "请先连接服务器", Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.nav_upload -> {
                    if (selectedConnection != null) {
                        uploadFile()
                    } else {
                        Toast.makeText(this, "请先连接服务器", Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.nav_about -> {
                    showAboutDialog()
                }
            }
            drawerLayout.closeDrawers()
            true
        }
    }

    private var storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            updateStatus("存储权限已授予")
        } else {
            updateStatus("存储权限未完全授予，部分功能可能不可用")
        }
    }

    private fun setupPermission() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            storagePermissionLauncher.launch(permissions)
        }
    }

    private fun showNewConnectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_connection, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etHost = dialogView.findViewById<EditText>(R.id.et_host)
        val etPort = dialogView.findViewById<EditText>(R.id.et_port)
        val etUsername = dialogView.findViewById<EditText>(R.id.et_username)
        val etPassword = dialogView.findViewById<EditText>(R.id.et_password)
        val etPath = dialogView.findViewById<EditText>(R.id.et_path)
        val cbSsl = dialogView.findViewById<CheckBox>(R.id.cb_ssl)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinner_protocol)

        // 设置协议列表
        val protocols = arrayOf("FTP", "FTPS", "SFTP", "SMB", "WebDAV")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, protocols)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("新建连接")
            .setView(dialogView)
            .setPositiveButton("连接") { _, _ ->
                val name = etName.text.toString().ifEmpty { "New Connection" }
                val protocol = protocols[spinner.selectedItemPosition]
                val host = etHost.text.toString()
                val port = etPort.text.toString().toIntOrNull() ?: when (protocol) {
                    "FTP" -> 21
                    "FTPS" -> 990
                    "SFTP" -> 22
                    "SMB" -> 445
                    "WebDAV" -> 80
                    else -> 21
                }
                val username = etUsername.text.toString()
                val password = etPassword.text.toString()
                val path = etPath.text.toString().ifEmpty { "/" }
                val useSsl = cbSsl.isChecked && protocol == "FTPS"

                val connection = RemoteConnection(
                    name = name,
                    type = when (protocol) {
                        "FTP" -> ProtocolType.FTP
                        "FTPS" -> ProtocolType.FTPS
                        "SFTP" -> ProtocolType.SFTP
                        "SMB" -> ProtocolType.SMB
                        "WebDAV" -> ProtocolType.WEBDAV
                        else -> ProtocolType.FTP
                    },
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    path = path,
                    useSsl = useSsl
                )

                if (connectionManager.addConnection(connection)) {
                    Toast.makeText(this, "连接已添加", Toast.LENGTH_SHORT).show()
                    if (connectToServer(connection)) {
                        selectedConnection = connection
                        updateStatus("已连接: $name")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun connectToServer(connection: RemoteConnection): Boolean {
        val success = connectionManager.connect(connection)
        
        if (success) {
            updateStatus("已连接: ${connection.name} - ${connection.host}")
            loadFiles(connectionManager.getCurrentDirectory(connection))
        } else {
            Toast.makeText(this, "连接失败: ${connection.errorMessage}", Toast.LENGTH_LONG).show()
        }
        
        return success
    }

    private fun loadFiles(path: String) {
        if (selectedConnection == null) return
        
        try {
            tvStatus.text = "正在加载: $path"
            val files = connectionManager.listFiles(selectedConnection!!, path)
            
            fileAdapter.submitList(files)
            etPath.setText(path)
            
            if (path != "/") {
                val newList = mutableListOf<FileInfo>()
                newList.add(FileInfo("..", true, 0, "", null, null))
                newList.addAll(files)
                fileAdapter.submitList(newList)
            }
            
            updateStatus("已加载 ${files.size} 个项目")
        } catch (e: Exception) {
            tvStatus.text = "加载失败: ${e.message}"
            e.printStackTrace()
        }
    }

    private fun navigateToPath(path: String) {
        if (path.isNotEmpty() && selectedConnection != null) {
            loadFiles(path)
        }
    }

    private fun navigateParent() {
        val currentPath = selectedConnection?.let { connectionManager.getCurrentDirectory(it) } ?: return
        if (currentPath == "/") return
        
        val parentPath = currentPath.substringBeforeLast("/").ifEmpty { "/" }
        loadFiles(parentPath)
    }

    private fun showDownloadDialog(file: FileInfo? = null) {
        val fileName = file?.name ?: "download_file"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        
        intentLauncher.launch(intent)
    }

    private var intentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    val file = fileAdapter.items.getOrNull(resultAdapterPosition)?.let { f ->
                        if (f.name != "..") f else null
                    } ?: return@use
                    val success = connectionManager.downloadFile(
                        selectedConnection!!,
                        "${etPath.text.toString()}/${file.name}",
                        outputStream
                    )
                    if (success) {
                        Toast.makeText(this, "下载成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private var resultAdapterPosition: Int = 0

    private fun uploadFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
        }
        
        filePickerLauncher.launch(intent)
    }

    private var filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        uri?.let { pickedUri ->
            contentResolver.openInputStream(pickedUri)?.use { inputStream ->
                val fileName = pickedUri.lastPathSegment ?: "uploaded_file"
                val currentPath = selectedConnection?.let { conn -> connectionManager.getCurrentDirectory(conn) } ?: "/"
                val remotePath = if (currentPath == "/") "/$fileName" else "$currentPath/$fileName"
                
                val success = connectionManager.uploadFile(
                    selectedConnection!!,
                    inputStream,
                    remotePath
                )
                
                if (success) {
                    Toast.makeText(this, "上传成功: $fileName", Toast.LENGTH_SHORT).show()
                    loadFiles(currentPath)
                } else {
                    Toast.makeText(this, "上传失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFileContextMenu(position: Int) {
        val file = fileAdapter.getItem(position) ?: return
        
        val items = arrayOf("下载", "重命名", "删除", "复制", "移动")
        
        AlertDialog.Builder(this)
            .setTitle("操作: ${file.name}")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        resultAdapterPosition = position
                        showDownloadDialog(file)
                    }
                    1 -> {
                        showRenameDialog(file)
                    }
                    2 -> {
                        AlertDialog.Builder(this)
                            .setTitle("确认删除")
                            .setMessage("确定要删除 \"${file.name}\" 吗？")
                            .setPositiveButton("删除") { _, _ ->
                                val currentPath = selectedConnection?.let { connectionManager.getCurrentDirectory(it) } ?: "/"
                                val fullPath = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                                val success = connectionManager.delete(selectedConnection!!, fullPath)
                                if (success) {
                                    loadFiles(currentPath)
                                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                    3 -> {
                        showCopyDialog(file)
                    }
                    4 -> {
                        showMoveDialog(file)
                    }
                }
            }
            .show()
    }

    private fun showRenameDialog(file: FileInfo) {
        AlertDialog.Builder(this)
            .setTitle("重命名")
            .setView(R.layout.dialog_new_connection)
            .setPositiveButton("保存") { dialog, _ ->
                val editText = (dialog as AlertDialog).findViewById<EditText>(R.id.et_name)
                val newName = editText?.text?.toString() ?: return@setPositiveButton
                val currentPath = selectedConnection?.let { connectionManager.getCurrentDirectory(it) } ?: "/"
                val oldPath = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                val success = connectionManager.rename(selectedConnection!!, oldPath, newName)
                if (success) {
                    loadFiles(currentPath)
                    Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCopyDialog(file: FileInfo) {
        AlertDialog.Builder(this)
            .setTitle("复制")
            .setMessage("复制 \"${file.name}\" 到当前目录？")
            .setPositiveButton("复制") { _, _ ->
                val currentPath = selectedConnection?.let { connectionManager.getCurrentDirectory(it) } ?: "/"
                val sourcePath = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                val destPath = "$currentPath/copy_of_${file.name}"
                val success = connectionManager.copyRemote(selectedConnection!!, sourcePath, destPath)
                if (success) {
                    loadFiles(currentPath)
                    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showMoveDialog(file: FileInfo) {
        showRenameDialog(file)
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("关于")
            .setMessage("远程文件管理器 v1.0\n\n支持 FTP、FTPS、SFTP、SMB、WebDAV 协议\n\n基于 Kotlin + Android 开发")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun updateStatus(text: String) {
        tvStatus.text = text
    }

    override fun onSupportNavigateUp(): Boolean {
        drawerLayout.openDrawer(Gravity.START)
        return true
    }
}

// 文件适配器
class FileAdapter(
    private val onItemClick: (FileInfo) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    var items: MutableList<FileInfo> = mutableListOf()

    fun submitList(list: List<FileInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): FileInfo? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = items[position]
        holder.bind(file)
        holder.itemView.setOnClickListener {
            if (position > 0) {
                onItemClick(file)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvInfo: TextView = itemView.findViewById(R.id.tv_info)
        private val ivIcon: ImageView = itemView.findViewById(R.id.iv_icon)

        fun bind(file: FileInfo) {
            tvName.text = file.name
            tvInfo.text = "${file.formattedSize} | ${file.lastModified ?: "-"}"
            ivIcon.setImageResource(if (file.isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
        }
    }
}

// 连接适配器
class ConnectionAdapter(
    private val onItemClick: (RemoteConnection) -> Unit
) : RecyclerView.Adapter<ConnectionAdapter.ConnectionViewHolder>() {

    private var items: MutableList<RemoteConnection> = mutableListOf()

    fun submitList(list: List<RemoteConnection>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_connection, parent, false)
        return ConnectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConnectionViewHolder, position: Int) {
        val connection = items[position]
        holder.bind(connection)
        holder.itemView.setOnClickListener {
            onItemClick(connection)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ConnectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvDetail: TextView = itemView.findViewById(R.id.tv_detail)
        private val ivStatus: ImageView = itemView.findViewById(R.id.iv_status)

        fun bind(connection: RemoteConnection) {
            tvName.text = connection.name
            tvDetail.text = "${connection.type.name} - ${connection.host}:${connection.port}"
            ivStatus.setImageResource(
                when (connection.connectionState) {
                    ConnectionState.CONNECTED -> android.R.drawable.ic_menu_compass
                    ConnectionState.CONNECTING -> android.R.drawable.ic_menu_rotate
                    ConnectionState.ERROR -> R.drawable.ic_menu_report_image
                    else -> R.drawable.ic_menu_map_mode
                }
            )
        }
    }
}
