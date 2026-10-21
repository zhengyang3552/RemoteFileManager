# 改造说明：Material 3 视觉重构 + 布局/交互优化 + 核心功能完善

## 0. 改造范围与约束

| 约束 | 说明 |
| --- | --- |
| 技术栈不变 | 仍是 Kotlin + Android 原生 View 体系 + Gradle；Material Components 库本来同时提供 M2/M3 主题，本次把主题切换到 `Theme.Material3.DayNight.NoActionBar`，未引入 Compose 或任何新的 UI 框架 |
| 项目结构不变 | 包结构保留 `model/`、`manager/`，按职责新增 `data/`（持久化）、`ui/`（适配器与对话框）、`util/`（路径/格式化/排序）、`manager/impl/`（协议实现）；`res/` 目录结构与资源命名方式不变 |
| 功能可用 | 原有入口（连接、浏览、上传、下载、重命名、删除、复制、移动、排序、抽屉）全部保留；`app-debug.apk` 可正常构建，单元测试与界面冒烟测试通过 |
| 改造目标 | `settings.gradle.kts` 只 `include(":app")`，因此 Android 模块是实际交付物；根目录 `src/main/kotlin` 的 Swing 原型保留作参考，未参与构建、未改动 |

> 构建链路小调整：`gradle-wrapper` 8.2 → 8.6、Kotlin 1.8.22 → 1.9.23（AGP 8.2 要求 Gradle ≥ 8.2，而 Gradle 8.2 无法运行在 JDK 21 上；CI 的 JDK 17 同样兼容 8.6）。业务代码与依赖库版本未改变。

---

## 1. 配色重构：从「两个颜色」到完整 M3 令牌体系

### 1.1 原状

```xml
<!-- 改造前：只有 5 个色值 + 3 个语义色，且布局里直接硬编码 -->
<item name="colorPrimary">@color/purple_500</item>
<item name="colorPrimaryVariant">@color/purple_700</item>
<!-- activity_main.xml -->
android:background="#f5f5f5"   <!-- 路径栏 -->
android:background="#e0e0e0"   <!-- 状态栏 -->
android:textColor="#666666"    <!-- 副文本 -->
```

问题：没有 `onXxx` 配对色（深色模式必然出现对比度不足）、用 M2 的 `colorPrimaryVariant` 语义、布局里散落硬编码色值、深色模式下 `#f5f5f5` 依然是浅灰。

### 1.2 新体系（三层）

```
种子色 (8 个色调板)  →  语义角色 (44 个 color_*)  →  组件样式 (Widget.RFM.*)
   md_primary_40              color_primary              Widget.RFM.Button
```

- **色调板**：`md_primary_0 … md_primary_100` 等 8 组，共 236 个 tone 值（`values/colors.xml` 与 `values-night/colors.xml` 各一份，tone 相同、角色映射不同）。
- **语义角色**：`color_primary` / `color_on_primary` / `color_primary_container` / `color_on_primary_container` / `color_surface_container_low` … 共 44 个，全部成对提供 `on_*`。
- **组件样式**：布局只写 `style="@style/Widget.RFM.Button.Tonal"` 这类语义样式，颜色/圆角/描边在样式层统一。

### 1.3 配色依据（摘要，完整推导见 `docs/design-tokens.md`）

