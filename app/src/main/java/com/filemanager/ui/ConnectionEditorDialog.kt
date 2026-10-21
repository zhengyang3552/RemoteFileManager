package com.filemanager.ui

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.filemanager.R
import com.filemanager.databinding.DialogConnectionBinding
import com.filemanager.manager.ConnectionManager
import com.filemanager.model.ProtocolType
import com.filemanager.model.RemoteConnection
import com.filemanager.util.RemotePath
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 新建 / 编辑连接对话框。
 *
 * 交互与校验要点：
 * - 协议切换时联动默认端口、加密开关与被动模式开关的可见性；
 * - 保存前做「必填 / 端口范围 / 超时范围 / 名称唯一性」四类校验，错误就地展示；
 * - 支持「测试连接」：在后台线程验证凭据，不写入配置，也不影响当前会话；
 * - 对话框关闭时释放线程池，避免 Activity 泄漏。
 */
class ConnectionEditorDialog(
    private val activity: AppCompatActivity,
    private val existing: RemoteConnection?,
    private val existingNames: Set<String>,
    private val onSave: (RemoteConnection) -> Unit
) {

    private val binding = DialogConnectionBinding.inflate(activity.layoutInflater)
    private val protocols = ProtocolType.values()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dialog: AlertDialog? = null

    fun show() {
        setupProtocolDropdown()
        fillExisting()
        setupProtocolDependents()
        setupTestButton()

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) R.string.dialog_new_connection else R.string.dialog_edit_connection)
            .setView(binding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)

        dialog = builder.create().also { alert ->
            alert.setOnShowListener {
                alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (validate()) save(alert) }
            }
            alert.setOnDismissListener { executor.shutdownNow() }
            alert.show()
        }
    }

    private fun setupProtocolDropdown() {
        val labels = protocols.map { activity.getString(it.labelRes) }
        binding.actProtocol.setAdapter(
            ArrayAdapter(activity, android.R.layout.simple_list_item_1, labels)
        )
        binding.actProtocol.setOnItemClickListener { _, _, position, _ ->
            applyProtocolDefaults(protocols[position], resetPort = true)
        }
    }

    private fun fillExisting() {
        val current = existing
        if (current == null) {
            binding.actProtocol.setText(activity.getString(ProtocolType.FTP.labelRes), false)
            binding.etPort.setText(ProtocolType.FTP.defaultPort.toString())
            binding.etPath.setText(RemotePath.ROOT)
            binding.etTimeout.setText((RemoteConnection.DEFAULT_TIMEOUT_MS / 1000).toString())
            binding.swPassive.isChecked = true
        } else {
            binding.actProtocol.setText(activity.getString(current.type.labelRes), false)
            binding.etName.setText(current.name)
            binding.etHost.setText(current.host)
            binding.etPort.setText(current.port.toString())
            binding.etUsername.setText(current.username)
            binding.etPassword.setText(current.password)
            binding.etPath.setText(current.path)
            binding.etTimeout.setText((current.timeoutMs / 1000).toString())
            binding.swSsl.isChecked = current.useSsl
            binding.swPassive.isChecked = current.passiveMode
        }
        // 名称校验提示随输入即时清除
        binding.etName.addTextChangedListener(clearErrorOnEdit(binding.tilName))
        binding.etHost.addTextChangedListener(clearErrorOnEdit(binding.tilHost))
        binding.etPort.addTextChangedListener(clearErrorOnEdit(binding.tilPort))
        binding.etTimeout.addTextChangedListener(clearErrorOnEdit(binding.tilTimeout))
    }

    private fun setupProtocolDependents() {
        val selected = selectedProtocol()
        binding.swSsl.showIf(selected.supportsSslToggle)
        binding.swPassive.showIf(selected.supportsPassiveMode)

        // WebDAV 打开 HTTPS 时，端口在 80/443 之间自动跟随（用户手动改过的不覆盖）
        binding.swSsl.setOnCheckedChangeListener { _, checked ->
            if (selectedProtocol() == ProtocolType.WEBDAV) {
                when {
                    checked && binding.etPort.text?.toString() == "80" -> binding.etPort.setText("443")
                    !checked && binding.etPort.text?.toString() == "443" -> binding.etPort.setText("80")
                }
            }
        }
    }

    private fun applyProtocolDefaults(protocol: ProtocolType, resetPort: Boolean) {
        if (resetPort) binding.etPort.setText(protocol.defaultPort.toString())
        binding.swSsl.showIf(protocol.supportsSslToggle)
        binding.swPassive.showIf(protocol.supportsPassiveMode)
        if (!protocol.supportsSslToggle) binding.swSsl.isChecked = false
        if (protocol == ProtocolType.FTPS) binding.swSsl.isChecked = true
        if (protocol == ProtocolType.WEBDAV) binding.swSsl.isChecked = false
        binding.tilPort.error = null
    }

    private fun setupTestButton() {
        binding.btnTest.setOnClickListener {
            val host = binding.etHost.text?.toString()?.trim().orEmpty()
            if (host.isEmpty()) {
                binding.tilHost.error = activity.getString(R.string.error_host_required)
                return@setOnClickListener
            }
            val candidate = buildConnection() ?: return@setOnClickListener
            setTesting(true)
            executor.execute {
                val result = runCatching { ConnectionManager(activity).test(candidate) }
                mainHandler.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    setTesting(false)
                    if (result.isSuccess) {
                        binding.root.showSnack(activity.getString(R.string.dialog_test_success))
                    } else {
                        binding.root.showSnack(ErrorTexts.message(activity, result.exceptionOrNull()!!))
                    }
                }
            }
        }
    }

    private fun setTesting(testing: Boolean) {
        binding.progressTest.showIf(testing)
        binding.btnTest.isEnabled = !testing
    }

    private fun selectedProtocol(): ProtocolType {
        val text = binding.actProtocol.text?.toString().orEmpty()
        return protocols.firstOrNull { activity.getString(it.labelRes) == text } ?: ProtocolType.FTP
    }

    private fun validate(): Boolean {
        var valid = true

        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val nameError = when {
            name.isEmpty() -> activity.getString(R.string.error_name_required)
            RemotePath.validateName(name) != null -> activity.getString(R.string.error_invalid_name_chars)
            existingNames.any { it.equals(name, ignoreCase = true) && it != existing?.name } ->
                activity.getString(R.string.error_name_duplicate)
            else -> null
        }
        binding.tilName.error = nameError
        if (nameError != null) valid = false

        val host = binding.etHost.text?.toString()?.trim().orEmpty()
        val hostError = when {
            host.isEmpty() -> activity.getString(R.string.error_host_required)
            host.contains(' ') || host.contains('/') -> activity.getString(R.string.error_invalid_input)
            else -> null
        }
        binding.tilHost.error = hostError
        if (hostError != null) valid = false

        val port = binding.etPort.text?.toString()?.trim()?.toIntOrNull()
        val portError = if (port == null || port !in 1..65535) activity.getString(R.string.error_port_invalid) else null
        binding.tilPort.error = portError
        if (portError != null) valid = false

        val timeoutSeconds = binding.etTimeout.text?.toString()?.trim()?.toIntOrNull()
        val timeoutError = if (timeoutSeconds == null || timeoutSeconds !in 5..120) {
            activity.getString(R.string.error_timeout_invalid)
        } else null
        binding.tilTimeout.error = timeoutError
        if (timeoutError != null) valid = false

        return valid
    }

    private fun buildConnection(): RemoteConnection? {
        val protocol = selectedProtocol()
        val host = binding.etHost.text?.toString()?.trim().orEmpty()
        if (host.isEmpty()) return null
        val port = binding.etPort.text?.toString()?.trim()?.toIntOrNull() ?: protocol.defaultPort
        val timeoutSeconds = binding.etTimeout.text?.toString()?.trim()?.toIntOrNull()
            ?: (RemoteConnection.DEFAULT_TIMEOUT_MS / 1000)
        val path = RemotePath.normalize(binding.etPath.text?.toString())

        return RemoteConnection(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            name = binding.etName.text?.toString()?.trim().orEmpty().ifEmpty { host },
            type = protocol,
            host = host,
            port = port,
            username = binding.etUsername.text?.toString()?.trim().orEmpty(),
            password = binding.etPassword.text?.toString().orEmpty(),
            path = path,
            useSsl = protocol.supportsSslToggle && binding.swSsl.isChecked,
            passiveMode = if (protocol.supportsPassiveMode) binding.swPassive.isChecked else true,
            timeoutMs = (timeoutSeconds * 1000).coerceIn(
                RemoteConnection.MIN_TIMEOUT_MS,
                RemoteConnection.MAX_TIMEOUT_MS
            )
        )
    }

    private fun save(alert: AlertDialog) {
        val connection = buildConnection() ?: return
        alert.dismiss()
        onSave(connection)
    }

    private fun clearErrorOnEdit(target: com.google.android.material.textfield.TextInputLayout) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (target.error != null) target.error = null
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }
}
