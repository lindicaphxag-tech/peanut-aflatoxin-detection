"""
Kaggle级LightGBM花生黄曲霉检测训练流水线
严格和端侧推理逻辑1:1对齐，无任何训练-推理偏差
作者: HDU Peanut Detection Team
"""
import os
import json
import joblib
import numpy as np
import pandas as pd
import cv2
from skimage.feature import graycomatrix, graycoprops
from sklearn.model_selection import StratifiedKFold, GridSearchCV, train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (accuracy_score, precision_score, recall_score, f1_score,
                             roc_auc_score, confusion_matrix, classification_report)
import lightgbm as lgb
import matplotlib.pyplot as plt

# ====================== 配置区（和端侧完全一致） ======================
SEED = 42
DATA_DIR = "./data/"  # 目录结构: data/normal/*.jpg, data/moldy/*.jpg
OUTPUT_DIR = "./output/"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 预处理参数（和端侧lightGBM.py完全对齐）
ILLUM_KERNEL_SIZE = 35
CLAHE_CLIP_LIMIT = 2.2
CLAHE_TILE_GRID = (8, 8)
SEG_MARGIN = 0.18
SEG_ITER = 4

# 特征名称（和端侧严格一致）
FEAT_NAMES = (
    [f"gray_{n}" for n in ["mean", "std", "skew", "kurt", "p10", "p50", "p90", "range"]]
    + [f"glcm_{n}" for n in ["contrast", "energy", "homogeneity", "correlation"]]
    + [f"tex_{n}" for n in ["localstd_mean", "localstd_std"]]
    + [f"morph_{n}" for n in ["circularity", "eccentricity"]]
)

# ====================== 预处理函数（和端侧逐行对齐） ======================
def illumination_correct(gray_img, kernel_size=ILLUM_KERNEL_SIZE):
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_size, kernel_size))
    bg = cv2.morphologyEx(gray_img, cv2.MORPH_OPEN, kernel)
    corrected = cv2.subtract(gray_img, bg)
    corrected = cv2.normalize(corrected, None, 0, 255, cv2.NORM_MINMAX, dtype=cv2.CV_8U)
    return corrected

def load_preprocess(img_path):
    img = cv2.imread(img_path)
    if img is None:
        raise FileNotFoundError(f"无法读取 {img_path}")
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY) if img.ndim == 3 else img
    corr = illumination_correct(gray)
    clahe = cv2.createCLAHE(clipLimit=CLAHE_CLIP_LIMIT, tileGridSize=CLAHE_TILE_GRID)
    enhanced = clahe.apply(corr)
    return gray, enhanced, img