1. **品牌继承**：主色种子取原主题的 `#6200EE`，只保留其色相（H≈310°），把色度从 C≈85 收敛到 **C=48**。原色是为按钮/小面积高亮设计的高饱和紫，直接铺满界面（应用栏、FAB、选中态）会视觉疲劳；M3 的 primary 色调板通常落在 36–48。
2. **三色结构**：`primary` 紫（品牌、主操作）、`secondary` 青 `#03DAC5`（原项目的辅助色，用于文件夹图标容器等次级强调）、`tertiary` 玫红 `#B02E6B`（极少量强调，避免抢主色）。
3. **扩展语义色**：M3 只内置 `error`，但文件管理器需要「连接成功 / 连接中 / 失败」三态，因此按同一套 tone 规则从绿/琥珀种子推导出 `success`（tone 40/80）与 `warning`（tone 40/80），并配套 container 与 on 色——不是随手挑的 `#4CAF50`。
4. **中性色带品牌调**：`neutral`（C=4）与 `neutral-variant`（C=8）沿用主色色相，于是浅色 surface=`#FCF8FF`、深色 surface=`#151218`，是极轻的紫调灰而非纯灰，和紫色主色并置时不会显脏。
5. **明暗映射遵循规范**：浅色用 tone 40 作 primary、90 作容器、10 作 onContainer；深色反转为 80/30/90。表面用 neutral 98/6，并补齐 `surface_container_lowest/low/container/high/highest` 五档（对应 tone 100/96/94/92/90 与 4/10/12/17/22）——**深色模式靠容器明度分层，不靠阴影**。
6. **对比度实测**（`docs/design-tokens.md` 第 4 节）：正文文本浅色 ≥ 8.9:1、深色 ≥ 10.9:1；按钮文字 6.5:1 / 7.7:1；次级文本在空状态容器上 7.7:1 / 8.5:1，全部达到 WCAG AA，主要阅读文本达到 AAA。
7. **系统栏与图标**：状态栏 = `color_surface`、导航栏 = `color_surface_container`，`windowLightStatusBar` 在 `values-night` 中翻转；启动图标改为自适应图标（背景 tone 40 + 矢量前景 + monochrome），并为 API 24/25 补了 `mipmap-anydpi` 回退。

### 1.4 明暗模式

- 颜色天然分流：`values/colors.xml` 与 `values-night/colors.xml` 同名字符串，主题只引用 `@color/color_*`。
- 用户偏好：抽屉「外观」→ 跟随系统 / 浅色 / 深色，写入 `AppSettings`，在 `App.onCreate()` 里 `AppCompatDelegate.setDefaultNightMode` 提前应用，**切换后不闪屏**。
- 所有 `Surface`/文本/描边/状态点都走角色色，切换主题无需任何布局改动。

---

## 2. 布局与交互优化

### 2.1 信息层级

改造前：`Toolbar(紫色, 白字) → 路径栏(#f5f5f5, 常驻输入框 + 4 个系统菜单图标) → 列表 → 状态栏(#e0e0e0)`，四个区块四种底色，视觉噪声大。

改造后：

| 层级 | 内容 | 视觉手段 |
| --- | --- | --- |
| 1 主层 | 应用栏标题 `远程文件管理器` + 搜索/排序 | surface 背景、TitleLarge、无阴影、底部 1dp 分割线 |
| 2 位置 | 根目录 / 上级 / 当前路径胶囊 / 刷新 | surfaceContainer 胶囊 + 48dp 图标按钮，路径单行省略前缀 |
| 3 内容 | 文件列表（唯一可变区域） | 64dp 行高、72dp 内缩分割线、图标容器区分类型 |
| 4 辅助 | 状态点 + 连接信息 + 条目数 | surfaceContainer 细条、LabelSmall |

具体变化：
- 路径从**常驻输入框**改为**可点击的路径胶囊**：点击后弹出对话框输入绝对路径。减少了常驻表单控件，也让「当前在哪」比「可输入框」更突出。
- 图标按钮从系统 `android.R.drawable.ic_menu_*`（风格不一、部分还是 3D 拟物）换成统一的 24dp 矢量图标（`ic_home`/`ic_arrow_upward`/`ic_refresh`/`ic_search`…）。
- 文件类型用**图标容器**区分：文件夹 = `secondary_container` 填充、文件 = `surface_container_highest`、`..` = 描边透明容器。原来只有 `#FFC107` 文件夹 + 黑色文件图标。
- 列表项文本从「`tvName` 16sp bold + `#666666` 12sp」改为 `TitleMedium` + `BodySmall(onSurfaceVariant)`，并统一 `文件夹/文件 · 大小 · 修改时间` 的元信息格式。

### 2.2 间距与形状一致性

- 新增 `dimens.xml` 作为唯一间距来源：`space_1..space_8`（2/4/8/12/16/24/32/48dp）对应 4dp 网格；`screen_margin=16dp`、`list_item_min_height=64dp`、`touch_target=48dp`。
- 形状令牌 `ShapeAppearance.RFM.*`：4dp（输入框）/ 8dp（图标容器、路径胶囊）/ 12dp（卡片）/ 16dp（抽屉）/ 28dp（对话框），替代原来散落的 `8dp padding`、`12dp padding`、默认直角。
- 分割线用 `divider_list_item.xml`（左侧 72dp 内缩），与文本起始位置对齐，而不是通栏的 `DividerItemDecoration` 默认样式。
- 布局中不再出现 `#RRGGBB` 与魔法数（保留 0dp/1dp 描边）。

