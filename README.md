# 远程网络文件管理器 (Remote Network File Manager)

一个基于 Kotlin + Swing 开发的远程网络文件管理器，支持多种协议。

## 支持协议

- **FTP** - 文件传输协议
- **FTPS** - FTP over SSL/TLS
- **SFTP** - SSH 文件传输协议
- **SMB/CIFS** - 服务器消息块协议
- **WebDAV** - 基于 Web 的分布式创作和版本控制

## 功能特性

### 连接管理
- 添加、编辑、删除远程连接
- 支持自定义主机、端口、用户名、密码
- 支持连接状态实时显示
- 支持主动/被动 FTP 模式

### 文件管理
- 浏览远程目录和文件
- 上传文件到远程服务器
- 从远程服务器下载文件
- 创建/删除目录
- 重命名文件/目录
- 复制/移动远程文件

### 用户界面
- 直观的 Swing 图形界面
- 连接列表管理
- 文件表格视图
- 右键菜单操作
- 状态栏实时反馈

## 技术栈

- **Kotlin 1.9.23** - 编程语言
- **Swing** - UI 框架
- **Apache Commons Net** - FTP/FTPS 支持
- **SSHJ** - SFTP 支持
- **JCIFS NG** - SMB 支持
- **HttpURLConnection** - WebDAV 支持

## 构建和运行

### 前置要求
- JDK 17 或更高版本
- Gradle 7.0 或更高版本

### 构建项目
```bash
cd RemoteFileManager
gradle build
```

### 运行程序
```bash
gradle run
```

### 生成 JAR 包
```bash
gradle build
# JAR 包位于 build/libs/RemoteFileManager-1.0.0.jar
java -jar build/libs/RemoteFileManager-1.0.0.jar
```

## 使用说明

### 1. 创建连接

1. 点击菜单栏 "文件" -> "新建连接"
2. 填写连接信息：
   - **连接名称**: 自定义连接名称，如 "My FTP Server"
   - **协议类型**: 选择 FTP、FTPS、SFTP、SMB 或 WebDAV
   - **主机地址**: 服务器地址，如 `192.168.1.100` 或 `example.com`
   - **端口**: 默认端口（FTP: 21, SFTP: 22, SMB: 445, WebDAV: 80）
   - **用户名**: 登录用户名
   - **密码**: 登录密码
   - **初始路径**: 连接后进入的目录，默认为 `/`

3. 点击 "连接" 按钮

### 2. 管理连接

- **连接/断开**: 在连接列表右键点击选择 "连接/断开"
- **编辑连接**: 在连接列表右键点击选择 "编辑连接"
- **删除连接**: 在连接列表右键点击选择 "删除连接"
- **测试连接**: 在连接列表右键点击选择 "测试连接"

### 3. 文件操作

#### 浏览文件
- 双击文件夹进入
- 点击 "上级" 按钮返回上级目录
- 点击 "主页" 按钮回到根目录
- 直接在路径栏输入路径并回车

#### 文件操作
- **复制**: 右键选择 "复制文件"
- **移动**: 右键选择 "移动文件"
- **重命名**: 右键选择 "重命名"
- **删除**: 右键选择 "删除"
- **下载**: 右键选择 "下载文件" 或双击文件
- **上传**: 点击菜单栏 "传输" -> "上传文件"

### 4. 快捷键

| 快捷键 | 功能 |
|--------|------|
| Ctrl+N | 新建连接 |
| F5 | 刷新列表 |
| Delete | 删除选中文件 |
| Enter | 打开/进入文件 |

## 项目结构

```
RemoteFileManager/
├── src/main/kotlin/com/filemanager/
│   ├── Main.kt                    # 主程序入口
│   ├── model/
│   │   ├── FileInfo.kt            # 文件信息模型
│   │   └── FileEvent.kt           # 文件事件模型
│   ├── manager/
│   │   ├── FileSystemManager.kt   # 管理器接口
│   │   ├── FileSystemManagerFactory.kt  # 管理器工厂
│   │   ├── ConnectionManager.kt   # 连接管理器
│   │   ├── FtpFileSystemManager.kt     # FTP 实现
│   │   ├── FtpsFileSystemManager.kt    # FTPS 实现
│   │   ├── SftpFileSystemManager.kt    # SFTP 实现
│   │   ├── SmbFileSystemManager.kt     # SMB 实现
│   │   └── WebDavFileSystemManager.kt  # WebDAV 实现
├── build.gradle.kts               # Gradle 构建配置
├── settings.gradle.kts            # Gradle 设置
└── README.md                      # 说明文档
```

## 常见问题

### Q: 连接 FTP 超时怎么办？
A: 尝试切换到被动模式。在连接设置中勾选 "被动模式"。

### Q: SFTP 连接失败？
A: 确保服务器开启了 SSH/SFTP 服务，并检查端口和用户名密码是否正确。

### Q: SMB 连接需要安装额外软件吗？
A: 不需要，但确保网络可达且 SMB 共享已正确配置。

### Q: WebDAV 不支持？
A: 确保服务器支持标准 WebDAV 协议。

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
# Trigger CI
