"""Core pieces of the single-channel peanut classifier (normal=0, mold=1).

Everything that both training and testing need lives here:

    * preprocessing constants and the centre-ROI crop
    * the 1-channel MobileNetV3-Small model and its deployment wrapper
    * a self-describing checkpoint format (weights + config + threshold)
    * prediction helpers and metric functions

No pandas and no training-loop code: ``peanut_test.py`` can be copied to a
machine that only has torch + numpy + Pillow and still work.
"""
from __future__ import annotations

import hashlib
from datetime import datetime
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps
import torch
from torch import nn
from torch.utils.data import DataLoader, Dataset
from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small
from torchvision.transforms import v2

# --------------------------------------------------------------------------- #
# constants
# --------------------------------------------------------------------------- #
SEED = 20260927
SIZE = 224                 # network input
CROP = 340                 # square ROI inside the 640x480 frame
MEAN, STD = 0.449, 0.226   # ImageNet greyscale mean/std (the backbone was pre-trained in colour)
EXT = {'.jpg', '.jpeg', '.png', '.tif', '.tiff', '.bmp'}

CLASSES = ('normal', 'mold')
LABEL_NAMES = {0: 'normal', 1: 'mold'}
CHINESE_NAMES = {0: '正常', 1: '发霉'}

WEIGHT_FORMAT = 'peanut-single-channel/1'
ARCH = 'mobilenet_v3_small_1ch'


def seed_all(seed: int = SEED) -> None:
    import random

    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False


def resolve_device(name: str | None = 'auto') -> torch.device:
    if name in (None, '', 'auto'):
        return torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    return torch.device(name)


def read_text_auto(path: Path | str) -> str:
    """Read a small text file written by any of the scripts / Windows locales."""
    raw = Path(path).read_bytes()
    for encoding in ('utf-8-sig', 'utf-8', 'gbk', 'cp936', 'latin-1'):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return raw.decode('utf-8', errors='replace')



# --------------------------------------------------------------------------- #
# preprocessing
# --------------------------------------------------------------------------- #
def crop_image(image: Image.Image, c: int = CROP) -> Image.Image:
    """Square ROI around the holder.

    The peanut sits slightly below the image centre, so the crop is biased
    downwards by 23% of the height.
    """
    w, h = image.size
    c = min(c, w, h)
    left = (w - c) // 2
    top = min(h - c, max(0, int(round(h * .23))))
    return image.crop((left, top, left + c, top + c))


def load_grey(path: Path | str) -> Image.Image:
    """EXIF-corrected 8-bit greyscale, exactly as used for training."""
    with Image.open(path) as source:
        return ImageOps.exif_transpose(source).convert('L')


def to_tensor(image: Image.Image, crop: int | None = CROP,
              size: int = SIZE, mean: float = MEAN, std: float = STD) -> torch.Tensor:
    if crop:
        image = crop_image(image, crop)
    image = image.resize((size, size), Image.Resampling.LANCZOS)
    x = np.asarray(image, dtype=np.float32) / 255.0
    x = (x - mean) / std
    return torch.from_numpy(x[None].copy())


# --------------------------------------------------------------------------- #
# dataset
# --------------------------------------------------------------------------- #
class PeanutDataset(Dataset):
    """Rows are dict-like records with filename / label / group_id / stage.

    ``root`` is the dataset root; ``filename`` is relative to it (posix style).
    """

    def __init__(self, rows, root, train: bool = False, crop: int = CROP,
                 size: int = SIZE, mean: float = MEAN, std: float = STD):
        self.rows = [dict(r) for r in rows]
        self.root = Path(root)
        self.train = train
        self.crop = crop
        self.size = size
        self.mean = mean
        self.std = std
        self.aug = v2.Compose([
            v2.RandomAffine(degrees=12, translate=(0.06, 0.06), scale=(0.9, 1.12),
                            shear=4, fill=120),
            v2.RandomHorizontalFlip(),
            v2.RandomApply([v2.GaussianBlur(3, sigma=(0.1, 1.0))], p=0.2),
        ])

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, i: int):
        import random

        row = self.rows[i]
        image = load_grey(self.root / row['filename'])
        if self.train:
            from PIL import ImageEnhance

            if random.random() < .5:
                image = ImageEnhance.Brightness(image).enhance(random.uniform(.85, 1.15))
            if random.random() < .5:
                image = ImageEnhance.Contrast(image).enhance(random.uniform(.85, 1.15))
            image = self.aug(image)
        x = to_tensor(image, self.crop, self.size, self.mean, self.std)
        stage = str(row.get('stage') or '')
        return (x, int(row['label']), int(stage == 'early'),
                str(row['filename']), str(row.get('group_id') or ''))