### 2.3 交互改进

| 场景 | 改造前 | 改造后 |
| --- | --- | --- |
| 长按文件 | 500ms 延时 + 手写 `onInterceptTouchEvent`，且 `position > 0` 才能触发，容易误触 | 每行右侧「⋮」溢出菜单；连接项额外保留长按 |
| 重命名 | 复用了整个「新建连接」表单布局，用户会看到主机/端口等无关字段 | 专用单输入框对话框，预填原名并全选 |
| 复制/移动 | 复制固定为 `copy_of_name`，移动实际上只做了重命名 | 输入目标路径，默认当前目录；同名冲突二次确认；支持覆盖 |
| 状态反馈 | 仅底部一行文字，连接失败也只弹 Toast | 五态内容区 + Snackbar + 状态点语义色；错误态提供「重试」 |
| 上传/下载 | `ACTION_GET_CONTENT` + `lastPathSegment` 当文件名（常得到 `primary:Download/x.pdf` 这类脏名） | SAF + `OpenableColumns.DISPLAY_NAME` 解析 + `RemotePath.sanitizeFileName` 清洗 |
| 返回键 | 未处理，直接退出 | 逐级处理：关抽屉 → 收搜索 → 上级目录 → 退出 |
| 刷新 | 只能点菜单 | 下拉刷新 + 路径栏刷新 + 应用栏菜单 |
| 文件过多 | 无排序/筛选 | 名称/大小/时间排序（升降序，持久化）+ 当前目录即时筛选 |
| 主题 | 只能跟随系统 | 系统/浅色/深色三选一，持久化 |

### 2.4 可访问性

- 所有图标按钮 `contentDescription`，触控目标 ≥ 48dp（`touch_target`）。
- 文字对比度达到 AA/AAA（第 1.3 节）。
- 状态不只靠颜色：连接状态同时有文字（状态栏）与圆点；排序菜单用 `checkableBehavior="single"` 提供选中语义。
- 列表项主文本单行省略，副文本单行省略，避免长文件名把布局撑破。

---

## 3. 核心功能梳理与异常处理

### 3.1 原来「看起来能用」但实际是空实现

改造前的 `ConnectionManager` 全部是 stub：

```kotlin
fun connect(connection: RemoteConnection): Boolean {
    connection.connectionState = ConnectionState.CONNECTED   // 直接标记成功
    return true
}
fun listFiles(...): List<FileInfo> = emptyList()            // 永远空
fun downloadFile(...) = true                                // 什么也没做
```

因此「连接成功但列表永远为空」「下载永远提示成功」是必然结果。本次补齐了四个协议的真实实现：

| 协议 | 关键实现点 |
| --- | --- |
| FTP / FTPS | Commons Net；`CWD + LIST .` 列举（兼容不支持绝对路径列举的服务器）；二进制传输；FTPS 支持显式（AUTH TLS）与隐式（990）两种模式，`PROT P` 加密数据通道；FTP 返回码映射到错误原因（530→认证、550→权限…） |
| SFTP | SSHJ；连接/读超时分离；`ls` 过滤 `.`/`..`；上传用 `WRITE|CREAT|TRUNC` 明确覆盖语义；递归删除逐层 `rmdir` |
| SMB | jcifs-ng（SMB2/3）；`DOMAIN\user`、`user@domain`、纯用户名三种写法；`/` 列共享、`/share/dir` 列目录；超时通过 `PropertyConfiguration` 注入 |
| WebDAV | `HttpURLConnection` + PROPFIND 解析；路径按段百分号编码（中文/空格文件名）；过滤响应中的自身条目；COPY/MOVE 支持服务端复制；关闭 XXE |

### 3.2 线程模型

- 所有远程 I/O 走单线程 `ExecutorService` + `Handler(Looper.getMainLooper())` 回主线程；`ConnectionManager` 方法全部 `@Synchronized`，保证同一时刻只有一个协议操作在跑（FTP 控制通道、SFTP 会话都是有状态的，并发调用会串包）。
- 回调前检查 `isFinishing || isDestroyed`，避免旋转/退出后更新已销毁的视图。
- `onDestroy` 关闭会话与线程池。

