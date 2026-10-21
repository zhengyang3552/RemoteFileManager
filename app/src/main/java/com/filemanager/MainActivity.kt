package com.filemanager

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.filemanager.data.AppSettings
import com.filemanager.data.ConnectionStore
import com.filemanager.databinding.ActivityMainBinding
import com.filemanager.databinding.DialogTextInputBinding
import com.filemanager.manager.ConnectionManager
import com.filemanager.manager.ErrorMapper
import com.filemanager.manager.RemoteException
import com.filemanager.model.FileInfo
import com.filemanager.model.RemoteConnection
import com.filemanager.model.SortField
import com.filemanager.ui.ConnectionEditorDialog
import com.filemanager.ui.ConnectionsAdapter
import com.filemanager.ui.ErrorTexts
import com.filemanager.ui.FilesAdapter
import com.filemanager.ui.showIf
import com.filemanager.ui.showSnack
import com.filemanager.util.FileSorter
import com.filemanager.util.RemotePath
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 文件浏览主界面。
 *
 * 职责划分：
 * - 视图状态机：加载中 / 列表 / 空目录 / 无连接 / 错误，五态互斥，避免出现「空白列表 + 无提示」；
 * - 远程 I/O：全部交给单线程执行器 + 主线程回调，UI 线程不做任何阻塞调用；
 * - 错误处理：统一经 [ErrorTexts] 翻译，操作失败不会静默，也不会让界面停在「加载中」。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var connectionStore: ConnectionStore
    private lateinit var settings: AppSettings
    private lateinit var fileAdapter: FilesAdapter
    private lateinit var connectionAdapter: ConnectionsAdapter
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private val connectionManager by lazy { ConnectionManager(applicationContext) }
    private val remoteExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingTasks = AtomicInteger(0)

    private val connections = mutableListOf<RemoteConnection>()
    private var allFiles: List<FileInfo> = emptyList()
    private var filterQuery: String = ""
    private var pendingDownload: FileInfo? = null
    private var activeConnectionId: String? = null
    private var busy: Boolean = false

    // ---------- Activity Result：SAF 读写 ----------

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = pendingDownload
        pendingDownload = null
        if (uri == null || file == null) return@registerForActivityResult

        val baseDir = connectionManager.currentDirectory
        runRemote(
            task = {
                val output = contentResolver.openOutputStream(uri)
                    ?: throw RemoteException(RemoteException.Reason.INVALID_INPUT, "无法打开目标文件")
                output.use { connectionManager.download(file, it, baseDir) }
            },
            onSuccess = { binding.root.showSnack(getString(R.string.msg_download_success, file.name)) },
            onError = { throwable ->
                // 下载失败时删除写了一半的目标文档，避免留下空文件
                runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
                binding.root.showSnack(ErrorTexts.message(this, throwable))
            }
        )
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        if (!connectionManager.isConnected) {
            binding.root.showSnack(getString(R.string.msg_pick_connection_first))
            return@registerForActivityResult
        }
        val name = resolveDisplayName(uri)
        if (allFiles.any { it.name == name }) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_overwrite_title)
                .setMessage(getString(R.string.dialog_overwrite_message, name))
                .setPositiveButton(R.string.action_overwrite) { _, _ -> upload(uri, name) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        } else {
            upload(uri, name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        connectionStore = ConnectionStore(this)
        settings = AppSettings(this)

        setSupportActionBar(binding.toolbar)
        setupDrawer()
        setupLists()
        setupListeners()
        setupBackHandling()
        syncSortMenu()

        restoreConnections(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CONNECTION_ID, activeConnectionId)
        outState.putString(STATE_PATH, connectionManager.currentDirectory)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 断开连接涉及网络收尾（FTP logout / SSH close），放到工作线程，避免退出时卡顿
        runCatching { remoteExecutor.execute { runCatching { connectionManager.disconnect() } } }
        remoteExecutor.shutdown()
    }

    // ---------- 初始化 ----------

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.action_menu,
            R.string.action_menu
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
    }

    private fun setupLists() {
        fileAdapter = FilesAdapter(
            onClick = ::onFileClicked,
            onMoreClick = { file, _ -> showFileActions(file) }
        )
        binding.recyclerFiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerFiles.adapter = fileAdapter
        ContextCompat.getDrawable(this, R.drawable.divider_list_item)?.let { divider ->
            binding.recyclerFiles.addItemDecoration(
                DividerItemDecoration(this, DividerItemDecoration.VERTICAL).apply { setDrawable(divider) }
            )
        }

        connectionAdapter = ConnectionsAdapter(
            onClick = ::connectTo,
            onMoreClick = { connection, _ -> showConnectionActions(connection) }
        )
        binding.recyclerConnections.layoutManager = LinearLayoutManager(this)
        binding.recyclerConnections.adapter = connectionAdapter
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }

        binding.btnHome.setOnClickListener { navigateHome() }
        binding.btnParent.setOnClickListener { navigateUp() }
        binding.btnRefresh.setOnClickListener { refreshCurrent() }
        binding.tvPath.setOnClickListener { editPath() }
        binding.fabUpload.setOnClickListener { pickFileToUpload() }

        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.color_primary),
            ContextCompat.getColor(this, R.color.color_secondary)
        )
        binding.swipeRefresh.setOnRefreshListener {
            if (connectionManager.isConnected) loadDirectory(connectionManager.currentDirectory) else {
                binding.swipeRefresh.isRefreshing = false
                showDisconnectedState()
            }
        }

        binding.btnNewConnection.setOnClickListener { showNewConnectionDialog() }
        binding.btnTheme.setOnClickListener { showThemeDialog() }
        binding.btnAbout.setOnClickListener { showAboutDialog() }

        binding.etSearch.doAfterTextChanged { editable ->
            filterQuery = editable?.toString().orEmpty().trim()
            renderFiles()
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    binding.tilSearch.isVisible -> toggleSearch()
                    connectionManager.isConnected && !RemotePath.isRoot(connectionManager.currentDirectory) -> navigateUp()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    // ---------- 连接管理 ----------

    private fun restoreConnections(savedInstanceState: Bundle?) {
        connections.clear()
        connections.addAll(connectionStore.load())
        publishConnections()

        val savedId = savedInstanceState?.getString(STATE_CONNECTION_ID) ?: settings.lastConnectionId
        val target = connections.firstOrNull { it.id == savedId } ?: connections.firstOrNull()
        if (target == null) {
            showDisconnectedState()
            setStatus(R.string.status_idle_hint)
            setStatusDot(R.color.color_outline)
        } else {
            connectTo(target, savedInstanceState?.getString(STATE_PATH))
        }
    }

    private fun publishConnections() {
        connectionAdapter.submitList(connections.toList())
        connectionAdapter.setActiveId(activeConnectionId)
        binding.drawerEmpty.showIf(connections.isEmpty())
        binding.recyclerConnections.showIf(connections.isNotEmpty())
    }

    private fun connectTo(connection: RemoteConnection, initialPath: String? = null) {
        if (busy) return
        binding.drawerLayout.closeDrawer(GravityCompat.START)

        activeConnectionId = connection.id
        settings.lastConnectionId = connection.id
        allFiles = emptyList()
        fileAdapter.submitList(emptyList())
        connectionAdapter.setActiveId(connection.id)
        binding.tvCount.text = ""
        setStatusDot(R.color.color_warning)
        setStatus(R.string.status_connecting)
        setBusy(true)

        runRemote(
            task = { connectionManager.connect(connection) },
            onSuccess = { resolvedPath ->
                publishConnections()
                setStatusDot(R.color.color_success)
                val path = initialPath?.takeIf { it != connection.path } ?: resolvedPath
                loadDirectory(path)
            },
            onError = { throwable ->
                activeConnectionId = null
                connectionAdapter.setActiveId(null)
                setStatusDot(R.color.color_error)
                setStatus(R.string.status_error)
                showState(
                    iconRes = R.drawable.ic_cloud_off,
                    titleRes = R.string.empty_error_title,
                    message = ErrorTexts.message(this, throwable),
                    actionRes = R.string.action_retry
                ) { connectTo(connection) }
            }
        )
    }

    private fun disconnectActive() {
        connectionManager.disconnect()
        activeConnectionId = null
        allFiles = emptyList()
        fileAdapter.submitList(emptyList())
        publishConnections()
        setStatusDot(R.color.color_outline)
        setStatus(R.string.status_disconnected)
        binding.tvCount.text = ""
        showDisconnectedState()
    }

    private fun showConnectionActions(connection: RemoteConnection) {
        val isActive = connection.id == activeConnectionId && connectionManager.isConnected
        val labels = arrayOf(
            getString(if (isActive) R.string.action_disconnect else R.string.action_connect),
            getString(R.string.action_edit),
            getString(R.string.action_delete_connection)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(connection.name)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> if (isActive) disconnectActive() else connectTo(connection)
                    1 -> showEditConnectionDialog(connection)
                    2 -> confirmDeleteConnection(connection)
                }
            }
            .show()
    }

    private fun showNewConnectionDialog() {
        ConnectionEditorDialog(
            activity = this,
            existing = null,
            existingNames = connections.map { it.name }.toSet(),
            onSave = { created ->
                connections.add(created)
                connectionStore.saveAll(connections)
                publishConnections()
                binding.root.showSnack(getString(R.string.msg_connection_saved))
                connectTo(created)
            }
        ).show()
    }

    private fun showEditConnectionDialog(connection: RemoteConnection) {
        ConnectionEditorDialog(
            activity = this,
            existing = connection,
            existingNames = connections.map { it.name }.toSet(),
            onSave = { updated ->
                val index = connections.indexOfFirst { it.id == updated.id }
                if (index >= 0) {
                    connections[index] = updated
                } else {
                    connections.add(updated)
                }
                connectionStore.saveAll(connections)
                publishConnections()
                binding.root.showSnack(getString(R.string.msg_connection_saved))
                // 若改的是当前连接，重连以应用新配置
                if (updated.id == activeConnectionId) connectTo(updated)
            }
        ).show()
    }

    private fun confirmDeleteConnection(connection: RemoteConnection) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_connection_title)
            .setMessage(getString(R.string.dialog_delete_connection_message, connection.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (connection.id == activeConnectionId) disconnectActive()
                connections.removeAll { it.id == connection.id }
                connectionStore.saveAll(connections)
                publishConnections()
                if (settings.lastConnectionId == connection.id) settings.lastConnectionId = null
                binding.root.showSnack(getString(R.string.msg_connection_deleted, connection.name))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // ---------- 目录浏览 ----------

    private fun loadDirectory(path: String) {
        if (!connectionManager.isConnected) {
            showDisconnectedState()
            return
        }
        setStatus(getString(R.string.status_loading, path))
        runRemote(
            task = { connectionManager.list(path) },
            onSuccess = { files ->
                allFiles = files
                filterQuery = binding.etSearch.text?.toString()?.trim().orEmpty()
                renderFiles()
                val connection = connectionManager.activeConnection
                if (connection != null) {
                    setStatusDot(R.color.color_success)
                    setStatus(
                        getString(R.string.status_connected) + " · " +
                            getString(connection.type.labelRes) + " · " + connection.endpoint
                    )
                }
            },
            onError = { throwable ->
                setStatusDot(R.color.color_error)
                setStatus(R.string.status_error)
                showState(
                    iconRes = R.drawable.ic_error,
                    titleRes = R.string.empty_error_title,
                    message = ErrorTexts.message(this, throwable),
                    actionRes = R.string.action_retry
                ) { loadDirectory(path) }
            }
        )
    }

    private fun refreshCurrent() {
        if (!connectionManager.isConnected) {
            showDisconnectedState()
            return
        }
        loadDirectory(connectionManager.currentDirectory)
    }

    private fun navigateHome() {
        if (!connectionManager.isConnected) {
            binding.root.showSnack(getString(R.string.msg_pick_connection_first))
            return
        }
        loadDirectory(RemotePath.ROOT)
    }

    private fun navigateUp() {
        if (!connectionManager.isConnected) return
        if (RemotePath.isRoot(connectionManager.currentDirectory)) return
        runRemote(
            task = { connectionManager.goUp() },
            onSuccess = { path -> loadDirectory(path) },
            onError = { binding.root.showSnack(ErrorTexts.message(this, it)) }
        )
    }

    private fun onFileClicked(file: FileInfo) {
        when {
            file.isParentLink -> navigateUp()
            file.isDirectory -> enterDirectory(file)
            else -> startDownload(file)
        }
    }

    private fun enterDirectory(file: FileInfo) {
        runRemote(
            task = { connectionManager.enter(file.name) },
            onSuccess = { path -> loadDirectory(path) },
            onError = { binding.root.showSnack(ErrorTexts.message(this, it)) }
        )
    }

    private fun renderFiles() {
        val filtered = if (filterQuery.isBlank()) {
            allFiles
        } else {
            allFiles.filter { it.name.contains(filterQuery, ignoreCase = true) }
        }
        val sorted = FileSorter.sort(filtered, settings.sortField, settings.sortAscending)

        val display = ArrayList<FileInfo>(sorted.size + 1)
        if (!RemotePath.isRoot(connectionManager.currentDirectory)) display.add(FileInfo.parentLink())
        display.addAll(sorted)
        fileAdapter.submitList(display)

        binding.tvPath.text = connectionManager.currentDirectory
        binding.tvCount.text = getString(R.string.status_loaded_items, sorted.size)

        when {
            filtered.isEmpty() && filterQuery.isNotBlank() -> showState(
                iconRes = R.drawable.ic_search,
                titleRes = R.string.empty_search_title,
                messageRes = R.string.empty_search_message
            )
            filtered.isEmpty() -> showState(
                iconRes = R.drawable.ic_folder_open,
                titleRes = R.string.empty_dir_title,
                messageRes = R.string.empty_dir_message
            )
            else -> showContent()
        }
    }

    /** 编辑当前路径：直接输入绝对路径跳转 */
    private fun editPath() {
        if (!connectionManager.isConnected) {
            binding.root.showSnack(getString(R.string.msg_pick_connection_first))
            return
        }
        showTextInputDialog(
            titleRes = R.string.field_path,
            hintRes = R.string.path_hint,
            initial = connectionManager.currentDirectory,
            positiveRes = R.string.action_go,
            validate = { value ->
                if (!value.trim().startsWith(RemotePath.ROOT)) getString(R.string.error_path_invalid) else null
            },
            onConfirm = { value -> loadDirectory(RemotePath.normalize(value)) }
        )
    }

    // ---------- 文件操作 ----------

    private fun startDownload(file: FileInfo) {
        pendingDownload = file
        val suggested = RemotePath.sanitizeFileName(file.name)
        runCatching { createDocumentLauncher.launch(suggested) }
            .onFailure { binding.root.showSnack(getString(R.string.msg_storage_unavailable)) }
    }

    private fun pickFileToUpload() {
        if (!connectionManager.isConnected) {
            binding.root.showSnack(getString(R.string.msg_pick_connection_first))
            return
        }
        runCatching { openDocumentLauncher.launch(arrayOf("*/*")) }
            .onFailure { binding.root.showSnack(getString(R.string.msg_no_file_picker)) }
    }

    private fun upload(uri: Uri, name: String) {
        val baseDir = connectionManager.currentDirectory
        runRemote(
            task = {
                val input = contentResolver.openInputStream(uri)
                    ?: throw RemoteException(RemoteException.Reason.INVALID_INPUT, "无法读取所选文件")
                input.use { connectionManager.upload(name, it, baseDir) }
            },
            onSuccess = {
                binding.root.showSnack(getString(R.string.msg_upload_success, name))
                loadDirectory(baseDir)
            },
            onError = { throwable ->
                binding.root.showSnack(
                    getString(R.string.msg_upload_failed) + " · " + ErrorTexts.message(this, throwable)
                )
            }
        )
    }

    private fun showFileActions(file: FileInfo) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        if (!file.isDirectory) {
            labels.add(getString(R.string.action_download))
            actions.add { startDownload(file) }
        }
        labels.add(getString(R.string.action_rename))
        actions.add { showRenameDialog(file) }
        labels.add(getString(R.string.action_copy))
        actions.add { showTransferDialog(file, move = false) }
        labels.add(getString(R.string.action_move))
        actions.add { showTransferDialog(file, move = true) }
        labels.add(getString(R.string.action_delete))
        actions.add { confirmDelete(file) }

        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setItems(labels.toTypedArray()) { _, which -> actions.getOrNull(which)?.invoke() }
            .show()
    }

    private fun showRenameDialog(file: FileInfo) {
        showTextInputDialog(
            titleRes = R.string.dialog_rename_title,
            hintRes = R.string.field_new_name,
            initial = file.name,
            positiveRes = R.string.action_save,
            validate = { value ->
                when {
                    value.trim().isEmpty() -> getString(R.string.error_name_required_short)
                    value.trim() == file.name -> getString(R.string.error_same_name)
                    RemotePath.validateName(value) != null -> getString(R.string.error_invalid_name_chars)
                    else -> null
                }
            },
            onConfirm = { value ->
                val baseDir = connectionManager.currentDirectory
                runRemote(
                    task = { connectionManager.rename(file, value.trim(), baseDir) },
                    onSuccess = {
                        binding.root.showSnack(getString(R.string.msg_rename_success, value.trim()))
                        loadDirectory(baseDir)
                    },
                    onError = { binding.root.showSnack(ErrorTexts.message(this, it)) }
                )
            }
        )
    }

    private fun showTransferDialog(file: FileInfo, move: Boolean) {
        val defaultTarget = connectionManager.currentDirectory
        showTextInputDialog(
            titleRes = if (move) R.string.dialog_move_title else R.string.dialog_copy_title,
            titleArg = file.name,
            hintRes = R.string.field_target_path,
            initial = defaultTarget,
            positiveRes = R.string.action_ok,
            validate = { value ->
                val normalized = RemotePath.normalize(value)
                when {
                    value.trim().isEmpty() -> getString(R.string.error_path_required)
                    !value.trim().startsWith(RemotePath.ROOT) -> getString(R.string.error_path_invalid)
                    normalized == RemotePath.child(connectionManager.currentDirectory, file.name) ->
                        getString(R.string.error_path_same)
                    else -> null
                }
            },
            onConfirm = { value -> copyOrMove(file, move, RemotePath.normalize(value)) }
        )
    }

    private fun copyOrMove(file: FileInfo, move: Boolean, targetDir: String, overwrite: Boolean = false) {
        val baseDir = connectionManager.currentDirectory
        runRemote(
            task = {
                if (move) connectionManager.move(file, targetDir, overwrite, baseDir)
                else connectionManager.copy(file, targetDir, overwrite, baseDir)
            },
            onSuccess = {
                binding.root.showSnack(
                    getString(if (move) R.string.msg_move_success else R.string.msg_copy_success, file.name)
                )
                if (targetDir == connectionManager.currentDirectory) {
                    loadDirectory(connectionManager.currentDirectory)
                }
            },
            onError = { throwable ->
                val remote = ErrorMapper.toRemote(throwable)
                if (remote.reason == RemoteException.Reason.ALREADY_EXISTS && !overwrite) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_overwrite_title)
                        .setMessage(getString(R.string.dialog_overwrite_message, file.name))
                        .setPositiveButton(R.string.action_overwrite) { _, _ ->
                            copyOrMove(file, move, targetDir, overwrite = true)
                        }
                        .setNegativeButton(R.string.action_cancel, null)
                        .show()
                } else {
                    binding.root.showSnack(ErrorTexts.message(this, throwable))
                }
            }
        )
    }

    private fun confirmDelete(file: FileInfo) {
        val message = if (file.isDirectory) {
            getString(R.string.dialog_delete_dir_message, file.name)
        } else {
            getString(R.string.dialog_delete_message, file.name)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val baseDir = connectionManager.currentDirectory
                runRemote(
                    task = { connectionManager.delete(file, baseDir) },
                    onSuccess = {
                        binding.root.showSnack(getString(R.string.msg_delete_success, file.name))
                        loadDirectory(baseDir)
                    },
                    onError = { binding.root.showSnack(ErrorTexts.message(this, it)) }
                )
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // ---------- 搜索与排序 ----------

    private fun toggleSearch() {
        val show = !binding.tilSearch.isVisible
        if (show) {
            // 未连接时不做筛选，避免用户误以为「搜索不到文件」
            if (!connectionManager.isConnected) {
                binding.root.showSnack(getString(R.string.msg_pick_connection_first))
                return
            }
            binding.tilSearch.showIf(true)
            binding.etSearch.requestFocus()
            showKeyboard(binding.etSearch)
        } else {
            binding.etSearch.setText("")
            filterQuery = ""
            binding.tilSearch.showIf(false)
            hideKeyboard()
            renderFiles()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) return true
        return when (item.itemId) {
            R.id.action_search -> {
                toggleSearch()
                true
            }
            R.id.sort_name -> updateSortField(SortField.NAME)
            R.id.sort_size -> updateSortField(SortField.SIZE)
            R.id.sort_date -> updateSortField(SortField.DATE)
            R.id.sort_direction -> {
                settings.sortAscending = !settings.sortAscending
                syncSortMenu()
                renderFiles()
                true
            }
            R.id.action_refresh_menu -> {
                refreshCurrent()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateSortField(field: SortField): Boolean {
        settings.sortField = field
        syncSortMenu()
        renderFiles()
        return true
    }

    private fun syncSortMenu() {
        val menu = binding.toolbar.menu
        val itemId = when (settings.sortField) {
            SortField.NAME -> R.id.sort_name
            SortField.SIZE -> R.id.sort_size
            SortField.DATE -> R.id.sort_date
        }
        menu.findItem(itemId)?.isChecked = true
        menu.findItem(R.id.sort_direction)?.setTitle(
            if (settings.sortAscending) R.string.sort_ascending else R.string.sort_descending
        )
    }

    // ---------- 外观与关于 ----------

    private fun showThemeDialog() {
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val labels = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val current = modes.indexOf(settings.themeMode).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                settings.themeMode = modes[which]
                dialog.dismiss()
                AppCompatDelegate.setDefaultNightMode(modes[which])
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAboutDialog() {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_about_title)
            .setMessage(getString(R.string.dialog_about_message, version))
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    // ---------- 通用 UI 状态 ----------

    private fun showContent() {
        binding.stateContainer.showIf(false)
        binding.recyclerFiles.showIf(true)
        binding.swipeRefresh.showIf(true)
    }

    private fun showDisconnectedState() {
        setBusy(false)
        showState(
            iconRes = R.drawable.ic_link,
            titleRes = R.string.empty_disconnected_title,
            messageRes = R.string.empty_disconnected_message,
            actionRes = R.string.action_new_connection
        ) { showNewConnectionDialog() }
    }

    private fun showState(
        iconRes: Int,
        titleRes: Int,
        messageRes: Int = 0,
        message: String? = null,
        actionRes: Int = 0,
        action: (() -> Unit)? = null
    ) {
        binding.recyclerFiles.showIf(false)
        binding.swipeRefresh.showIf(false)
        binding.stateContainer.showIf(true)
        binding.ivStateIcon.setImageResource(iconRes)
        binding.tvStateTitle.setText(titleRes)
        binding.tvStateMessage.text = message ?: if (messageRes != 0) getString(messageRes) else ""
        binding.tvStateMessage.showIf(binding.tvStateMessage.text.isNotEmpty())
        if (actionRes != 0 && action != null) {
            binding.btnStateAction.showIf(true)
            binding.btnStateAction.setText(actionRes)
            binding.btnStateAction.setOnClickListener { action() }
        } else {
            binding.btnStateAction.showIf(false)
            binding.btnStateAction.setOnClickListener(null)
        }
    }

    private fun setStatus(statusRes: Int) {
        binding.tvStatus.setText(statusRes)
    }

    private fun setStatus(text: CharSequence) {
        binding.tvStatus.text = text
    }

    private fun setStatusDot(colorRes: Int) {
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.showIf(value)
        binding.swipeRefresh.isRefreshing = false
        binding.fabUpload.isEnabled = !value
        binding.btnStateAction.isEnabled = !value
    }

    /** 统一的输入对话框：重命名 / 新建文件夹 / 目标路径 */
    private fun showTextInputDialog(
        titleRes: Int,
        titleArg: String? = null,
        hintRes: Int,
        initial: String,
        positiveRes: Int,
        validate: (String) -> String?,
        onConfirm: (String) -> Unit
    ) {
        val dialogBinding = DialogTextInputBinding.inflate(layoutInflater)
        dialogBinding.tilInput.setHint(hintRes)
        dialogBinding.etInput.setText(initial)
        dialogBinding.etInput.setSelection(initial.length)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(titleArg?.let { getString(titleRes, it) } ?: getString(titleRes))
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(positiveRes, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val value = dialogBinding.etInput.text?.toString().orEmpty()
                val error = validate(value)
                if (error != null) {
                    dialogBinding.tilInput.error = error
                } else {
                    dialogBinding.tilInput.error = null
                    dialog.dismiss()
                    onConfirm(value)
                }
            }
            dialogBinding.etInput.requestFocus()
            showKeyboard(dialogBinding.etInput)
        }
        dialog.show()
    }

    // ---------- 基础设施 ----------

    private fun <T> runRemote(
        task: () -> T,
        onSuccess: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        pendingTasks.incrementAndGet()
        setBusy(true)
        remoteExecutor.execute {
            val outcome = runCatching(task)
            mainHandler.post {
                if (!alive()) return@post
                // 只有最后一个任务结束才收起进度条，避免连续操作时指示器闪烁
                if (pendingTasks.decrementAndGet() <= 0) setBusy(false)
                outcome.fold(
                    onSuccess = { value -> onSuccess(value) },
                    onFailure = { error -> (onError ?: { binding.root.showSnack(ErrorTexts.message(this, it)) })(error) }
                )
            }
        }
    }

    private fun alive(): Boolean = !isFinishing && !isDestroyed

    private fun resolveDisplayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return RemotePath.sanitizeFileName(cursor.getString(index))
                }
            }
        }
        return RemotePath.sanitizeFileName(uri.lastPathSegment)
    }

    private fun showKeyboard(view: View) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        manager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        manager.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private companion object {
        const val STATE_CONNECTION_ID = "state_connection_id"
        const val STATE_PATH = "state_path"
    }
}
