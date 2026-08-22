# 项目说明

这是一个禁漫天堂(JM/18comic)漫画下载器的 Android native 应用（Kotlin + Jetpack Compose）。
核心功能：浏览（搜索/收藏夹/每周必看/本地库存）与下载，另含本地阅读器和 CBZ 导出。

原 PC 版（Tauri/Rust + Vue）代码已整体移至 `legacy/` 目录，仅供参考移植算法：
- `legacy/src-tauri/src/jm_client.rs`：JM API 协议（token 签名 / AES-256-ECB 解密）
- `legacy/src-tauri/src/download_manager.rs`：下载状态机 + 图片反切片拼接算法
- `legacy/src-tauri/src/types/`：Comic/ChapterInfo 等元数据 JSON 磁盘格式

## 本地开发与验证
- 构建：`./gradlew assembleDebug`（需 Android SDK，JAVA_HOME 指向 Android Studio 自带 JBR）
- 本地模拟器（Pixel_8 AVD，Android 36）：
  - `adb install app/build/outputs/apk/debug/app-debug.apk`
  - 用 adb 抓 logcat、截图、uiautomator dump 定位问题
- 单测：`./gradlew testDebugUnitTest`（重点覆盖 AES 解密、反切片、dirFmt、元数据兼容）
- 发布构建走 GitHub Actions 的 `.github/workflows/publish.yml`，push 触发

## 存储与权限
- 默认下载目录：`/storage/emulated/0/Download/comics`（需「所有文件访问」权限，
  原生代码可直接跳转 `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 授权页）
- 目录格式、`元数据.json`/`章节元数据.json`/`cover.jpg` 与旧版磁盘格式保持一致，兼容已有下载数据
- 已下载状态用落盘索引（app 私有目录 `download_index.json`）持久化：启动读入 + 目录核对，
  下载完成/删除时增量更新，O(1) 查询不卡顿