def segment_peanut(enhanced_gray, bgr_ori):
    h, w = enhanced_gray.shape
    m = SEG_MARGIN
    rect = (int(w * m), int(h * m), int(w * (1 - 2 * m)), int(h * (1 - 2 * m)))
    mask = np.full((h, w), cv2.GC_BGD, np.uint8)
    mask[rect[1]:rect[1] + rect[3], rect[0]:rect[0] + rect[2]] = cv2.GC_PR_FGD
    bgd = np.zeros((1, 65), np.float64)
    fgd = np.zeros((1, 65), np.float64)
    cv2.grabCut(bgr_ori, mask, rect, bgd, fgd, iterCount=SEG_ITER, mode=cv2.GC_INIT_WITH_RECT)
    fg = np.where((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)

    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))
    fg = cv2.morphologyEx(fg, cv2.MORPH_CLOSE, k, iterations=2)
    fg = cv2.morphologyEx(fg, cv2.MORPH_OPEN, k, iterations=1)

    cnts, _ = cv2.findContours(fg, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    out = np.zeros_like(fg)
    if not cnts:
        return out
    area_total = h * w
    best, best_area = None, 0
    for c in cnts:
        a = cv2.contourArea(c)
        if 0.05 * area_total < a < 0.70 * area_total and a > best_area:
            best, best_area = c, a
    if best is None:
        best = max(cnts, key=cv2.contourArea)

    if len(best) >= 5:
        try:
            ellipse = cv2.fitEllipse(best)
            cv2.ellipse(out, ellipse, 255, -1)
        except cv2.error:
            cv2.drawContours(out, [best], -1, 255, -1)
    else:
        cv2.drawContours(out, [best], -1, 255, -1)

    out = cv2.erode(out, np.ones((3, 3), np.uint8), iterations=1)
    return out

# ====================== 特征提取（和端侧逐行对齐） ======================
def feat_gray_stats(pixels):
    if len(pixels) < 50:
        return [0.0] * 8
    return [
        float(np.mean(pixels)), float(np.std(pixels)),
        float(pd.Series(pixels).skew()), float(pd.Series(pixels).kurtosis()),
        float(np.percentile(pixels, 10)), float(np.percentile(pixels, 50)),
        float(np.percentile(pixels, 90)),
        float(np.percentile(pixels, 90) - np.percentile(pixels, 10)),
    ]

def feat_glcm(gray_img, mask):
    roi = cv2.bitwise_and(gray_img, gray_img, mask=mask)
    if np.sum(roi) < 10:
        return [0.0] * 4
    roi_f = np.clip(roi.astype(np.float32), 0, 255).astype(np.uint8)
    glcm = graycomatrix(roi_f, distances=[1],
                        angles=[0, np.pi/4, np.pi/2, 3*np.pi/4],
                        levels=256, symmetric=True, normed=True)
    return [
        float(graycoprops(glcm, "contrast").mean()),
        float(graycoprops(glcm, "energy").mean()),
        float(graycoprops(glcm, "homogeneity").mean()),
        float(graycoprops(glcm, "correlation").mean()),
    ]

def feat_texture(gray_img, mask):
    roi = cv2.bitwise_and(gray_img, gray_img, mask=mask)
    if np.sum(roi) < 10:
        return [0.0, 0.0]
    roi_f = roi.astype(np.float32)
    mean = cv2.blur(roi_f, (9, 9))
    sq = cv2.blur(roi_f * roi_f, (9, 9))
    local_std = np.sqrt(np.abs(sq - mean * mean) + 1e-8)
    px = local_std[mask > 0]
    return [float(np.mean(px)), float(np.std(px))]

def feat_morph(mask):
    cnts, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not cnts:
        return [0.0, 0.0]
    c = max(cnts, key=cv2.contourArea)
    area = cv2.contourArea(c)
    peri = cv2.arcLength(c, True)
    circ = 4 * np.pi * area / (peri ** 2) if peri > 1e-6 else 0
    ecc = 0.0
    if len(c) >= 5:
        try:
            _, axes, _ = cv2.fitEllipse(c)
            ma, mi = axes
            if ma > 1e-6:
                ratio = np.clip(mi / ma, 0.0, 1.0)
                ecc = float(np.sqrt(1 - ratio ** 2 + 1e-8))
        except cv2.error:
            pass
    return [float(circ), ecc]

def extract_one(img_path):
    gray, enhanced, bgr = load_preprocess(img_path)
    mask = segment_peanut(enhanced, bgr)
    if np.sum(mask) < 200:
        return None
    pixels = gray[mask > 0]
    vec = feat_gray_stats(pixels) + feat_glcm(gray, mask) + feat_texture(gray, mask) + feat_morph(mask)
    vec_arr = np.array(vec, dtype=np.float64)
    vec_arr[np.isinf(vec_arr)] = np.nan
    return vec_arr

# ====================== 数据加载与5折交叉验证 ======================
def build_dataset():
    X, y = [], []
    for label, folder_name in [("normal", "normal"), ("moldy", "moldy")]:
        folder = os.path.join(DATA_DIR, folder_name)
        lab = 1 if label == "moldy" else 0
        if not os.path.isdir(folder):
            continue
        for f in sorted(os.listdir(folder)):
            if not f.lower().endswith((".png", ".jpg", ".jpeg", ".bmp")):
                continue
            try:
                v = extract_one(os.path.join(folder, f))
                if v is not None:
                    X.append(v)
                    y.append(lab)
            except Exception as e:
                print(f"跳过 {f}: {e}")
    X = np.array(X)
    y = np.array(y)
    keep = ~np.isnan(X).any(axis=1)
    return X[keep], y[keep]

if __name__ == "__main__":
    np.random.seed(SEED)
    print("===== Step1: 构建数据集 =====")
    X, y = build_dataset()
    print(f"总样本数: {len(y)}, 正常: {(y==0).sum()}, 霉变: {(y==1).sum()}")

    # 划分训练/测试集 9:1
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.1, random_state=SEED, stratify=y
    )

    # 标准化
    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)

    print("\n===== Step2: 5折分层交叉验证+网格搜索调参 =====")
    param_grid = {
        "n_estimators": [100, 200, 300],
        "max_depth": [3, 5, 7],
        "learning_rate": [0.05, 0.1],
        "num_leaves": [7, 15, 31],
        "min_child_samples": [5, 10]
    }
    lgb_clf = lgb.LGBMClassifier(random_state=SEED, objective="binary", verbosity=-1)
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)
    grid = GridSearchCV(lgb_clf, param_grid, cv=cv, scoring="roc_auc", n_jobs=-1, verbose=1)
    grid.fit(X_train_s, y_train)
    best_model = grid.best_estimator_
    print(f"最优参数: {grid.best_params_}")
    print(f"5折CV最优AUC: {grid.best_score_:.4f}")

    print("\n===== Step3: 测试集评估 =====")
    y_pred = best_model.predict(X_test_s)
    y_prob = best_model.predict_proba(X_test_s)[:, 1]
    print(classification_report(y_test, y_pred, target_names=["正常", "霉变"]))
    print(f"Accuracy: {accuracy_score(y_test, y_pred):.4f}")
    print(f"Precision: {precision_score(y_test, y_pred):.4f}")
    print(f"Recall: {recall_score(y_test, y_pred):.4f}")
    print(f"F1: {f1_score(y_test, y_pred):.4f}")
    print(f"AUC: {roc_auc_score(y_test, y_prob):.4f}")
    print(f"混淆矩阵:\n{confusion_matrix(y_test, y_pred)}")

    print("\n===== Step4: 特征重要性分析 =====")
    importance = pd.DataFrame({
        "feature": FEAT_NAMES,
        "importance": best_model.feature_importances_
    }).sort_values("importance", ascending=False)
    print(importance)
    importance.to_csv(os.path.join(OUTPUT_DIR, "feature_importance.csv"), index=False)

    print("\n===== Step5: 导出端侧部署文件 =====")
    # 导出scaler
    scaler_params = {
        "mean_": scaler.mean_.tolist(),
        "scale_": scaler.scale_.tolist(),
        "n_features_in_": int(scaler.n_features_in_)
    }
    with open(os.path.join(OUTPUT_DIR, "scaler_params.json"), "w") as f:
        json.dump(scaler_params, f, indent=2)
    # 导出LightGBM模型为JSON
    booster = best_model.booster_
    model_json = booster.dump_model()
    with open(os.path.join(OUTPUT_DIR, "lgbm_model.json"), "w") as f:
        json.dump(model_json, f, indent=2)
    # 保存原始pkl模型
    joblib.dump(best_model, os.path.join(OUTPUT_DIR, "nir_lgbm_peanut.pkl"))
    joblib.dump(scaler, os.path.join(OUTPUT_DIR, "nir_scaler.pkl"))

    print(f"所有文件已导出到 {OUTPUT_DIR}")
