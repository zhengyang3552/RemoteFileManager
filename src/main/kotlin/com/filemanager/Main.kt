package com.filemanager

import com.filemanager.manager.ConnectionManager
import com.filemanager.model.*
import com.filemanager.manager.FileSystemManagerFactory
import javax.swing.*
import java.awt.*
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.*
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter
import java.time.LocalDateTime

fun main() {
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {}

        val app = RemoteFileManagerApp()
        app.createAndShowGUI()
    }
}

class RemoteFileManagerApp {
    private lateinit var connectionManager: ConnectionManager
    private lateinit var frame: JFrame
    private lateinit var connectionList: JList<RemoteConnection>
    private lateinit var connectionListModel: DefaultListModel<RemoteConnection>
    private lateinit var fileTable: JTable
    private lateinit var fileTableModel: DefaultTableModel
    private lateinit var statusBar: JLabel
    private lateinit var pathField: JTextField
    private var selectedConnection: RemoteConnection? = null
    private var localDownloadDir: String = System.getProperty("user.home") + "/Downloads/RemoteFiles"

    fun createAndShowGUI() {
        frame = JFrame("远程网络文件管理器 v1.0")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(1200, 800)

        connectionManager = ConnectionManager()

        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(createMenuBar(), BorderLayout.NORTH)

        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(createConnectionPanel(), BorderLayout.WEST)
        centerPanel.add(createFilePanel(), BorderLayout.CENTER)
        mainPanel.add(centerPanel, BorderLayout.CENTER)

        statusBar = JLabel("就绪")
        statusBar.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
        mainPanel.add(statusBar, BorderLayout.SOUTH)

        frame.contentPane.add(mainPanel)
        frame.setLocationRelativeTo(null)
        frame.visible = true
    }

    private fun createMenuBar(): JMenuBar {
        val menuBar = JMenuBar()

        val fileMenu = JMenu("文件")
        fileMenu.add(createNewConnectionAction())
        fileMenu.addSeparator()

        val refreshItem = JMenuItem("刷新列表")
        refreshItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0)
        refreshItem.addActionListener { refreshCurrentDirectory() }
        fileMenu.add(refreshItem)

