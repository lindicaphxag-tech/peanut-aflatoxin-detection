# -*- coding: utf-8 -*-
"""近红外单通道二分类推理服务（主线）。

模型：MobileNetV3-Small（1 通道），权重 peanut_gray2.pt，阈值 0.6823
预处理（与训练严格一致）：
  读图 → EXIF 转正 → 转 8 位灰度
  → 以水平居中、垂直 0.23×H 为起点裁 340×340（图小则取短边）
  → LANCZOS 缩放到 224×224
  → (x/255 - 0.449) / 0.226

接口：
  POST /api/detect   multipart 字段 image，或 JSON {"base64": "..."}
  GET  /api/health
"""
import base64
import os
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

from flask import Flask, request, jsonify
from flask_cors import CORS
from PIL import Image, ImageOps
import numpy as np
import torch

import peanut_core as core

app = Flask(__name__)
CORS(app)
try:
    app.json.ensure_ascii = False
except Exception:
    pass
app.config['MAX_CONTENT_LENGTH'] = 32 * 1024 * 1024

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
WEIGHTS = os.path.join(BASE_DIR, 'weights', 'peanut_gray2.pt')

_state = {'model': None, 'threshold': 0.6823}


def load_model():
    if _state['model'] is not None:
        return _state['model']
    pack = torch.load(WEIGHTS, map_location='cpu')
    model = core.Model(pretrained=False)
    sd = pack.get('model') if isinstance(pack, dict) else pack
    if isinstance(pack, dict) and 'state_dict' in pack:
        sd = pack['state_dict']
    model.load_state_dict(sd)
    model.eval()
    _state['model'] = model
    if isinstance(pack, dict) and 'threshold' in pack:
        _state['threshold'] = float(pack['threshold'])
    print(f'近红外模型已加载，阈值 {_state["threshold"]:.4f}', flush=True)
    return model


def infer(path):
    """单张推理，返回统一结构"""
    t0 = time.time()
    model = load_model()
    with Image.open(path) as src:
        img = ImageOps.exif_transpose(src).convert('L')
    x = core.to_tensor(img)              # 已含裁剪/缩放/归一化
    with torch.no_grad():
        # 与训练/验收脚本一致：原图 + 水平翻转 的 TTA 平均
        inp = x[None]
        p0 = torch.softmax(model(inp), dim=1)[:, 1]
        p1 = torch.softmax(model(torch.flip(inp, [3])), dim=1)[:, 1]
        p_mold = float(((p0 + p1) / 2).item())
    probs = np.array([1.0 - p_mold, p_mold], dtype=np.float64)
    thr = _state['threshold']
    moldy = p_mold >= thr
    return {
        'route': 'nir',
        'result': '发霉' if moldy else '正常',
        'p_mold': round(p_mold, 4),
        'confidence': round((p_mold if moldy else 1 - p_mold) * 100, 1),
        'threshold': thr,
        'normal_prob': round(float(probs[0]) * 100, 1),
        'moldy_prob': round(p_mold * 100, 1),
        'class_index': 1 if moldy else 0,
        'elapsed_ms': int((time.time() - t0) * 1000),
    }


def _from_request():
    if 'image' in request.files:
        fd, tmp = tempfile.mkstemp(suffix='.jpg')
        os.close(fd)
        try:
            request.files['image'].save(tmp)
            return infer(tmp), None
        finally:
            try:
                os.unlink(tmp)
            except OSError:
                pass
    if request.is_json and isinstance(request.json, dict) and 'base64' in request.json:
        raw = request.json['base64']
        if raw.strip().startswith('data:') and ',' in raw:
            raw = raw.split(',', 1)[1]
        fd, tmp = tempfile.mkstemp(suffix='.jpg')
        os.close(fd)
        try:
            with open(tmp, 'wb') as f:
                f.write(base64.b64decode(raw))
            return infer(tmp), None
        finally:
            try:
                os.unlink(tmp)
            except OSError:
                pass
    return None, (jsonify({'error': '请上传图片（字段 image）或提供 base64'}), 400)


@app.route('/api/detect', methods=['POST'])
def detect():
    try:
        result, err = _from_request()
        if err:
            return err
        return jsonify(result)
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500


@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'ok',
        'route': 'nir',
        'model': 'MobileNetV3-Small (1ch)',
        'classes': ['正常', '发霉'],
        'threshold': _state['threshold'],
    })


if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else int(os.environ.get('PORT', 5001))
    pack = torch.load(WEIGHTS, map_location='cpu')
    if isinstance(pack, dict) and 'threshold' in pack:
        _state['threshold'] = float(pack['threshold'])
    load_model()
    print(f'近红外服务已启动：http://127.0.0.1:{port}', flush=True)
    app.run(host='0.0.0.0', port=port, debug=False)
