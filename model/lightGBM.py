import os
import time
import joblib
import numpy as np
import pandas as pd
import cv2
from sklearn.metrics import accuracy_score, recall_score, roc_auc_score, f1_score, confusion_matrix

# ====================== 配置区 ======================
DATA_DIR = "./data/"                 # normal/ moldy/
MODEL_PATH = "nir_lgbm_peanut.pkl"   # 训练好的 LightGBM 模型
SCALER_PATH = "nir_scaler.pkl"       # 训练时保存的 StandardScaler
TEST_CSV = None                      # 若已有特征CSV可指定；None 表示现场从图片提取
# 预处理参数（与训练阶段保持一致；如果单独存在参数文件，可在此覆盖）
ILLUM_KERNEL_SIZE = 35
CLAHE_CLIP_LIMIT = 2.2
CLAHE_TILE_GRID = (8, 8)
SEG_MARGIN = 0.18
SEG_ITER = 4

# 若图片是训练时切分好的测试集，放这里；否则从 DATA_DIR 全量评估
TEST_IMG_DIRS = {
    "normal": os.path.join(DATA_DIR, "normal"),
    "moldy":  os.path.join(DATA_DIR, "moldy"),
}

# ====================== 预处理 ======================
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


# ====================== 分割 ======================
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


# ====================== 特征提取 ======================
from skimage.feature import graycomatrix, graycoprops

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
                        angles=[0, np.pi / 4, np.pi / 2, 3 * np.pi / 4],
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


FEAT_NAMES = (
    [f"gray_{n}" for n in ["mean", "std", "skew", "kurt", "p10", "p50", "p90", "range"]]
    + [f"glcm_{n}" for n in ["contrast", "energy", "homogeneity", "correlation"]]
    + [f"tex_{n}" for n in ["localstd_mean", "localstd_std"]]
    + [f"morph_{n}" for n in ["circularity", "eccentricity"]]
)


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


# ====================== 构建测试集 ======================
def load_testset_from_images():
    X, y, names = [], [], []
    for label, folder in TEST_IMG_DIRS.items():
        lab = 1 if label == "moldy" else 0
        if not os.path.isdir(folder):
            continue
        for f in sorted(os.listdir(folder)):
            if not f.lower().endswith((".png", ".jpg", ".jpeg", ".bmp", ".tif")):
                continue
            try:
                v = extract_one(os.path.join(folder, f))
                if v is None:
                    continue
                X.append(v); y.append(lab); names.append(f)
            except Exception as e:
                print(f"[跳过] {f}: {e}")
    if not X:
        raise ValueError("测试图片为空，请检查 TEST_IMG_DIRS 配置")
    X = np.array(X, dtype=np.float64)
    # 与训练一致：去掉 NaN 行
    keep = ~np.isnan(X).any(axis=1)
    X, y = X[keep], np.array(y)[keep]
    return X, y, [n for n, k in zip(names, keep) if k]


# ====================== 主测试流程 ======================
def main():
    print("==== 加载模型与标准化器 ====")
    model = joblib.load(MODEL_PATH)
    scaler = joblib.load(SCALER_PATH)
    print("加载完成:", type(model).__name__)

    # 构建测试数据
    if TEST_CSV and os.path.exists(TEST_CSV):
        df = pd.read_csv(TEST_CSV)
        X = df[FEAT_NAMES].values
        y = df["label"].values
    else:
        print("==== 从图片提取测试特征 ====")
        t0 = time.perf_counter()
        X, y, _ = load_testset_from_images()
        t_extract = time.perf_counter() - t0
        print(f"样本数: {len(y)}  特征提取耗时: {t_extract:.2f}s")

    # 与训练一致：先 NaN 过滤（若是 CSV 分支）
    if TEST_CSV and os.path.exists(TEST_CSV):
        keep = ~np.isnan(X).any(axis=1)
        X, y = X[keep], y[keep]

    print(f"\n测试集样本总数: {len(y)}  (正常={int((y==0).sum())}, 发霉={int((y==1).sum())})")

    # 推理（不计入 FPS）
    X_s = scaler.transform(X)
    _ = model.predict_proba(X_s[:1])  # warmup

    # ====== FPS 测量：逐张推理（含预处理？分两种口径） ======
    # 口径A：仅模型推理吞吐（不含特征提取），衡量模型部署速度
    n = len(X_s)
    t0 = time.perf_counter()
    for i in range(n):
        model.predict_proba(X_s[i:i + 1])
    t_model = time.perf_counter() - t0
    fps_model = n / t_model if t_model > 0 else float("inf")

    # 口径B：端到端（图片→预处理→分割→特征→推理）
    # 需要在图片分支下才测量，否则跳过
    fps_e2e = None
    if not (TEST_CSV and os.path.exists(TEST_CSV)):
        # 这里复用上面已经提取的 X，但由于特征已缓存，真实端到端应重新跑一遍图片
        # 为公平，这里单独再跑一次图片流
        t0 = time.perf_counter()
        cnt = 0
        for label, folder in TEST_IMG_DIRS.items():
            if not os.path.isdir(folder):
                continue
            for f in sorted(os.listdir(folder)):
                if not f.lower().endswith((".png", ".jpg", ".jpeg", ".bmp", ".tif")):
                    continue
                v = extract_one(os.path.join(folder, f))
                if v is None or np.isnan(v).any():
                    continue
                model.predict_proba(scaler.transform(v.reshape(1, -1)))
                cnt += 1
        t_e2e = time.perf_counter() - t0
        fps_e2e = cnt / t_e2e if t_e2e > 0 else float("inf")

    # ====== 指标 ======
    y_pred = model.predict(X_s)
    y_prob = model.predict_proba(X_s)[:, 1]

    acc = accuracy_score(y, y_pred)
    rec = recall_score(y, y_pred)
    f1 = f1_score(y, y_pred)
    auc = roc_auc_score(y, y_prob) if len(np.unique(y)) > 1 else float("nan")
    cm = confusion_matrix(y, y_pred)

    print("\n==== 测试集结果 ====")
    print(f"Accuracy : {acc:.4f}")
    print(f"Recall   : {rec:.4f}")
    print(f"F1       : {f1:.4f}")
    print(f"AUC      : {auc:.4f}")
    print("混淆矩阵:\n", cm)

    print("\n==== 推理速度 (FPS) ====")
    print(f"模型推理口径 (n={n}): {fps_model:.2f} FPS  (单张耗时 {1000/fps_model:.2f} ms)")
    if fps_e2e is not None:
        print(f"端到端口径 (图片→预测): {fps_e2e:.2f} FPS  (单张耗时 {1000/fps_e2e:.2f} ms)")


if __name__ == "__main__":
    main()