# --------------------------------------------------------------------------- #
# model
# --------------------------------------------------------------------------- #
class Model(nn.Module):
    """MobileNetV3-Small with a 1-channel stem, 2-way head."""

    def __init__(self, pretrained: bool = False):
        super().__init__()
        base = mobilenet_v3_small(
            weights=MobileNet_V3_Small_Weights.IMAGENET1K_V1 if pretrained else None)
        first = base.features[0][0]
        mono = nn.Conv2d(1, first.out_channels, first.kernel_size, stride=first.stride,
                         padding=first.padding, dilation=first.dilation, bias=False)
        with torch.no_grad():
            mono.weight.copy_(first.weight.sum(dim=1, keepdim=True))
        base.features[0][0] = mono
        self.features = base.features
        self.pool = nn.AdaptiveAvgPool2d(1)
        self.dropout = nn.Dropout(.3)
        self.fc = nn.Linear(base.classifier[0].in_features, 2)

    def forward(self, x):
        return self.fc(self.dropout(torch.flatten(self.pool(self.features(x)), 1)))


class Deploy(nn.Module):
    """Raw 0..255 grey in, probabilities out; normalisation is inside the graph."""

    def __init__(self, model: Model, mean: float = MEAN, std: float = STD):
        super().__init__()
        self.model = model
        self.mean = mean
        self.std = std

    def forward(self, x):
        x = (x / 255.0 - self.mean) / self.std
        return torch.softmax(self.model(x), dim=1)


class EMA:
    def __init__(self, model: nn.Module, decay: float = 0.999):
        self.decay = decay
        self.shadow = {k: v.detach().clone() for k, v in model.state_dict().items()}

    @torch.no_grad()
    def update(self, model: nn.Module) -> None:
        for k, v in model.state_dict().items():
            if v.dtype.is_floating_point:
                self.shadow[k].mul_(self.decay).add_(v.detach(), alpha=1 - self.decay)
            else:
                self.shadow[k].copy_(v)

    def ema_model(self, model: nn.Module) -> nn.Module:
        import copy

        m = copy.deepcopy(model)
        m.load_state_dict(self.shadow)
        m.eval()
        return m


# --------------------------------------------------------------------------- #
# self-describing checkpoint
# --------------------------------------------------------------------------- #
def sha256_file(path: Path | str, chunk: int = 1 << 20) -> str:
    digest = hashlib.sha256()
    with open(path, 'rb') as handle:
        for block in iter(lambda: handle.read(chunk), b''):
            digest.update(block)
    return digest.hexdigest()


def save_checkpoint(path: Path | str, state_dict, *, threshold: float = 0.5,
                    metrics: dict | None = None, extra: dict | None = None,
                    size: int = SIZE, crop: int = CROP,
                    mean: float = MEAN, std: float = STD) -> Path:
    """Write weights together with everything needed to run them.

    The result is a plain ``torch.save`` dict with scalar/list/dict metadata,
    so it loads with ``torch.load(..., weights_only=True)``.
    """
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        'format': WEIGHT_FORMAT,
        'arch': ARCH,
        'classes': list(CLASSES),
        'size': int(size),
        'crop': int(crop),
        'mean': float(mean),
        'std': float(std),
        'threshold': float(threshold),
        'created': datetime.now().isoformat(timespec='seconds'),
        'state_dict': state_dict,
    }
    if metrics:
        payload['metrics'] = metrics
    if extra:
        payload.update({k: v for k, v in extra.items() if k not in payload})
    torch.save(payload, path)
    return path


def load_checkpoint(path: Path | str, device='cpu'):
    """Load either a bundle written by :func:`save_checkpoint` or a bare state_dict."""
    path = Path(path)
    obj = torch.load(path, map_location='cpu', weights_only=True)
    if isinstance(obj, dict) and 'state_dict' in obj:
        state_dict = obj['state_dict']
        meta = {k: v for k, v in obj.items() if k != 'state_dict'}
    else:
        state_dict, meta = obj, {}
    if state_dict and all(str(k).startswith('module.') for k in state_dict):
        state_dict = {str(k)[len('module.'):]: v for k, v in state_dict.items()}
    model = Model()
    model.load_state_dict(state_dict)
    model.to(device).eval()
    meta.setdefault('format', 'bare-state-dict')
    meta.setdefault('classes', list(CLASSES))
    meta.setdefault('size', SIZE)
    meta.setdefault('crop', CROP)
    meta.setdefault('mean', MEAN)
    meta.setdefault('std', STD)
    meta.setdefault('threshold', 0.5)
    return model, meta


