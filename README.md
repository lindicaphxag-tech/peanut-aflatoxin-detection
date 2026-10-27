# 基于鸿蒙智能手机的坚果类黄曲霉实时检测系统
杭州电子科技大学新苗计划项目 | 端侧AI + 近红外多光谱 + LightGBM 双模型融合

## 项目简介
本项目探索使用智能手机、近红外采集硬件及端侧 LightGBM 模型进行花生等坚果的污染筛查。当前仓库提供离线模型与应用演示，仍需采集硬件、样本和实验室检测数据完成验证。

## 目录说明
- `model/`：算法训练与端侧模型文件
- `peanut_detection_android/`：Android端完整可编译工程
- `peanut_detection_harmony/`：鸿蒙端完整可编译工程
- `game_level_frontend.html`：游戏级炫酷演示前端
- `software_copyright/`：软件著作权申请材料
- `patent_utility/`：实用新型专利申请材料

## 当前状态
两端应用目前是研究演示，手机端特征提取与模型训练流程尚未对齐，模型输出不能用于判断食品安全。仓库中暂未提供可复核的独立测试集、实验室标签和设备校准数据，因此性能指标尚待验证。实际食品安全判断应以实验室检测为准。

## 构建演示应用

- Android：安装 JDK 17、Android SDK 34 和构建工具 34.0.0；在 `peanut_detection_android` 运行 `gradlew.bat assembleDebug`（Windows）或 `./gradlew assembleDebug`。调试 APK 位于 `app/build/outputs/apk/debug/`。
- Android 自动化检查：在同一目录运行 `gradlew.bat testDebugUnitTest`；测试覆盖主界面启动、图片特征向量、模型输出和历史记录。首次运行需要下载 Robolectric 运行时。
- OpenHarmony：使用支持 API 10 的 DevEco Studio 和 SDK 打开 `peanut_detection_harmony`，执行 `assembleHap`。目前生成的 HAP 未签名，需要配置设备签名后才能安装；实际设备兼容性仍需验证。

## 公开数据实验

`experiments/` 包含公开花生高光谱表格的独立实验和复现说明。该实验的输入、标签及用途均不同于手机应用，不可用其实验分数宣称手机模型的准确率。
