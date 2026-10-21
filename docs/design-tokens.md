# Material 3 设计令牌（Design Tokens）

> 本文件由 `docs/tokens.json` 与色调算法生成，记录配色、排版、形状、间距的设计依据。
> 应用内所有颜色/圆角/间距一律引用这里定义的令牌，布局与代码中不出现魔法值。

## 1. 设计目标

| 目标 | 落地方式 |
| --- | --- |
| 保留品牌识别 | 主色色相沿用原主题的紫色（种子 `#6200EE`），只把色度收敛到 M3 推荐范围 |
| 明暗一致 | 光/暗两套角色映射共用同一套色调板，仅切换 tone |
| 语义清晰 | 所有颜色以「角色」命名（`color_on_surface_variant`），不出现 `gray_600` 这类具象命名 |
| 可访问性 | 正文对比度 ≥ 4.5:1，关键文本 ≥ 7:1（见第 4 节实测值） |
| 可换肤 | 换品牌只需替换 8 个色调板的种子色，组件层零改动 |

## 2. 色调算法

M3 的 `tone` 与 CIELAB 的 `L*` 数值等价，因此可以用 CIELAB 精确复现 M3 色调板：

1. 种子色 sRGB → 线性 RGB → XYZ(D65) → CIELAB → `LCh`，得到品牌色的 **色相 H** 与 **色度 C**；
2. 对每个 tone（L* 目标值）保持 H 不变，色度取 `min(C_seed, 该 tone 下 sRGB 色域内最大色度)`，即色域裁剪（gamut mapping）；
3. 依次得到 tone 0/10/20/30/40/50/60/70/80/90/95/99/100 的色板；
4. 按 M3 规范把 tone 映射到语义角色：浅色用 40/90/10，深色用 80/30/90，中性表面用 neutral 98/6。

种子与色度上限：

| 色调板 | 种子 | 色度 C | 色相 H | 10 | 20 | 30 | 40 | 90 | 95 | 99 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| primary | `#6200EE` | 48 | 310° | `#24094F` | `#3D2067` | `#563681` | `#704E9B` | `#EDDCFF` | `#F6EDFF` | `#FDFBFF` |
| secondary | `#03DAC5` | 32 | 183° | `#00201C` | `#003731` | `#015048` | `#026A60` | `#93F4E4` | `#B4FFF2` | `#F2FFFC` |
| tertiary | `#B02E6B` | 36 | 356° | `#3E001F` | `#5C1235` | `#772C4C` | `#924564` | `#FFD9E5` | `#FFECF2` | `#FFFBFC` |
| error | `#B3261E` | 68 | 36° | `#380C00` | `#680100` | `#93010B` | `#B4271F` | `#FFDAD3` | `#FFEDE9` | `#FFFBFB` |
| success | `#2E7D32` | 44 | 140° | `#062100` | `#003909` | `#0E5217` | `#2C6B2E` | `#B1F3AB` | `#C8FFC2` | `#F5FFF3` |
| warning | `#B26A00` | 60 | 69° | `#291800` | `#482900` | `#693C00` | `#8A5101` | `#FFDCBE` | `#FFEEDF` | `#FFFCF9` |
| neutral | `#6200EE` | 4 | 310° | `#1D1B20` | `#322F35` | `#49464B` | `#605D63` | `#E5E1E8` | `#F3EFF7` | `#FDFBFF` |
| neutral-variant | `#6200EE` | 8 | 310° | `#1F1924` | `#342E39` | `#4A4450` | `#625C68` | `#E7E0EE` | `#F5EEFD` | `#FDFBFF` |

> 说明：`neutral` / `neutral-variant` 的色相继承主色（H≈310°），色度分别固定为 4 与 8，
> 因此浅色表面 `#FCF8FF`、深色表面 `#151218` 带有极轻的紫调，而不是纯灰——避免大面积纯灰与紫色并置显得「脏」。

主色色度从原始 `#6200EE` 的高饱和（C≈85）收敛到 **C=48**：
大面积界面使用高饱和紫会造成视觉疲劳，而 M3 建议 primary 色调板色度落在 36–48 区间。

## 3. 颜色角色映射

Light 取值见 `values/colors.xml`，Dark 取值见 `values-night/colors.xml`，主题中通过 `?attr/colorPrimary` 等形式引用。

### 主色 Primary

| 角色 (token) | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `color_primary` | `#704E9B` | `#DBB8FF` | 主要操作、选中态、强调图标 |
| `color_on_primary` | `#FFFFFF` | `#3D2067` | 主色之上的文字/图标 |
| `color_primary_container` | `#EDDCFF` | `#563681` | 主色容器（FAB、标签底色） |
| `color_on_primary_container` | `#24094F` | `#EDDCFF` | 主色容器之上的文字/图标 |
| `color_inverse_primary` | `#DBB8FF` | `#704E9B` | Snackbar 动作文字等反色场景 |

