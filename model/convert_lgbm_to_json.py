import joblib
import json
import numpy as np

# 加载模型和标准化器
model = joblib.load("nir_lgbm_peanut.pkl")
scaler = joblib.load("nir_scaler.pkl")

# 导出scaler参数
scaler_params = {
    "mean_": scaler.mean_.tolist() if hasattr(scaler.mean_, 'tolist') else list(scaler.mean_),
    "scale_": scaler.scale_.tolist() if hasattr(scaler.scale_, 'tolist') else list(scaler.scale_),
    "var_": scaler.var_.tolist() if hasattr(scaler.var_, 'tolist') else list(scaler.var_) if hasattr(scaler, 'var_') else None,
    "n_features_in_": int(scaler.n_features_in_)
}

# 导出LightGBM模型为JSON格式（端侧可直接遍历树结构推理）
# LightGBMBooster可以导出为model_dump
booster = model.booster_
model_json = booster.dump_model()

# 保存
with open("scaler_params.json", "w", encoding="utf-8") as f:
    json.dump(scaler_params, f, indent=2, ensure_ascii=False)

with open("lgbm_model.json", "w", encoding="utf-8") as f:
    json.dump(model_json, f, indent=2, ensure_ascii=False)

print("模型转换完成：")
print(f"- scaler参数：{len(scaler.mean_)} 维特征")
print(f"- 树的数量：{model_json['num_trees']}")
print(f"- 每棵树叶子节点数：{[t['num_leaves'] for t in model_json['tree_info'][:5]]}...")
print("输出文件：scaler_params.json, lgbm_model.json")
