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
- [2026-06-25] 完成 4 个文件插桩
- [2026-06-25] 获取到 debug.log，根因确认：

## 根因确认
日志显示扫描流程完全正常（root exists=true isDir=true），随后主线程在 `CircularProgressIndicator` 渲染时崩溃：
```
java.lang.NoSuchMethodError: No virtual method at(...) in KeyframesSpec$KeyframesSpecConfig
at androidx.compose.material3.ProgressIndicatorKt$CircularProgressIndicator$endAngle$1.invoke(ProgressIndicator.kt:371)
at com.localbeats.MainActivityKt.MusicApp(MainActivity.kt:231)
```

**根因**：Compose BOM `2024.01.00` 解析出的 `animation-core` 与 `material3` 版本不匹配。Material3 1.2+ 的 `CircularProgressIndicator` 新签名调用了 `KeyframesSpecConfig.at()`，但打包进 APK 的 animation-core 版本里没有该方法。

**假设验证**：
- H1-H5 全部否定（与中文名、文件扫描、协程、UI OOM、saveCoverArt 均无关）
- 真实根因：依赖版本冲突（BOM 与 material3 新 API 不匹配）

**症状解释**：
- 中文文件夹有歌曲 → isLoading=true 触发 CircularProgressIndicator 渲染 → 崩溃
- 空文件夹 → 扫描快但仍短暂进入 isLoading=true → 崩溃
- 后续每次启动 → 已保存 URI 直接走加载流程 → 又触发崩溃

## 最小修复
`app/build.gradle.kts`：Compose BOM 升级 `2024.01.00` → `2024.02.00`
- material3 1.1.2 → 1.2.0
- animation-core 1.5.x → 1.6.1
- 版本匹配，NoSuchMethodError 消失

等待用户验证。