        val exitItem = JMenuItem("退出")
        exitItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK)
        exitItem.addActionListener {
            connectionManager.closeAll()
            frame.dispose()
        }
        fileMenu.add(exitItem)
        menuBar.add(fileMenu)

        val editMenu = JMenu("编辑")
        val copyItem = JMenuItem("复制文件")
        copyItem.addActionListener { copySelectedFile() }
        editMenu.add(copyItem)

        val moveItem = JMenuItem("移动文件")
        moveItem.addActionListener { moveSelectedFile() }
        editMenu.add(moveItem)
        menuBar.add(editMenu)

        val transferMenu = JMenu("传输")
        val downloadItem = JMenuItem("下载文件")
        downloadItem.addActionListener { downloadSelectedFile() }
        transferMenu.add(downloadItem)

        val uploadItem = JMenuItem("上传文件")
        uploadItem.addActionListener { uploadFile() }
        transferMenu.add(uploadItem)
        menuBar.add(transferMenu)

        val helpMenu = JMenu("帮助")
        val aboutItem = JMenuItem("关于")
        aboutItem.addActionListener { showAboutDialog() }
        helpMenu.add(aboutItem)
        menuBar.add(helpMenu)

        return menuBar
    }

    private fun createNewConnectionAction(): JMenuItem {
        val item = JMenuItem("新建连接...")
        item.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK)
        item.addActionListener { showNewConnectionDialog() }
        return item
    }

    private fun createConnectionPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(280, 0)

        val panelTitle = JLabel("连接列表")
        panelTitle.font = Font("sans-serif", Font.BOLD, 14)
        panelTitle.border = BorderFactory.createEmptyBorder(10, 10, 5, 10)
        panel.add(panelTitle, BorderLayout.NORTH)

        connectionListModel = DefaultListModel()
        connectionList = JList(connectionListModel)
        connectionList.cellRenderer = ConnectionListCellRenderer()

        connectionList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = connectionList.selectedValue
                if (selected != null) selectConnection(selected)
            }
        }

        val popupMenu = JPopupMenu()
        val connectItem = JMenuItem("连接/断开")
        connectItem.addActionListener {
            val selected = connectionList.selectedValue
            if (selected != null) {
                if (selected.isConnected) {
                    connectionManager.disconnect(selected)
                } else {
                    connectToServer(selected)
                }
            }
        }
        popupMenu.add(connectItem)
        popupMenu.addSeparator()

        val editItem = JMenuItem("编辑连接")
        editItem.addActionListener {
            val selected = connectionList.selectedValue
            if (selected != null) showEditConnectionDialog(selected)
        }
        popupMenu.add(editItem)

        val deleteItem = JMenuItem("删除连接")
        deleteItem.addActionListener {
            val selected = connectionList.selectedValue
            if (selected != null) {
                connectionManager.removeConnection(selected.name)
                refreshConnectionList()
            }
        }
        popupMenu.add(deleteItem)

        val testItem = JMenuItem("测试连接")
        testItem.addActionListener {
            val selected = connectionList.selectedValue
            if (selected != null) connectToServer(selected)
        }
        popupMenu.add(testItem)
        connectionList.componentPopupMenu = popupMenu

        val scrollPane = JScrollPane(connectionList)
        scrollPane.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        panel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER))
        val addBtn = JButton("+ 添加连接")
        addBtn.addActionListener { showNewConnectionDialog() }
        buttonPanel.add(addBtn)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createFilePanel(): JPanel {
        val panel = JPanel(BorderLayout())

        val pathPanel = JPanel(BorderLayout())
        pathPanel.border = BorderFactory.createEmptyBorder(10, 10, 5, 10)

        val goButton = JButton("前往")
        goButton.addActionListener { navigateToPath() }

        val parentButton = JButton("上一级")
        parentButton.addActionListener { navigateParent() }

        val homeButton = JButton("主页")
        homeButton.addActionListener { navigateHome() }

        pathField = JTextField()
        pathField.addActionListener { navigateToPath() }

        pathPanel.add(parentButton, BorderLayout.WEST)
        pathPanel.add(goButton, BorderLayout.EAST)
        pathPanel.add(homeButton, BorderLayout.CENTER)
        pathPanel.add(pathField, BorderLayout.NORTH)

        panel.add(pathPanel, BorderLayout.NORTH)

        fileTableModel = DefaultTableModel(
            arrayOf("名称", "类型", "大小", "修改时间"),
            0
        )
        fileTable = JTable(fileTableModel)
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        fileTable.selectionModel.addListSelectionListener { updateSelectedFileInfo() }

        val sorter = TableRowSorter(fileTableModel)
        fileTable.sorter = sorter

        fileTable.componentPopupMenu = createFilePopupMenu()
        fileTable.addMouseListener { e ->
            if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
                doubleClickFile()
            }
        }

        val fileScrollPane = JScrollPane(fileTable)
        fileScrollPane.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        panel.add(fileScrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun createFilePopupMenu(): JPopupMenu {
        val menu = JPopupMenu()

        val openItem = JMenuItem("打开/进入")
        openItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        openItem.addActionListener { doubleClickFile() }
        menu.add(openItem)
        menu.addSeparator()

        val downloadItem = JMenuItem("下载")
        downloadItem.addActionListener { downloadSelectedFile() }
        menu.add(downloadItem)

        val renameItem = JMenuItem("重命名")
        renameItem.addActionListener { renameSelectedFile() }
        menu.add(renameItem)

        val deleteItem = JMenuItem("删除")
        deleteItem.accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)
        deleteItem.addActionListener { deleteSelectedFile() }
        menu.add(deleteItem)

        return menu
    }

    // ==================== 连接管理 ====================

    private fun refreshConnectionList() {
        connectionListModel.clear()
        connectionManager.getConnections().forEach { conn ->
            connectionListModel.addElement(conn)
        }
        if (selectedConnection != null) {
            val index = connectionManager.getConnections().indexOf(selectedConnection)
            if (index >= 0) connectionList.selectedIndex = index
        }
    }

    private fun selectConnection(connection: RemoteConnection) {
        selectedConnection = connection

        if (connection.isConnected) {
            statusBar.text = "已连接: ${connection.name} - ${connection.host}"
            loadDirectory(connection.getCurrentDirectory())
        } else {
            statusBar.text = "未连接: ${connection.name}"
            fileTableModel.rowCount = 0
        }
    }

    private fun showNewConnectionDialog() {
        val dialog = JDialog(frame, "新建连接", true)
        dialog.preferredSize = Dimension(500, 500)
        dialog.layout = BorderLayout()

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)

        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
        panel.add(JLabel("连接名称:"), gbc)
        gbc.gridx = 1
        val nameField = JTextField("My Connection", 20)
        panel.add(nameField, gbc)

        gbc.gridx = 0; gbc.gridy = 1
        panel.add(JLabel("协议类型:"), gbc)
        gbc.gridx = 1
        val protocolCombo = JComboBox(FileSystemManagerFactory.getAllProtocols().toTypedArray())
        panel.add(protocolCombo, gbc)

        gbc.gridx = 0; gbc.gridy = 2
        panel.add(JLabel("主机地址:"), gbc)
        gbc.gridx = 1
        val hostField = JTextField("localhost", 20)
        panel.add(hostField, gbc)

        gbc.gridx = 0; gbc.gridy = 3
        panel.add(JLabel("端口:"), gbc)
        gbc.gridx = 1
        val portField = JTextField(
            FileSystemManagerFactory.getDefaultPort(protocolCombo.selectedItem as ProtocolType).toString(), 5
        )
        panel.add(portField, gbc)

        protocolCombo.addActionListener {
            val protocol = protocolCombo.selectedItem as ProtocolType
            portField.text = FileSystemManagerFactory.getDefaultPort(protocol).toString()
        }

        gbc.gridx = 0; gbc.gridy = 4
        panel.add(JLabel("用户名:"), gbc)
        gbc.gridx = 1
        val usernameField = JTextField("anonymous", 20)
        panel.add(usernameField, gbc)

        gbc.gridx = 0; gbc.gridy = 5
        panel.add(JLabel("密码:"), gbc)
        gbc.gridx = 1
        val passwordField = JPasswordField(20)
        panel.add(passwordField, gbc)

        gbc.gridx = 0; gbc.gridy = 6
        panel.add(JLabel("初始路径:"), gbc)
        gbc.gridx = 1
        val pathField2 = JTextField("/", 20)
        panel.add(pathField2, gbc)

        gbc.gridx = 0; gbc.gridy = 7
        val sslCheck = JCheckBox("使用SSL (FTPS)")
        panel.add(sslCheck, gbc)

        gbc.gridx = 0; gbc.gridy = 8
        val activeModeCheck = JCheckBox("主动模式 (Active Mode)")
        panel.add(activeModeCheck, gbc)

        gbc.gridx = 0; gbc.gridy = 9; gbc.gridwidth = 2
        gbc.anchor = GridBagConstraints.CENTER
        val buttonPanel = JPanel(FlowLayout())
        val connectBtn = JButton("连接")
        val cancelBtn = JButton("取消")
        buttonPanel.add(connectBtn)
        buttonPanel.add(cancelBtn)
        panel.add(buttonPanel, gbc)

        connectBtn.addActionListener {
            val name = nameField.text.trim()
            if (name.isBlank()) {
                JOptionPane.showMessageDialog(dialog, "请输入连接名称", "错误", JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }

            val protocol = protocolCombo.selectedItem as ProtocolType
            val host = hostField.text.trim()
            val port = portField.text.toIntOrNull() ?: 0
            val username = usernameField.text.trim()
            val password = String(passwordField.password)
            val path = pathField2.text.trim().ifEmpty { "/" }

            if (host.isBlank()) {
                JOptionPane.showMessageDialog(dialog, "请输入主机地址", "错误", JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }

            val connection = RemoteConnection(
                name = name, type = protocol, host = host, port = port,
                username = username, password = password, path = path,
                useSsl = sslCheck.isSelected, activeMode = activeModeCheck.isSelected
            )

            connectionManager.addConnection(connection)
            refreshConnectionList()

            if (connectToServer(connection)) {
                dialog.dispose()
            } else {
                JOptionPane.showMessageDialog(dialog,
                    "连接失败: ${connection.errorMessage}",
                    "连接失败", JOptionPane.ERROR_MESSAGE)
            }
        }

        cancelBtn.addActionListener { dialog.dispose() }

        dialog.contentPane.add(panel)
        dialog.pack()
        dialog.setLocationRelativeTo(frame)
        dialog.visible = true
    }

    private fun showEditConnectionDialog(connection: RemoteConnection) {
        val dialog = JDialog(frame, "编辑连接", true)
        dialog.preferredSize = Dimension(500, 500)
        dialog.layout = BorderLayout()

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)

        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST
        panel.add(JLabel("连接名称:"), gbc)
        gbc.gridx = 1
        val nameField = JTextField(connection.name, 20)
        panel.add(nameField, gbc)

        gbc.gridx = 0; gbc.gridy = 1
        panel.add(JLabel("协议类型:"), gbc)
        gbc.gridx = 1
        val protocolCombo = JComboBox(FileSystemManagerFactory.getAllProtocols().toTypedArray())
        protocolCombo.selectedItem = connection.type
        panel.add(protocolCombo, gbc)

        gbc.gridx = 0; gbc.gridy = 2
        panel.add(JLabel("主机地址:"), gbc)
        gbc.gridx = 1
        val hostField = JTextField(connection.host, 20)
        panel.add(hostField, gbc)

        gbc.gridx = 0; gbc.gridy = 3
        panel.add(JLabel("端口:"), gbc)
        gbc.gridx = 1
        val portField = JTextField(connection.port.toString(), 5)
        panel.add(portField, gbc)

        protocolCombo.addActionListener {
            val protocol = protocolCombo.selectedItem as ProtocolType
            portField.text = FileSystemManagerFactory.getDefaultPort(protocol).toString()
        }

        gbc.gridx = 0; gbc.gridy = 4
        panel.add(JLabel("用户名:"), gbc)
        gbc.gridx = 1
        val usernameField = JTextField(connection.username, 20)
        panel.add(usernameField, gbc)

        gbc.gridx = 0; gbc.gridy = 5
        panel.add(JLabel("密码:"), gbc)
        gbc.gridx = 1
        val passwordField = JPasswordField(connection.password, 20)
        panel.add(passwordField, gbc)

        gbc.gridx = 0; gbc.gridy = 6
        panel.add(JLabel("初始路径:"), gbc)
        gbc.gridx = 1
        val pathField2 = JTextField(connection.path, 20)
        panel.add(pathField2, gbc)

        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2
        gbc.anchor = GridBagConstraints.CENTER
        val buttonPanel = JPanel(FlowLayout())
        val saveBtn = JButton("保存")
        val cancelBtn = JButton("取消")
        buttonPanel.add(saveBtn)
        buttonPanel.add(cancelBtn)
        panel.add(buttonPanel, gbc)

        saveBtn.addActionListener {
            connection.name = nameField.text.trim()
            connection.type = protocolCombo.selectedItem as ProtocolType
            connection.host = hostField.text.trim()
            connection.port = portField.text.toIntOrNull() ?: 0
            connection.username = usernameField.text.trim()
            connection.password = String(passwordField.password)
            connection.path = pathField2.text.trim()

            refreshConnectionList()
            dialog.dispose()
        }

        cancelBtn.addActionListener { dialog.dispose() }

        dialog.contentPane.add(panel)
        dialog.pack()
        dialog.setLocationRelativeTo(frame)
        dialog.visible = true
    }

    private fun connectToServer(connection: RemoteConnection): Boolean {
        val success = connectionManager.connect(connection)

        if (success) {
            statusBar.text = "已连接: ${connection.name} - ${connection.host}"
            SwingUtilities.invokeLater { loadDirectory(connection.getCurrentDirectory()) }
        } else {
            statusBar.text = "连接失败: ${connection.errorMessage}"
            JOptionPane.showMessageDialog(frame,
                "连接失败: ${connection.errorMessage}",
                "连接错误", JOptionPane.ERROR_MESSAGE)
        }
        return success
    }

    // ==================== 文件操作 ====================

    private fun loadDirectory(path: String) {
        val connection = selectedConnection ?: return

        try {
            statusBar.text = "正在加载: $path"
            val files = connectionManager.listFiles(connection, path)

            SwingUtilities.invokeLater {
                fileTableModel.rowCount = 0

                if (path != "/") {
                    fileTableModel.addRow(arrayOf("..", "<DIR>", "-", "-"))
                }

                for (file in files) {
                    val modifiedStr = file.lastModified?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: "-"
                    fileTableModel.addRow(
                        arrayOf(file.name, if (file.isDirectory) "<DIR>" else file.formattedSize, file.formattedSize, modifiedStr)
                    )
                }

                pathField.text = path
            }

            statusBar.text = "已加载 ${files.size} 个项目: $path"
        } catch (e: Exception) {
            statusBar.text = "加载失败: ${e.message}"
            e.printStackTrace()
        }
    }

    private fun navigateToPath() {
        val path = pathField.text.trim()
        if (path.isNotEmpty()) loadDirectory(path)
    }

    private fun navigateParent() {
        val connection = selectedConnection ?: return
        val currentPath = connectionManager.getCurrentDirectory(connection)
        if (currentPath == "/") return
        val parentPath = currentPath.substringBeforeLast("/").ifEmpty { "/" }
        loadDirectory(parentPath)
    }

    private fun navigateHome() {
        val connection = selectedConnection ?: return
        loadDirectory("/")
    }

    private fun refreshCurrentDirectory() {
        val connection = selectedConnection ?: return
        val currentDir = connectionManager.getCurrentDirectory(connection)
        loadDirectory(currentDir)
    }

    private fun doubleClickFile() {
        val selectedRow = fileTable.selectedRow
        if (selectedRow < 0) return

        val fileName = fileTableModel.getValueAt(selectedRow, 0) as String
        if (fileName == "..") { navigateParent(); return }

        val connection = selectedConnection ?: return
        val currentPath = connectionManager.getCurrentDirectory(connection)
        val fullPath = if (currentPath == "/") "/$fileName" else "$currentPath/$fileName"

        val files = connectionManager.listFiles(connection, currentPath)
        val file = files.find { it.name == fileName }

        if (file?.isDirectory == true) {
            if (connectionManager.changeDirectory(connection, fullPath)) {
                loadDirectory(fullPath)
            } else {
                JOptionPane.showMessageDialog(frame, "无法进入目录: $fileName", "错误", JOptionPane.ERROR_MESSAGE)
            }
        } else {
            downloadFileAtPath(fullPath)
        }
    }

    private fun updateSelectedFileInfo() {
        val selectedRow = fileTable.selectedRow
        if (selectedRow < 0) {
            statusBar.text = "就绪"
            return
        }
        val fileName = fileTableModel.getValueAt(selectedRow, 0) as String
        val fileType = fileTableModel.getValueAt(selectedRow, 1) as String
        val fileSize = fileTableModel.getValueAt(selectedRow, 2) as String
        statusBar.text = "已选择: $fileName ($fileType, $fileSize)"
    }

    private fun deleteSelectedFile() {
        val selectedRow = fileTable.selectedRow
        if (selectedRow < 0) return
        val fileName = fileTableModel.getValueAt(selectedRow, 0) as String
        if (fileName == "..") return

        val connection = selectedConnection ?: return
        val currentPath = connectionManager.getCurrentDirectory(connection)
        val fullPath = if (currentPath == "/") "/$fileName" else "$currentPath/$fileName"

        val confirm = JOptionPane.showConfirmDialog(
            frame, "确定要删除 \"$fileName\" 吗?", "确认删除", JOptionPane.YES_NO_OPTION)

        if (confirm == JOptionPane.YES_OPTION) {
            val success = connectionManager.delete(connection, fullPath)
            if (success) {
                loadDirectory(currentPath)
                statusBar.text = "已删除: $fileName"
            } else {
                JOptionPane.showMessageDialog(frame, "删除失败", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun renameSelectedFile() {
        val selectedRow = fileTable.selectedRow
        if (selectedRow < 0) return
        val oldName = fileTableModel.getValueAt(selectedRow, 0) as String
        if (oldName == "..") return

        val newName = JOptionPane.showInputDialog(frame, "输入新名称:", oldName)
        if (newName.isNullOrEmpty()) return

        val connection = selectedConnection ?: return
        val currentPath = connectionManager.getCurrentDirectory(connection)
        val fullPath = if (currentPath == "/") "/$oldName" else "$currentPath/$oldName"

        val success = connectionManager.rename(connection, fullPath, newName)
        if (success) {
            loadDirectory(currentPath)
            statusBar.text = "已重命名: $oldName -> $newName"
        } else {
            JOptionPane.showMessageDialog(frame, "重命名失败", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun copySelectedFile() {
        val selectedRow = fileTable.selectedRow
        if (selectedRow < 0) return
        val fileName = fileTableModel.getValueAt(selectedRow, 0) as String
        if (fileName == "..") return

        val connection = selectedConnection ?: return
        val currentPath = connectionManager.getCurrentDirectory(connection)
        val sourcePath = if (currentPath == "/") "/$fileName" else "$currentPath/$fileName"
        val destPath = "$currentPath/copy_of_$fileName"

        val success = connectionManager.copyRemote(connection, sourcePath, destPath)
        if (success) {
            loadDirectory(currentPath)
            statusBar.text = "已复制: $fileName"
        } else {
            JOptionPane.showMessageDialog(frame, "复制失败", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun moveSelectedFile() {
        val selectedRow = fileTable.selectedRow
        if (selectedRow < 0) return
        val fileName = fileTableModel.getValueAt(selectedRow, 0) as String
        if (fileName == "..") return

        val newName = JOptionPane.showInputDialog(frame, "输入新名称:", fileName)
        if (newName.isNullOrEmpty()) return

        val connection = selectedConnection ?: return
        val currentPath = connectionManager.getCurrentDirectory(connection)
        val sourcePath = if (currentPath == "/") "/$fileName" else "$currentPath/$fileName"
        val destPath = if (currentPath == "/") "/$newName" else "$currentPath/$newName"

        val success = connectionManager.moveRemote(connection, sourcePath, destPath)
        if (success) {
            loadDirectory(currentPath)
            statusBar.text = "已移动: $fileName -> $newName"
        } else {
            JOptionPane.showMessageDialog(frame, "移动失败", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun downloadSelectedFile() {
        val selectedRow = fileTable.selectedRow
        if (selectedRow < 0) return
        val fileName = fileTableModel.getValueAt(selectedRow, 0) as String
        if (fileName == "..") return

        val connection = selectedConnection ?: return
        val currentPath = connectionManager.getCurrentDirectory(connection)
        val remotePath = if (currentPath == "/") "/$fileName" else "$currentPath/$fileName"
        downloadFileAtPath(remotePath)
    }

    private fun downloadFileAtPath(remotePath: String) {
        val connection = selectedConnection ?: return

        val fileChooser = JFileChooser()
        fileChooser.selectedFile = File(localDownloadDir, remotePath.substringAfterLast("/"))
        fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
        fileChooser.isMultiSelectionEnabled = false

        val result = fileChooser.showSaveDialog(frame)
        if (result == JFileChooser.APPROVE_OPTION) {
            val localFile = fileChooser.selectedFile
            val success = connectionManager.downloadFile(connection, remotePath, localFile.absolutePath)

            if (success) {
                statusBar.text = "已下载: ${localFile.name}"
                JOptionPane.showMessageDialog(frame, "下载成功: ${localFile.absolutePath}", "下载完成", JOptionPane.INFORMATION_MESSAGE)
            } else {
                JOptionPane.showMessageDialog(frame, "下载失败", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun uploadFile() {
        val connection = selectedConnection ?: return

        val fileChooser = JFileChooser()
        fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
        fileChooser.isMultiSelectionEnabled = false

        val result = fileChooser.showOpenDialog(frame)
        if (result == JFileChooser.APPROVE_OPTION) {
            val localFile = fileChooser.selectedFile
            val currentPath = connectionManager.getCurrentDirectory(connection)
            val remotePath = if (currentPath == "/") "/${localFile.name}" else "$currentPath/${localFile.name}"

            val success = connectionManager.uploadFile(connection, localFile.absolutePath, remotePath)

            if (success) {
                loadDirectory(currentPath)
                statusBar.text = "已上传: ${localFile.name}"
                JOptionPane.showMessageDialog(frame, "上传成功", "上传完成", JOptionPane.INFORMATION_MESSAGE)
            } else {
                JOptionPane.showMessageDialog(frame, "上传失败", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun showAboutDialog() {
        val aboutText = """远程网络文件管理器 v1.0

支持协议:
- FTP (File Transfer Protocol)
- FTPS (FTP over SSL/TLS)
- SFTP (SSH File Transfer Protocol)
- SMB/CIFS (Server Message Block)
- WebDAV (Web Distributed Authoring and Versioning)

功能:
- 连接管理: 添加、编辑、删除连接
- 文件浏览: 查看目录和文件列表
- 文件操作: 上传、下载、删除、重命名、复制、移动
- 多协议支持: 统一界面管理多种远程存储

基于 Kotlin + Swing 开发"""

        JOptionPane.showMessageDialog(frame, aboutText, "关于", JOptionPane.INFORMATION_MESSAGE)
    }

    // ==================== 自定义渲染器 ====================

    inner class ConnectionListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is RemoteConnection) {
                val icon = when {
                    !value.isConnected -> "🔌"
                    value.connectionState == ConnectionState.CONNECTING -> "⏳"
                    value.connectionState == ConnectionState.ERROR -> "❌"
                    else -> "✅"
                }
                val protocolName = FileSystemManagerFactory.getProtocolDisplayName(value.type)
                text = "$icon ${value.name} ($protocolName) - ${value.host}:${value.port}"
                if (value.connectionState == ConnectionState.ERROR) {
                    foreground = Color.RED
                } else if (value.connectionState == ConnectionState.CONNECTING) {
                    foreground = Color.ORANGE
                }
            }
            return component
        }
    }
}
