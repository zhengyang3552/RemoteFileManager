# 远程网络文件管理器 (Remote Network File Manager)

一个基于 **Kotlin + Android 原生视图 + Material 3** 的远程文件管理器，支持 FTP / FTPS / SFTP / SMB / WebDAV 五种协议。

> 设计体系与配色依据见 [docs/design-tokens.md](docs/design-tokens.md)，本次改造的详细说明见 [docs/redesign.md](docs/redesign.md)。

## 支持协议

| 协议 | 实现库 | 默认端口 | 服务端复制 | 说明 |
| --- | --- | --- | --- | --- |
| FTP | Apache Commons Net | 21 | 下载→上传中转 | 支持主动 / 被动模式 |
| FTPS | Apache Commons Net (FTPSClient) | 990 | 下载→上传中转 | 990 隐式 TLS，其他端口显式 AUTH TLS |
| SFTP | SSHJ | 22 | 下载→上传中转 | 密码认证，主机密钥不校验（自建服务器场景） |
| SMB/CIFS | jcifs-ng | 445 | 下载→上传中转 | 支持 SMB2/3，`DOMAIN\user`、`user@domain` 两种账号写法 |
| WebDAV | HttpURLConnection | 80 / 443 | COPY / MOVE | 支持 HTTPS，PROPFIND 解析目录 |

## 功能特性

### 连接管理
- 新建 / 编辑 / 删除连接，配置本地持久化（密码经 Android Keystore AES-GCM 加密）
- 连接前测试（对话框内直接验证凭据，不写入配置）
- 协议联动：切换协议自动填充默认端口、显示或隐藏加密 / 被动模式开关
- 连接状态实时可视化（已连接 / 连接中 / 异常 / 未连接）

### 文件管理
- 浏览目录、路径跳转、返回上级、回到根目录
- 上传（SAF 选择文件，同名冲突二次确认）、下载（SAF 选择保存位置）
- 新建文件夹、重命名、删除（目录递归删除，二次确认）
- 复制 / 移动到指定路径（同名冲突二次确认；不支持服务端复制时自动中转）
- 排序（名称 / 大小 / 修改时间，升序 / 降序，偏好持久化）
- 当前目录内即时筛选

### 界面
- Material 3 设计体系：完整语义化颜色角色、明暗双主题、统一设计令牌
- 五态内容区（列表 / 空目录 / 无匹配 / 未连接 / 错误可重试），不再出现空白页
- 下拉刷新、FAB 主操作、溢出菜单、Snackbar 反馈
- 外观模式可切换「跟随系统 / 浅色 / 深色」，启动时即生效无闪屏

## 技术栈

- **Kotlin 1.9.23** + **AGP 8.2** / Gradle 8.6 / compileSdk 34 / minSdk 24
- **AndroidX**：AppCompat、ConstraintLayout、RecyclerView、SwipeRefreshLayout、Activity KTX
- **Material Components 1.11.0**（Material 3 主题 `Theme.Material3.DayNight.NoActionBar`）
- **ViewBinding** 替代 `findViewById`
- 协议库：commons-net、sshj、jcifs-ng
- 单测：JUnit4（路径规范化、格式化、排序、异常映射）

## 构建和运行

### 前置要求
- JDK 17 或更高
- Android SDK Platform 34 + Build-Tools 34

### 构建
```bash
# Debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 单元测试
```bash
./gradlew testDebugUnitTest
```

### 安装
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

### 1. 新建连接
1. 打开左侧抽屉 → 「新建连接」
2. 填写连接名称、协议、主机、端口、用户名、密码与初始路径
3. 可先点「测试连接」验证，再点「保存」；保存后自动连接

> 初始路径不存在时会自动回退到根目录，不会直接连接失败。

### 2. 浏览与操作
| 操作 | 方式 |
| --- | --- |
| 进入目录 | 点击文件夹 |
| 返回上级 | 路径栏 ↑ 按钮，或系统返回键 |
| 回到根目录 | 路径栏 ⌂ 按钮 |
| 跳转指定路径 | 点击路径文字，输入绝对路径 |
| 刷新 | 下拉刷新 / 路径栏刷新按钮 |
| 上传 | 右下角 FAB |
| 下载 | 点击文件，或条目右侧「⋮」→ 下载 |
| 重命名 / 复制 / 移动 / 删除 | 条目右侧「⋮」 |
| 筛选当前目录 | 应用栏放大镜 |
| 排序 | 应用栏排序按钮（再次点击可切换升/降序） |

### 3. 多连接切换
抽屉中点击任一连接即切换；长按或点击「⋮」可编辑、删除、断开。

## 项目结构

```
RemoteFileManager/
├── app/src/main/java/com/filemanager/
│   ├── App.kt                      # Application：启动前应用外观模式
│   ├── MainActivity.kt             # 主界面：状态机、远程调度、对话框编排
│   ├── model/                      # FileInfo / RemoteConnection / ProtocolType / SortField
│   ├── manager/
│   │   ├── ConnectionManager.kt    # 会话门面：路径托管、串行化、能力降级
│   │   ├── FileSystemManager.kt    # 协议抽象接口
│   │   ├── FileSystemManagerFactory.kt
│   │   ├── RemoteException.kt      # 归一化异常（Reason 枚举）
│   │   ├── ErrorMapper.kt          # 底层异常 → Reason
│   │   └── impl/                   # FTP / SFTP / SMB / WebDAV 实现
│   ├── data/                       # 连接持久化、密码加密、设置存储
│   ├── ui/                         # 适配器、连接编辑对话框、文案与 Snackbar
│   └── util/                       # 路径规范化、格式化、排序、网络状态
├── app/src/main/res/
│   ├── values/                     # colors(light) / dimens / type / shapes / styles / themes / strings
│   ├── values-night/               # colors(dark) / themes
│   ├── layout/                     # activity_main / item_file / item_connection / dialog_*
│   └── drawable/                   # 矢量图标与形状
├── app/src/test/java/com/filemanager/  # 单元测试
├── docs/
│   ├── design-tokens.md            # 设计令牌与配色依据
│   ├── tokens.json                 # 令牌数据（由生成脚本产出）
│   └── redesign.md                 # 改造说明
└── src/main/kotlin/                # 早期桌面（Swing）原型，保留作参考，不参与 Android 构建
```

## 已知限制

- SFTP 目前仅支持密码认证，尚未接入私钥 / 密钥文件选择。
- FTPS 与 SFTP 为兼容自签名证书/自建服务器，放宽了证书与主机密钥校验；生产环境建议改为固定 CA 与 known_hosts 校验。
- WebDAV 上传使用 chunked 传输，极少数只接受 `Content-Length` 的老旧服务端可能拒绝。
- 不支持服务端复制的协议，复制大文件（>512MB）会被拒绝，避免占满本机缓存。

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
