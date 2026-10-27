# 花生图像模型演示 App

包含 Android 和 OpenHarmony 两个离线演示应用。支持从系统相册选择图片、本地运行模型演示、查看本地历史记录。没有实现相机拍摄或云端服务。

现有手机图片特征及模型尚未经过有效性验证。输出仅为研究演示，不能用于判断食品安全或黄曲霉毒素含量。

## Android

需要 JDK 17、Android SDK 34 和构建工具 34.0.0。在 `peanut_detection_android` 目录运行：

```bash
# Windows
gradlew.bat testDebugUnitTest assembleDebug
# macOS / Linux
./gradlew testDebugUnitTest assembleDebug
```

调试包路径为 `app/build/outputs/apk/debug/app-debug.apk`。首次运行自动化测试需要下载 Robolectric 依赖。

## OpenHarmony

在支持 OpenHarmony API 10 的 DevEco Studio 中打开 `peanut_detection_harmony` 并构建 `assembleHap`。当前输出为未签名 HAP；真机安装需要相应的签名配置。尚未完成真机或完整模拟器验证。
