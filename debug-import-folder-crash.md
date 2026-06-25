# Debug Session: import-folder-crash

**状态**: [OPEN]
**开始时间**: 2026-06-25
**问题描述**: 导入包含歌曲的（中文）命名文件夹后 App 立即闪退。已完成多轮代码修复但仍复现。

## 复现步骤
1. 启动 App
2. 点击导入文件夹按钮
3. 选择包含歌曲的命名文件夹（中文名）
4. App 闪退

## 假设列表（待证据验证）

| # | 假设 | 验证点 | 状态 |
|---|------|--------|------|
| H1 | `DocumentFile.listFiles()` 在某些 provider 上返回 null 导致 NPE | 检查 logcat 是否有 NullPointerException | PENDING |
| H2 | `MediaMetadataRetriever.setDataSource` 对某些 URI 触发原生崩溃（SIGSEGV）绕过 Java try/catch | logcat 中出现 native crash signal | PENDING |
| H3 | 协程未捕获异常导致进程崩溃（缺少 CoroutineExceptionHandler） | logcat 中出现 Coroutine 内未捕获异常 | PENDING |
| H4 | UI 层在显示大量带封面的 Tile 时 OOM 或 Coil 异常 | logcat 出现 OutOfMemoryError | PENDING |
| H5 | `saveCoverArt` 写入缓存失败或 `Uri.fromFile` 在特定路径下抛异常 | 插桩日志显示在 saveCoverArt 处中断 | PENDING |

## 证据收集计划
- 主：应用内文件日志 `debug.log`（无 USB 方案）
- 辅：logcat 输出（备用）

## 日志文件位置
`/storage/emulated/0/Android/data/com.localbeats/files/debug.log`

## 进度记录
- [2026-06-25] 初始化调试会话，列出 5 个假设
- [2026-06-25] 完成 4 个文件插桩：
  - LocalBeatsApp：全局未捕获异常处理器 + Dbg 文件日志工具
  - MainActivity：folderPicker、onCreate、crash-safe 路径
  - MusicViewModel：loadMusicFromFolder 各阶段 + CoroutineExceptionHandler
  - MusicRepository：扫描循环、每个文件、setDataSource、buildTrack
- [2026-06-25] 等待用户构建安装 APK、复现崩溃、获取 debug.log
