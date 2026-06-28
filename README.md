<div align="center">

# 🎵 BlingBeats

**你的本地音乐播放器**

一款采用 Jetpack Compose 构建的 Android 本地音乐播放器，  
以 Windows 磁贴墙 + 3D 专辑轮播为核心交互，支持嵌入式歌词同步显示。

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](#)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.02-4285F4?logo=jetpackcompose&logoColor=white)](#)

</div>

---

## ✨ 功能特色

### 🎨 无边界磁贴墙（竖屏）
- **Windows 风格磁贴布局** — 多种尺寸磁贴（1×1、2×1、1×2、2×2）无缝紧密拼接
- **2D 无边界双向平铺** — 升级为 **2x2 视口平铺** 架构，可向任意方向无限拖动，无任何滑动边界与黑边
- **自动曲目双重填充** — 歌曲较少时（少于 30 首）自动进行虚拟 ID 复制填充，确保小列表也有完美的平铺覆盖效果
- **长按拖拽排序** — 长按磁贴进入拖动模式，松手后与目标磁贴交换位置，位置交换持久化保存
- **分身同步播放状态** — 所有平铺出来的分身封面皆可独立点击播放并映射回原曲，且同时亮起发光播放状态指示
- **歌曲标题开关** — 可在右上角菜单中一键切换磁贴标题显示/隐藏，满足极简视觉追求

### 🎠 3D 专辑轮播（横屏）
- **3D 透视旋转** — 左右邻居专辑带 rotationY 3D 倾斜效果，中心专辑正对显示
- **无限循环滑动** — 采用虚拟页实现近似无限循环切换
- **惯性吸附动画** — 自定义 fling 行为，快速滑动后自然减速吸附到中心
- **滑动即播放** — 滑动停止后自动切换到对应曲目
- **沉浸式自动收起** — 播放栏在横屏下无操作 3.5 秒后自动收起，而歌名和同步滚动歌词永久显示以提供沉浸感。切歌时自动唤醒播放栏

### 🎤 歌词显示与切歌
- **即时切歌行为** — 优化为双击上一首/下一首直接切歌，跳过 ExoPlayer 默认的“当前进度大于 3s 时点击上一首重新播放本曲”的设定
- **嵌入式歌词提取** — 支持从 MP3（ID3 USLT）、M4A（iTunes ©lyr）、FLAC、OGG 等格式提取
- **LRC 时间同步** — 支持 `[mm:ss.xx]` 格式歌词逐行同步高亮
- **纯文本滚动** — 无时间戳的歌词以跑马灯方式滚动展示
- **多策略兼容** — MediaMetadataRetriever → jaudiotagger → 反射遍历标签字段，层层兜底

### ⚡ 顶级性能优化（极致省电）
- **GPU 硬件级位移加速** — 滑动偏移量彻底从 Compose 测量排版中剥离，交由 `graphicsLayer` 的 `translation` 直接操作 GPU 图层，全墙拖拽时维持 120 FPS 绝对丝滑
- **视口裁剪剔除 (Viewport Culling)** — 渲染层自动计算当前不可见的离屏磁贴并设置 `alpha = 0`，避免无效的 GPU 像素填充率消耗
- **生命周期感知型变频轮询** — 重构了原来高能耗的 16ms 轮询器，引入 App 生命周期感知：
  - **前台播放**：`100ms` 刷新率（兼顾极致歌词同步精度与低能耗）
  - **后台播放**：`1000ms` 刷新率（仅维持底限进度更新，规避高频 JNI / Binder 负载）
  - **暂停状态**：`2000ms` 刷新率（让 CPU 保持深度休眠）
  - **瞬时对齐**：返回前台的瞬间立即同步进度，规避任何视觉跳跃与延迟
### 📁 文件管理与 AI 交互
- **趣味 AI 扫描提示** — 扫描音频文件时集成极客范十足的 AI 幽默提示语（如「量子音频矩阵启动中」、「深度思考中...」、「绕过系统限制中」），为枯燥的文件加载增添趣味性
- **SAF 文件夹选择** — 通过系统文件选择器授权，支持持久化 URI 权限
- **递归扫描** — 自动遍历所选文件夹及子文件夹
- **崩溃恢复** — 加载过程崩溃后自动清除文件夹记忆，避免启动循环崩溃
- **重新扫描** — 随时手动触发重新扫描，刷新歌词等元数据

### 🎲 磁贴持久化与随机排列
- **磁贴随机排列** — 点击菜单项中带有 Casino 扑克骰子视觉风格的「随机排列」按钮，打乱网格排版
- **布局种子持久化** — 排列种子被保存至 `SharedPreferences`，旋转屏幕或重新启动 App 依然能完美重现上次的自定义磁贴排列
- **平铺状态同步** — 2D 平铺产生的分身（2x2 共 4 份拷贝）支持高度一致的随机序列映射，确保无限滚动时磁贴的尺寸（1x1、2x2）和位置在视口拼接处严丝合缝

---

## 📱 支持格式

| 格式 | 扩展名 | 歌词提取 |
|------|--------|---------|
| MP3 | `.mp3` | ✅ ID3 USLT/SYLT |
| AAC/M4A | `.m4a` `.aac` | ✅ iTunes LYRICS |
| FLAC | `.flac` | ✅ Vorbis LYRICS |
| OGG | `.ogg` | ✅ Vorbis LYRICS |
| WAV | `.wav` | ⚠️ 有限支持 |
| WMA | `.wma` | ⚠️ 有限支持 |

---

## 🏗️ 项目架构与核心设计

```
LocalBeats/
├── app/src/main/java/com/localbeats/
│   ├── LocalBeatsApp.kt              # Application 入口
│   ├── MainActivity.kt               # 主 Activity + 导入页 + 路由 + Activity 生命周期监听
│   ├── data/
│   │   ├── model/
│   │   │   └── MusicTrack.kt         # 音轨数据模型
│   │   ├── lyrics/
│   │   │   ├── LyricsExtractor.kt    # 歌词提取（jaudiotagger）
│   │   │   └── LyricsParser.kt       # LRC 歌词解析 + 时间同步
│   │   ├── player/
│   │   │   ├── MusicPlayer.kt        # ExoPlayer 封装（即时双击切歌重构）
│   │   │   └── MusicPlaybackService.kt  # 前台播放服务
│   │   └── repository/
│   │       └── MusicRepository.kt    # 音乐文件扫描 + 元数据提取
│   ├── viewmodel/
│   │   └── MusicViewModel.kt         # UI 状态管理 + 变频进度轮询器
│   └── ui/
│       ├── theme/
│       │   └── Theme.kt              # Material3 暗色主题
│       ├── screens/
│       │   ├── TileWallScreen.kt     # 竖屏无边界磁贴墙（Culling 剔除 + GPU 位移）
│       │   └── CarouselScreen.kt     # 横屏 3D 轮播（沉浸式自动隐藏控制条）
│       └── components/
│           ├── PlayerBar.kt          # 胶囊播放栏（Lambda 延迟状态读取）
│           ├── CarouselPager.kt      # 轮播子项
│           ├── MusicColors.kt        # 调色板
│           └── ReflectionEffect.kt   # 反射特效
```

---

## ⚡ 性能优化深度分析

### 1. 延迟重组屏障 (Deferred State Read via Lambda)
* **痛点**：音乐播放时，ExoPlayer 产生的播放进度是极高频更新的。如果直接将 `currentPosition: Long` 作为状态传入 `TileWallScreen` 或 `CarouselScreen`，每次进度的细微更新都会触发整个主屏幕甚至所有磁贴重组，导致 CPU 占用高、滑动卡顿。
* **解决方案**：我们将参数重构为 `currentPositionProvider: () -> Long` 这一 Lambda 形式。这使得 Compose 状态的读取点被推迟到最底层的歌词字符比对环节，形成了一道“重组屏障”，隔离了上层所有大面积的 UI 组件，播放时 CPU 占用骤降。

### 2. GPU 位移加速 (GPU-Accelerated Translation)
* **痛点**：在磁贴墙无限滑动过程中，若在 layout 测量和布局阶段直接加算 `offsetX` 和 `offsetY` 坐标，每次滑动像素变动都会导致全部 120 个磁贴重新执行 MeasurePass & LayoutPass，引起主线程阻塞。
* **解决方案**：我们将滚动偏移值全部交给 `Modifier.graphicsLayer { translationX = offsetX; translationY = offsetY }` 处理。该属性直接在 GPU 渲染节点（RenderNode）中做几何变换，跳过了主线程的测量和布局，滑动体验直接拉满到设备刷新率上限。

### 3. 视口裁剪剔除 (Viewport Culling)
* **痛点**：虽然磁贴在 GPU 中平移，但在 2x2 平铺无缝拼接模式下，屏幕外存在大量的不可见磁贴分身（多达 90+ 个），它们仍然会消耗填充率。
* **解决方案**：在 `graphicsLayer` 内部实时进行坐标重算。若磁贴完全在屏幕视口范围（`containerWidth` × `containerHeight`）之外，主动将 `alpha` 设为 `0f`。Android 硬件绘制系统将自动抛弃离屏磁贴，大幅降低功耗。

### 4. 生命周期感知自适应轮询 (Lifecycle-Aware Adaptive Polling)
* **痛点**：原本每秒 60 次 (16ms) 的 ExoPlayer 进度查询即便在暂停状态或应用在后台时也持续运作。这造成了极其严重且毫无必要的后台电量开销和 JNI 跨语言调用负载。
* **解决方案**：
  * 引入 `LocalLifecycleOwner` 状态，实时获知应用前后台状态。
  * 根据当前状态动态切换轮询间隔：前台播放中采用 `100ms`（保证精准歌词）；后台播放中降频至 `1000ms`（仅维持锁屏底限数据更新）；暂停状态深度降频至 `2000ms`（允许 CPU 充分休眠）。
  * 返回前台的瞬间触发立即刷新，消除了从后台重回前台时的进度条“跳变”或卡顿延迟。

---

## 🛠️ 构建与运行

### 环境要求
- **Android Studio** Hedgehog (2023.1.1) 或更高版本
- **JDK 17**
- **Android SDK 34**

### 快速构建
```bash
# 1. 克隆项目
git clone https://github.com/52blingbling/LocalBeats.git
cd LocalBeats

# 2. 构建调试版 APK
./gradlew assembleDebug

# 3. 安装到已连接的设备
./gradlew installDebug
```

---

## 🎨 设计亮点

- **Edge-to-Edge** — 全屏沉浸式设计，主界面完全延伸至状态栏和导航栏下方
- **Apple 风格液态玻璃播放栏** — 半透明磨砂玻璃质感，渐变高光边线结合超凡阴影表现
- **3.5 秒智能沉浸式隐藏** — 横屏 3D 卡片播放时控制条无操作自动收缩，永久显示歌词提升视听一体化体验
- **黑白双生经典极简 UI** — 契合音乐发烧友审美的极简黑底白片风格
- **双击即切歌** — 重构底层切歌事件，跳过了 Media3 标准播放器对上一首的首播回退机制

---

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 协议开源。

---

<div align="center">

**用 ❤️ 和 Kotlin 构建**

</div>