# --------------------------------------------------------------------------- #
# inference helpers
# --------------------------------------------------------------------------- #
@torch.inference_mode()
def evaluate_loader(model: nn.Module, loader: DataLoader, device,
                    tta: bool = False):
    """One pass over a loader.  Returns (mean cross-entropy, records)."""
    model.eval()
    criterion = nn.CrossEntropyLoss()
    total, seen, records = 0.0, 0, []
    for x, y, early, names, groups in loader:
        x, y = x.to(device), y.to(device)
        logits = model(x)
        total += criterion(logits, y).item() * len(y)
        seen += len(y)
        prob = torch.softmax(logits, dim=1)[:, 1]
        if tta:
            prob = (prob + torch.softmax(model(torch.flip(x, [3])), dim=1)[:, 1]) / 2
        for name, group, label, is_early, p in zip(names, groups, y.tolist(),
                                                   early.tolist(), prob.cpu().tolist()):
            records.append({'filename': str(name), 'group_id': str(group),
                            'label': int(label), 'early': int(is_early),
                            'p_mold': float(p)})
    return total / max(seen, 1), records


def predict_rows(model: nn.Module, rows, root, device=None, batch: int = 32,
                 tta: bool = True, workers: int = 0, meta: dict | None = None):
    """Predict a list of labelled records (see :class:`PeanutDataset`)."""
    meta = meta or {}
    device = device or resolve_device('auto')
    dataset = PeanutDataset(rows, root, train=False,
                            crop=int(meta.get('crop', CROP)),
                            size=int(meta.get('size', SIZE)),
                            mean=float(meta.get('mean', MEAN)),
                            std=float(meta.get('std', STD)))
    loader = DataLoader(dataset, batch_size=batch, shuffle=False,
                        num_workers=workers, pin_memory=device.type == 'cuda')
    _, records = evaluate_loader(model, loader, device, tta=tta)
    return records


class PeanutClassifier:
    """Convenience wrapper for one-off predictions."""

    def __init__(self, weights: Path | str, device=None, threshold: float | None = None,
                 tta: bool = True):
        self.path = Path(weights)
        self.device = resolve_device(device)
        self.model, self.meta = load_checkpoint(self.path, self.device)
        self.tta = tta
        self.threshold = float(threshold if threshold is not None
                               else self.meta.get('threshold', 0.5))

    @torch.inference_mode()
    def predict_image(self, image: Image.Image, tta: bool | None = None) -> float:
        x = to_tensor(image.convert('L'), int(self.meta.get('crop', CROP)),
                      int(self.meta.get('size', SIZE)),
                      float(self.meta.get('mean', MEAN)),
                      float(self.meta.get('std', STD)))[None].to(self.device)
        prob = torch.softmax(self.model(x), dim=1)[:, 1]
        if self.tta if tta is None else tta:
            prob = (prob + torch.softmax(self.model(torch.flip(x, [3])), dim=1)[:, 1]) / 2
        return float(prob.item())

    def predict_path(self, path: Path | str, tta: bool | None = None) -> float:
        return self.predict_image(load_grey(path), tta=tta)

    def label_of(self, p_mold: float, threshold: float | None = None) -> int:
        return int(p_mold >= (self.threshold if threshold is None else threshold))


# --------------------------------------------------------------------------- #
# metrics (numpy only, same definitions as the training run used)
# --------------------------------------------------------------------------- #
def metrics_at_threshold(y, p, threshold: float) -> dict:
    y = np.asarray(y, dtype=int)
    pred = (np.asarray(p, dtype=float) >= threshold).astype(int)
    tn = int(((y == 0) & (pred == 0)).sum())
    fp = int(((y == 0) & (pred == 1)).sum())
    fn = int(((y == 1) & (pred == 0)).sum())
    tp = int(((y == 1) & (pred == 1)).sum())
    accuracy = (tp + tn) / len(y) if len(y) else 0.0
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    specificity = tn / (tn + fp) if tn + fp else 0.0
    return {'threshold': float(threshold), 'accuracy': float(accuracy),
            'precision': float(precision), 'recall': float(recall),
            'f1': float(f1), 'specificity': float(specificity),
            'tn': tn, 'fp': fp, 'fn': fn, 'tp': tp}