### 辅助色 Secondary

| 角色 (token) | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `color_secondary` | `#026A60` | `#77D7C8` | 次级强调、可交互的辅助元素 |
| `color_on_secondary` | `#FFFFFF` | `#003731` | 辅助色之上的文字 |
| `color_secondary_container` | `#93F4E4` | `#015048` | 文件夹图标容器等次级容器 |
| `color_on_secondary_container` | `#00201C` | `#93F4E4` | 次级容器之上的文字/图标 |

### 第三色 Tertiary

| 角色 (token) | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `color_tertiary` | `#924564` | `#FFB0CC` | 少量强调，避免与主色争夺注意力 |
| `color_on_tertiary` | `#FFFFFF` | `#5C1235` | 第三色之上的文字 |
| `color_tertiary_container` | `#FFD9E5` | `#772C4C` | 第三色容器 |
| `color_on_tertiary_container` | `#3E001F` | `#FFD9E5` | 第三色容器之上的文字 |

### 语义状态

| 角色 (token) | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `color_error` | `#B4271F` | `#FFB4A5` | 错误文字、异常状态点 |
| `color_on_error` | `#FFFFFF` | `#680100` | 错误色之上的文字 |
| `color_error_container` | `#FFDAD3` | `#93010B` | 错误提示背景 |
| `color_on_error_container` | `#380C00` | `#FFDAD3` | 错误提示背景之上的文字 |
| `color_success` | `#2C6B2E` | `#96D691` | 连接成功、传输完成 |
| `color_on_success` | `#FFFFFF` | `#003909` | 成功色之上的文字 |
| `color_success_container` | `#B1F3AB` | `#0E5217` | 成功提示背景 |
| `color_on_success_container` | `#062100` | `#B1F3AB` | 成功提示背景之上的文字 |
| `color_warning` | `#8A5101` | `#FFB86F` | 连接中、需要注意的状态 |
| `color_on_warning` | `#FFFFFF` | `#482900` | 警告色之上的文字 |
| `color_warning_container` | `#FFDCBE` | `#693C00` | 警告提示背景 |
| `color_on_warning_container` | `#291800` | `#FFDCBE` | 警告提示背景之上的文字 |

### 表面与描边

| 角色 (token) | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `color_background` | `#FDFBFF` | `#151218` | 窗口背景 |
| `color_on_background` | `#1D1B20` | `#E5E1E8` | 背景之上的文字 |
| `color_surface` | `#FCF8FF` | `#151218` | 应用栏、抽屉等主表面 |
| `color_on_surface` | `#1D1B20` | `#E5E1E8` | 主表面之上的主文本 |
| `color_surface_variant` | `#E7E0EE` | `#4A4450` | 次级表面（分隔、次级容器） |
| `color_on_surface_variant` | `#4A4450` | `#CBC4D2` | 次级文本、图标 |
| `color_surface_container_lowest` | `#FFFFFF` | `#100D13` | 最低层级容器 |
| `color_surface_container_low` | `#F6F2F9` | `#1D1B20` | 卡片、抽屉 |
| `color_surface_container` | `#F0ECF4` | `#211F24` | 路径胶囊、状态栏 |
| `color_surface_container_high` | `#EAE7EE` | `#2C292E` | 空状态圆形底 |
| `color_surface_container_highest` | `#E5E1E8` | `#363439` | 文件图标容器 |
| `color_surface_dim` | `#DCD9E0` | `#151218` | 深色模式下的凹陷表面 |
| `color_surface_bright` | `#FCF8FF` | `#3B383E` | 深色模式下的高亮表面 |
| `color_outline` | `#7B7481` | `#958E9B` | 输入框描边、未连接状态点 |
| `color_outline_variant` | `#CBC4D2` | `#4A4450` | 分割线、卡片描边 |
| `color_inverse_surface` | `#322F35` | `#E5E1E8` | Snackbar 背景 |
| `color_inverse_on_surface` | `#F3EFF7` | `#322F35` | Snackbar 文字 |
| `color_scrim` | `#000000` | `#000000` | 模态遮罩 |
| `color_surface_tint` | `#704E9B` | `#DBB8FF` | 海拔叠加色（本方案以容器分层替代） |

## 4. 对比度验证（WCAG 2.1）

