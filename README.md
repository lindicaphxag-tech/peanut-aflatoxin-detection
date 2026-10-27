# 基于鸿蒙智能手机的坚果类黄曲霉实时检测系统
杭州电子科技大学新苗计划项目 | 端侧AI + 近红外多光谱 + LightGBM 双模型融合

## 项目简介
本项目将普通消费级智能手机改造为低成本近红外多光谱检测终端，通过定制三波段（930nm/970nm/1040nm）滤光片手机壳，结合端侧LightGBM轻量化模型，实现花生等坚果黄曲霉污染的现场实时筛查。检测无需联网，单样本端到端耗时<0.6秒，总体准确率87%+，早期隐性霉变召回率72%+，硬件总成本控制在200元以内，仅为传统便携式光谱仪价格的1%。

## 核心性能指标
| 指标 | 数值 | 说明 |
|---|---|---|
| 单张推理耗时 | 0.9ms | LightGBM纯端侧推理，无网络依赖 |
| 端到端总耗时 | ~500ms | 含图像预处理、分割、特征提取、推理 |
| 总体准确率 | 87.4% | 独立测试集结果 |
| 早期隐性霉变召回率 | 72.5% | 肉眼不可见的早期霉变识别率 |
| 模型体积 | <1MB | 端侧离线部署 |
| 检测精度AUC | >0.9 | 二分类任务综合性能 |

## 目录结构
```
peanut_aflatoxin_detection/
├── model/                          # 算法与模型目录
│   ├── train_kaggle_level.py       # Kaggle级完整训练流水线（5折CV+网格搜索+特征重要性）
│   ├── lightGBM.py                 # 推理逻辑（端侧训练1:1对齐）
│   ├── benchmark_inference.py      # 端侧性能基准测试脚本
│   ├── convert_lgbm_to_json.py     # 端侧模型格式转换脚本
│   ├── nir_lgbm_peanut.pkl        # 训练好的LightGBM模型
│   ├── nir_scaler.pkl              # 特征标准化器
│   ├── lgbm_model.json             # 端侧可直接解析的JSON格式模型
│   └── scaler_params.json          # 端侧标准化参数
├── peanut_detection_android/       # Android端完整可编译工程
│   ├── app/src/main/
│   │   ├── java/com/hdu/peanutaflatoxin/
│   │   │   ├── ui/                 # MainActivity/HistoryActivity
│   │   │   ├── ml/                 # LgbmInference纯Kotlin推理引擎
│   │   │   ├── db/                 # SQLite历史记录数据库
│   │   │   └── util/                # 特征提取工具类
│   │   ├── assets/                 # 端侧模型文件
│   │   ├── res/layout/             # 布局文件
│   │   └── AndroidManifest.xml
│   └── build.gradle                # Gradle配置
├── peanut_detection_harmony/      # HarmonyOS端完整可编译工程
│   ├── entry/src/main/
│   │   ├── ets/
│   │   │   ├── pages/               # Index/History页面
│   │   │   ├── engine/              # LgbmInference纯ArkTS推理引擎
│   │   │   └── db/                  # RDB历史记录数据库
│   │   └── module.json5            # 权限与路由配置
├── patent_utility/                  # 实用新型专利申请文件
└── software_copyright/              # 软件著作权申请材料
```

## 快速开始
### 1. 算法复现（Python端）
```bash
cd model
pip install -r requirements.txt
# 把数据集放到data/normal和data/moldy目录
python train_kaggle_level.py
```
运行后自动完成：数据加载 → 5折分层交叉验证 → 网格搜索调参 → 测试集评估 → 特征重要性分析 → 端侧部署文件导出。

### 2. Android端编译
1.  用Android Studio Hedgehog及以上版本打开 `peanut_detection_android` 目录
2.  等待Gradle同步完成
3.  连接Android 8.0+手机，直接点击运行即可安装

### 3. HarmonyOS端编译
1.  用DevEco Studio 5.0及以上版本打开 `peanut_detection_harmony` 目录
2.  等待工程同步完成
3.  连接HarmonyOS 4.0+华为/荣耀手机，直接点击运行即可安装

## 技术亮点
1.  **训练推理严格对齐**：端侧代码和训练脚本逐行对齐预处理、分割、特征提取逻辑，无任何训练-推理偏差
2.  **纯端侧推理零依赖**：LightGBM模型导出为JSON后，用纯ArkTS/Kotlin遍历树结构完成推理，无需引入任何原生推理库，APK体积增加<1MB
3.  **离线隐私保护**：所有检测数据本地存储，不需要上传云端，完全保护用户隐私
4.  **硬件低成本**：定制手机壳+补光模组总成本<200元，适合基层商户和普通家庭普及

## 项目成果
-  软件著作权：《坚果类黄曲霉近红外智能检测系统 V1.0》申请材料已完成
-  实用新型专利：《一种集成三波段近红外滤光片与补光模组的手机检测壳》申请文件已完成
-  双端App：HarmonyOS + Android双平台完整工程，可直接编译打包上架
