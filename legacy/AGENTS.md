这原来是一个pc端漫画下载器，现在的目标是改成适用与安卓16的app。
发布构建仍走 github actions：修改完代码后 push 到 github 触发 actions 进行构建。
但为了快速验证调试，允许在本地使用 Android Studio 自带的模拟器和 adb 进行验证：
- 可以本地构建 debug APK（tauri android build）并安装到本地模拟器调试
- 可以用 adb 抓 logcat、截图、uiautomator dump 等手段定位问题
- 不要在本地安装额外的编译/构建工具链（Rust/NDK/Android SDK 已有的可用）
之前安装构建完的 app 会白屏，根因是前端 dist 未打包进 APK，已修复，需要继续验证直到功能正常使用