| 前景 | 背景 | 用途 | 浅色对比度 | 深色对比度 | WCAG |
| --- | --- | --- | --- | --- | --- |
| `on_surface` | `surface` | 主文本 / 背景 | 16.27:1 | 14.38:1 | AAA（正文） |
| `on_surface_variant` | `surface` | 次级文本 / 背景 | 8.96:1 | 10.93:1 | AAA（正文） |
| `on_surface_variant` | `surface_container_high` | 次级文本 / 空状态容器 | 7.68:1 | 8.45:1 | AAA（正文） |
| `primary` | `surface` | 主色文字 / 背景 | 6.15:1 | 10.88:1 | AA（正文） |
| `on_primary` | `primary` | 按钮文字 / 主色 | 6.46:1 | 7.69:1 | AA（正文） |
| `on_primary_container` | `primary_container` | FAB 图标 / 主色容器 | 13.27:1 | 7.27:1 | AAA（正文） |
| `on_secondary_container` | `secondary_container` | 文件夹图标 / 次级容器 | 13.32:1 | 7.27:1 | AAA（正文） |
| `error` | `surface` | 错误文字 / 背景 | 6.16:1 | 10.89:1 | AA（正文） |
| `success` | `surface` | 成功状态点 / 背景 | 6.16:1 | 10.89:1 | AA（正文） |
| `outline` | `surface` | 描边 / 背景 | 4.30:1 | 5.85:1 | AA（大字/图形） |

结论：所有正文级文本在两个模式下均达到 AA 以上，主要阅读文本达到 AAA；
`outline` 仅用于 1dp 描边/分割线（非文本），符合「图形元素 ≥ 3:1」的要求。

## 5. 排版（Type Scale）

| Token | 字号/行高 | 字重 | 使用位置 |
| --- | --- | --- | --- |
| `TextAppearance.RFM.TitleLarge` | 20/28sp | medium | 顶部应用栏标题 |
| `TextAppearance.RFM.TitleMedium` | 16/24sp | medium | 文件列表主文本、抽屉标题 |
| `TextAppearance.RFM.TitleSmall` | 14/20sp | medium | 连接项名称、对话框小标题 |
| `TextAppearance.RFM.BodyMedium` | 14/20sp | regular | 路径、说明文字、开关标签 |
| `TextAppearance.RFM.BodySmall` | 12/16sp | regular | 列表副文本（大小/时间） |
| `TextAppearance.RFM.LabelMedium` | 12/16sp | medium | 抽屉分节标题（字距 0.04） |
| `TextAppearance.RFM.LabelSmall` | 11/16sp | medium | 底部状态栏 |
| `TextAppearance.RFM.HeadlineSmall` | 24/32sp | medium | 空状态标题 |

层级靠「字号 + 字重 + 颜色（onSurface → onSurfaceVariant）」三重差异建立，而不是只靠加粗。

## 6. 形状与间距

| Token | 值 | 使用位置 |
| --- | --- | --- |
| `radius_extra_small` | 4dp | 输入框圆角 |
| `radius_small` | 8dp | 图标容器、路径胶囊 |
| `radius_medium` | 12dp | 卡片 |
| `radius_large` | 16dp | 抽屉右侧圆角 |
| `radius_extra_large` | 28dp | 对话框 |
| `space_1 … space_8` | 2/4/8/12/16/24/32/48dp | 4dp 网格间距体系 |
| `screen_margin` | 16dp | 页面左右安全边距 |
| `list_item_min_height` | 64dp | 列表项最小高度（≥48dp 触控目标） |
| `touch_target` | 48dp | 所有图标按钮 |

## 7. 组件令牌映射

| 组件 | 令牌 |
| --- | --- |
| 顶部应用栏 | 背景 `color_surface`，标题 `TitleLarge`，底部 `MaterialDivider` |
| 路径胶囊 | `color_surface_container` + `radius_small` |
| 列表项 | 背景透明 + `?attr/selectableItemBackground`，图标容器 40dp |
| 文件夹图标容器 | `color_secondary_container` / `color_on_secondary_container` |
| 文件图标容器 | `color_surface_container_highest` / `color_on_surface_variant` |
| 状态栏 | `color_surface_container`，状态点语义色（success/warning/error/outline） |
| FAB | `color_primary_container` / `color_on_primary_container`，elevation 3dp |
| 对话框 | `ThemeOverlay.RFM.AlertDialog`，28dp 圆角 |
| 输入框 | 描边 1dp（聚焦 2dp 主色），错误态 `color_error` |
| Snackbar | `color_inverse_surface` / `color_inverse_on_surface`（M3 默认） |

## 8. 使用规则

1. **禁止硬编码**：布局与代码中不得出现 `#RRGGBB`、`dp` 字面量（除 0dp/1dp 描边），一律引用令牌。
2. **成对使用**：`color_*` 与 `color_on_*` 必须成对出现，禁止把 `on_*` 颜色用在非对应容器上。
3. **强调用色克制**：主色只用于「当前状态/主操作」，同一屏内主色面积不超过 20%。
4. **状态色语义固定**：`success` 只表示成功，`warning` 只表示进行中/需注意，`error` 只表示失败，不用于装饰。
5. **深色模式不叠阴影**：用 `surface_container_*` 五档表达层级，避免深色下阴影不可见。