### 3.3 错误归一化

```
UnknownHostException / ConnectException   → HOST_UNREACHABLE
SocketTimeoutException / "timed out"      → TIMEOUT
"530 Login incorrect" / SmbAuthException  → AUTH_FAILED
"550 Permission denied" / HTTP 403        → PERMISSION_DENIED
"550 No such file" / HTTP 404             → NOT_FOUND
"File already exists" / HTTP 409          → ALREADY_EXISTS
HTTP 405/501 / UnsupportedOperation       → UNSUPPORTED
未联网（ConnectivityManager）              → NETWORK_UNAVAILABLE
```

`ErrorMapper` 把各库异常压成 12 种 `Reason`，`ErrorTexts` 再把 `Reason` 翻成中文提示；因此同一个「认证失败」在列表加载、上传、测试连接三个入口的文案一致，且协议细节（如 `550`）作为补充信息出现在「协议通信失败：…」里。

### 3.4 边界与异常清单（本次补充）

| 场景 | 处理 |
| --- | --- |
| 初始路径配错 | 回退到根目录并继续连接，而不是直接失败 |
| 服务器返回非 2xx 欢迎码 | 与登录失败区分上报（`HOST_UNREACHABLE` vs `AUTH_FAILED`） |
| 目录为空 / 筛选无结果 / 未连接 / 加载失败 | 四种空状态各有图标、标题、说明与可选动作（重试 / 新建连接） |
| 名称含 `/ \ : * ? " < > \|`、`.`、`..`、超长、纯空格 | 表单内联报错，服务端不会收到非法名 |
| 端口非数字 / 越界（1–65535）、超时越界（5–120s） | 表单校验，不发起网络请求 |
| 连接名重复（忽略大小写） | 保存时拦截，编辑自身不误判 |
| 新建文件夹/重命名/复制/移动的目标已存在 | 先检测，弹「覆盖」二次确认，用户可取消 |
| 复制到同一路径 / 重命名为原名 | 直接拦截并提示 |
| 删除目录 | 明确提示「及其中的全部内容」，递归删除 |
| 下载中途失败 | 删除 SAF 中写了一半的文档，避免留下 0 字节文件 |
| 上传文件名为 SAF 编码串 | `OpenableColumns` → `sanitizeFileName` → 兜底 `untitled` |
| 服务器不支持服务端复制 | 自动降级「下载到缓存→上传」，并限制 512MB 以内 |
| 未选择文件 / 没有可用文件选择器 | 捕获 `ActivityNotFoundException`，提示而非崩溃 |
| 旋转屏幕 | 保存连接 id 与当前路径；`configChanges` 保持会话不中断 |
| 密码存储 | Android Keystore AES-256/GCM；Keystore 异常时降级 Base64（并在文档中标注安全边界） |
| 配置损坏 | `ConnectionStore` 逐条解析、坏记录跳过，不会整表丢失 |

---

## 4. 验证结果

| 项目 | 结果 |
| --- | --- |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL，产物 `app/build/outputs/apk/debug/app-debug.apk`（约 10MB） |
| `./gradlew testDebugUnitTest` | 通过：路径规范化 9、格式化 3、排序 6、异常映射 8、界面冒烟 2（含深色模式角色校验） |
| 资源链接 | 主题属性全部可解析（M3 attrs + 自定义 `Widget.RFM.*`） |
| 未覆盖 | 未接入真机/模拟器做端到端联调，也未连接真实的 FTP/SFTP/SMB/WebDAV 服务器；协议层以编译期检查 + 异常路径单测为主 |

## 5. 后续建议

1. SFTP 私钥认证与 known_hosts 固定；FTPS 改为可配置 CA，而不是接受全部证书。
2. 传输任务队列 + 前台服务（大文件上传/下载在后台继续，带进度通知）。
3. 多选与批量操作（批量删除/移动）。
4. 用 `Paging` 处理超大目录，避免一次拉全量。
5. 若后续要引入动态取色（Material You），只需在 `App.onCreate` 里加 `DynamicColors.applyToActivitiesIfAvailable(this)`；当前关闭是为了保证品牌一致性。
