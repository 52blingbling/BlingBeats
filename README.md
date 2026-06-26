<div align="center">

# 🎵 LocalBeats

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

### 🎨 磁贴墙（竖屏）
- **Windows 风格磁贴布局** — 多种尺寸磁贴（1×1、2×1、1×2、2×2）无缝紧密拼接
- **自由拖动平移** — 整个磁贴墙支持上下左右自由拖动，含边界弹性回弹
- **长按拖拽排序** — 长按磁贴进入拖动模式，松手后与目标磁贴交换位置
- **专辑封面展示** — 自动提取音频文件内嵌封面，无封面时显示渐变色彩占位
- **歌曲标题开关** — 设置菜单可切换磁贴上歌曲标题的显示/隐藏

### 🎠 3D 专辑轮播（横屏）
- **3D 透视旋转** — 左右邻居专辑带 rotationY 3D 倾斜效果，中心专辑正对显示
- **无限循环滑动** — 采用虚拟页实现近似无限循环切换
- **惯性吸附动画** — 自定义 fling 行为，快速滑动后自然减速吸附到中心
- **滑动即播放** — 滑动停止后自动切换到对应曲目

### 🎤 歌词显示
- **嵌入式歌词提取** — 支持从 MP3（ID3 USLT）、M4A（iTunes ©lyr）、FLAC、OGG 等格式提取
- **LRC 时间同步** — 支持 `[mm:ss.xx]` 格式歌词逐行同步高亮
- **纯文本滚动** — 无时间戳的歌词以跑马灯方式滚动展示
- **多策略兼容** — MediaMetadataRetriever → jaudiotagger → 反射遍历标签字段，层层兜底

### 🎵 播放器
- **ExoPlayer (Media3)** 驱动，稳定高效
- **前台服务** — 后台持续播放，系统通知栏控制
- **自动下一首** — 播放完毕自动切换，支持错误跳过（连续错误保护）
- **胶囊播放栏** — Apple 风格液态玻璃质感，半透明渐变 + 高光边缘

### 📁 文件管理
- **SAF 文件夹选择** — 通过系统文件选择器授权，支持持久化 URI 权限
- **递归扫描** — 自动遍历所选文件夹及子文件夹
- **崩溃恢复** — 加载过程崩溃后自动清除文件夹记忆，避免启动循环崩溃
- **重新扫描** — 随时手动触发重新扫描，刷新歌词等元数据

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

## 🏗️ 项目架构

```
LocalBeats/
├── app/src/main/java/com/localbeats/
│   ├── LocalBeatsApp.kt              # Application 入口
│   ├── MainActivity.kt               # 主 Activity + 导入页 + 路由
│   ├── data/
│   │   ├── model/
│   │   │   └── MusicTrack.kt         # 音轨数据模型
│   │   ├── lyrics/
│   │   │   ├── LyricsExtractor.kt    # 歌词提取（jaudiotagger）
│   │   │   └── LyricsParser.kt       # LRC 歌词解析 + 时间同步
│   │   ├── player/
│   │   │   ├── MusicPlayer.kt        # ExoPlayer 封装
│   │   │   └── MusicPlaybackService.kt  # 前台播放服务
│   │   └── repository/
│   │       └── MusicRepository.kt    # 音乐文件扫描 + 元数据提取
│   ├── viewmodel/
│   │   └── MusicViewModel.kt         # UI 状态管理
│   └── ui/
│       ├── theme/
│       │   └── Theme.kt              # Material3 暗色主题
│       ├── screens/
│       │   ├── TileWallScreen.kt     # 竖屏磁贴墙
│       │   └── CarouselScreen.kt     # 横屏 3D 轮播
│       └── components/
│           ├── PlayerBar.kt          # 胶囊播放栏 + 歌词
│           ├── CarouselPager.kt      # 轮播子项
│           ├── MusicColors.kt        # 调色板
│           └── ReflectionEffect.kt   # 反射特效
```

### 技术栈

| 层级 | 技术 |
|------|------|
| UI 框架 | Jetpack Compose + Material3 |
| 播放引擎 | AndroidX Media3 ExoPlayer 1.2.1 |
| 图片加载 | Coil Compose 2.5.0 |
| 歌词标签 | jaudiotagger 2.2.3 (AdrienPoupa fork) |
| 文件访问 | Storage Access Framework (SAF) + DocumentFile |
| 架构模式 | MVVM (ViewModel + StateFlow) |
| 最低 API | Android 8.0 (API 26) |
| 目标 API | Android 14 (API 34) |
| 构建工具 | Gradle 8.2.2 + Kotlin DSL |

---

## 🚀 快速开始

### 环境要求

- **Android Studio** Hedgehog (2023.1.1) 或更高版本
- **JDK 17**
- **Android SDK 34**

### 构建与运行

```bash
# 1. 克隆仓库
git clone https://github.com/52blingbling/LocalBeats.git
cd LocalBeats

# 2. 构建 Debug APK
./gradlew assembleDebug

# 3. 安装到设备
./gradlew installDebug
```

或直接在 Android Studio 中打开项目，点击 ▶️ 运行。

### 使用流程

1. 启动应用，点击 **「选择音乐文件夹」** 按钮
2. 在系统文件选择器中选择包含音乐文件的文件夹
3. 应用自动扫描文件夹（含子文件夹）中的所有音频文件
4. **竖屏** → 磁贴墙浏览，点击磁贴播放
5. **横屏** → 3D 专辑轮播，滑动切换曲目
6. 底部胶囊播放栏实时显示歌词

---

## 🔑 权限说明

| 权限 | 用途 |
|------|------|
| `READ_EXTERNAL_STORAGE` | 读取外部存储的音乐文件（Android 12 及以下） |
| `READ_MEDIA_AUDIO` | 读取音频文件（Android 13+） |
| `FOREGROUND_SERVICE` | 后台播放时保持前台服务 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 媒体播放类型的前台服务声明 |

---

## 🎨 设计亮点

- **Edge-to-Edge** — 全屏沉浸式，内容延伸至状态栏和导航栏下方
- **暗色主题** — 以 `#0D0D0D` 为基底的深色 UI，配合紫色 (`#BB86FC`) 和青色 (`#03DAC6`) 点缀
- **液态玻璃播放栏** — 半透明渐变背景 + 顶部高光线模拟玻璃边缘反光
- **平滑动画** — Compose 动画 API 驱动的入场、切换、脉冲等微交互
- **自适应布局** — 根据屏幕方向自动切换竖屏磁贴墙 / 横屏轮播

---

## 📄 开源协议

本项目使用 [MIT License](LICENSE) 开源。

---

<div align="center">

**用 ❤️ 和 Kotlin 构建**

</div>
