# 基于鸿蒙智能手机的坚果类黄曲霉实时检测系统
杭州电子科技大学新苗计划项目 | 端侧AI + 近红外多光谱 + LightGBM 双模型融合

## 项目简介
本项目探索使用智能手机、近红外采集硬件及端侧 LightGBM 模型进行花生等坚果的污染筛查。当前仓库提供离线模型与应用演示，仍需采集硬件、样本和实验室检测数据完成验证。

## 目录说明
- `model/`：算法训练与端侧模型文件
- `peanut_detection_android/`：Android端完整可编译工程
- `peanut_detection_harmony/`：鸿蒙端完整可编译工程
- `wechat_miniprogram/`：微信小程序前端（**纯图像三分类路线**，与近红外/ LightGBM 路线并行）
- `docs/`：小程序前端实现说明、测试报告与界面截图
- `game_level_frontend.html`：游戏级炫酷演示前端
- `software_copyright/`：软件著作权申请材料
- `patent_utility/`：实用新型专利申请材料

## 当前状态
两端应用目前是研究演示，手机端特征提取与模型训练流程尚未对齐，模型输出不能用于判断食品安全。仓库中暂未提供可复核的独立测试集、实验室标签和设备校准数据，因此性能指标尚待验证。实际食品安全判断应以实验室检测为准。

Android 和鸿蒙历史页均可导出演示记录 JSON。Android 新记录包含模型与标准化参数文件的 SHA-256、处理流程版本和 256×256 灰度像素摘要；Android 旧记录及鸿蒙现有记录的缺失字段保留为空。导出不包含图片 URI、原图或光谱，不能单独用于完整实验复现。鸿蒙导出的系统选择器与文件写入尚未在设备上验证。

## 构建演示应用

- Android：安装 JDK 17、Android SDK 34 和构建工具 34.0.0；在 `peanut_detection_android` 运行 `gradlew.bat assembleDebug`（Windows）或 `./gradlew assembleDebug`。调试 APK 位于 `app/build/outputs/apk/debug/`。
- Android 自动化检查：在同一目录运行 `gradlew.bat testDebugUnitTest`；测试覆盖主界面启动、图片特征向量、模型输出和历史记录。首次运行需要下载 Robolectric 运行时。
- OpenHarmony：使用支持 API 10 的 DevEco Studio 和 SDK 打开 `peanut_detection_harmony`，执行 `assembleHap`。目前生成的 HAP 未签名，需要配置设备签名后才能安装；实际设备兼容性仍需验证。
- OpenHarmony 导出逻辑测试：在 `peanut_detection_harmony` 下使用可解析 `typescript` 包的 Node.js 环境执行 `node --test tests/history-export.test.cjs`。这组宿主机测试通过 TypeScript 转译执行纯逻辑，不替代鸿蒙设备测试。

## 微信小程序（近红外二分类）

`wechat_miniprogram/` 是本项目的移动端前端，配合 `backend_nir/` 的推理服务使用：

- **输入**：近红外单通道图像（640×480，由采集装置成像）
- **模型**：MobileNetV3-Small（1 通道输入）+ 水平翻转 TTA
- **输出**：p(mold) 与二分类结论（正常 / 发霉），阈值 0.6823
- **指标**：测试集准确率 96.8%（157 张），按花生组投票 98.3%（117 组），AUC 0.9948

> 与仓库主体的"近红外多光谱 + LightGBM"共用同一采集装置，但模型与评价口径不同，两组指标不能互相引用。

### 技术要点

- 微信小程序原生（WXML/WXSS/JS），无第三方 UI 库与图表库；主包约 134 KB
- 动态效果全部由 WXSS 实现：全息扫描台（旋转刻度盘 / 射线 / 粒子）、检测中扫描遮罩与四步进度、流光按钮、呼吸光晕、数字滚动、3D 翻转卡
- 端上**阈值 + 灰区**判定：距阈值 ≥15 个百分点判"结果可信"，7.5–15 判"建议复检"，<7.5 判灰区；两个参数可在「关于」页调整
- 结果可生成分享海报（canvas 2d）；档案页支持时间轴分组、筛选、排序、导出 CSV、缩略图持久化
- 25→35 条测试用例（见 `docs/wechat-miniprogram-test.md`）

### 目录

```
wechat_miniprogram/     小程序工程（4 页 + 原生 tabBar 图标）
backend_nir/            推理服务：app_nir.py / peanut_core.py（不含权重）
docs/                   前端实现说明、测试报告、8 张界面截图
```

### 边界（引用指标时请保留）

1. 输出的是**图像可见霉变表型**判断，不是黄曲霉毒素含量；毒素定量需按 GB 2761 用色谱等方法；
2. 参考测试集已被反复查看，交付方亦注明"指标偏乐观"，正式结论应使用新采集的独立批次；
3. 全部样本来自同一套采集装置与相近光照，未做跨设备、跨品种验证；
4. 示例档案中的**时间轴为演示布置**（记录内容真实，时分秒为编排值），趋势图不可当作真实使用频率。


## 公开数据实验

`experiments/` 包含公开花生高光谱表格的独立实验和复现说明。该实验的输入、标签及用途均不同于手机应用，不可用其实验分数宣称手机模型的准确率。