def roc_auc(y, p) -> float | None:
    """Rank-based AUC with tie handling (equivalent to sklearn roc_auc_score)."""
    y = np.asarray(y, dtype=int)
    p = np.asarray(p, dtype=float)
    pos = y == 1
    n_pos, n_neg = int(pos.sum()), int((~pos).sum())
    if n_pos == 0 or n_neg == 0:
        return None
    order = np.argsort(p, kind='mergesort')
    sorted_p = p[order]
    ranks = np.empty(len(p), dtype=float)
    i = 0
    while i < len(sorted_p):
        j = i
        while j + 1 < len(sorted_p) and sorted_p[j + 1] == sorted_p[i]:
            j += 1
        ranks[order[i:j + 1]] = (i + j) / 2.0 + 1.0
        i = j + 1
    return float((ranks[pos].sum() - n_pos * (n_pos + 1) / 2.0) / (n_pos * n_neg))


def average_precision(y, p) -> float | None:
    y = np.asarray(y, dtype=int)
    p = np.asarray(p, dtype=float)
    if len(set(y.tolist())) < 2:
        return None
    order = np.argsort(-p, kind='mergesort')
    y, p = y[order], p[order]
    tp = np.cumsum(y)
    fp = np.cumsum(1 - y)
    recall = tp / tp[-1]
    precision = tp / np.maximum(tp + fp, 1)
    # keep the last sample of every tie group (same score => same threshold)
    keep = np.r_[np.where(np.diff(p) != 0)[0], len(p) - 1]
    recall, precision = recall[keep], precision[keep]
    return float(np.sum(np.diff(np.r_[0.0, recall]) * precision))


def best_threshold(y, p, target_recall: float = 0.95) -> dict:
    candidates = np.unique(np.r_[0.0, np.asarray(p, dtype=float), .5, 1.0])
    best = None
    for threshold in candidates:
        m = metrics_at_threshold(y, p, threshold)
        if m['recall'] >= target_recall:
            key = (m['accuracy'], m['f1'], m['threshold'])
            if best is None or key > (best['accuracy'], best['f1'], best['threshold']):
                best = m
    return best or metrics_at_threshold(y, p, 0.0)


def group_records(records) -> list[dict] | None:
    """Average frame probabilities per physical peanut.  None if no group ids."""
    if not records or all(not r.get('group_id') for r in records):
        return None
    buckets: dict[str, dict] = {}
    for r in records:
        gid = r['group_id']
        bucket = buckets.setdefault(gid, {'group_id': gid, 'label': r['label'], 'probs': []})
        if bucket['label'] != r['label']:
            raise ValueError(f'group {gid} has conflicting labels')
        bucket['probs'].append(r['p_mold'])
    return [{'group_id': b['group_id'], 'label': int(b['label']),
             'p_mold': float(np.mean(b['probs'])), 'n_images': len(b['probs'])}
            for b in buckets.values()]


def bootstrap_ci_for_groups(groups, threshold: float, n: int = 1000,
                            seed: int = SEED) -> dict:
    if not groups or len(groups) < 5:
        return {}
    y = np.array([g['label'] for g in groups])
    p = np.array([g['p_mold'] for g in groups])
    rng = np.random.default_rng(seed)
    keys = ('accuracy', 'precision', 'recall', 'specificity', 'f1')
    values = {k: [] for k in keys}
    for _ in range(n):
        idx = rng.integers(0, len(groups), len(groups))
        m = metrics_at_threshold(y[idx], p[idx], threshold)
        for k in keys:
            values[k].append(m[k])
    return {k: [float(np.percentile(v, 2.5)), float(np.percentile(v, 97.5))]
            for k, v in values.items()}


def summarise(records, threshold: float) -> dict:
    """Full metric block for a list of prediction records."""
    y = [r['label'] for r in records]
    p = [r['p_mold'] for r in records]
    result = {
        'n_images': len(records),
        'threshold': float(threshold),
        'at_threshold': metrics_at_threshold(y, p, threshold),
        'at_0.5': metrics_at_threshold(y, p, .5),
        'auc': roc_auc(y, p),
        'average_precision': average_precision(y, p),
        'group_level': None,
    }
    groups = group_records(records)
    if groups:
        result['n_groups'] = len(groups)
        result['group_level'] = {
            'n_groups': len(groups),
            'at_threshold': metrics_at_threshold(
                [g['label'] for g in groups], [g['p_mold'] for g in groups], threshold),
            'auc': roc_auc([g['label'] for g in groups], [g['p_mold'] for g in groups]),
        }
    return result
