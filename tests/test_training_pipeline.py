import importlib.util
from pathlib import Path

import numpy as np
from sklearn.preprocessing import StandardScaler


def test_search_fits_scaler_within_pipeline_and_exports_consistent_components(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    source = Path(__file__).resolve().parents[1] / 'model' / 'train_kaggle_level.py'
    spec = importlib.util.spec_from_file_location('peanut_training', source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    fit_sizes = []

    class RecordingScaler(StandardScaler):
        def fit(self, x, y=None, sample_weight=None):
            fit_sizes.append(len(x))
            return super().fit(x, y, sample_weight=sample_weight)

    monkeypatch.setattr(module, 'StandardScaler', RecordingScaler)
    rng = np.random.default_rng(4)
    x = rng.normal(size=(50, 16)) * np.arange(1, 17)
    y = np.arange(50) % 2
    search = module.make_training_search(
        param_grid={'model__n_estimators': [5], 'model__num_leaves': [3], 'model__min_child_samples': [2]},
        folds=2, n_jobs=1,
    )
    search.fit(x, y)
    assert sorted(fit_sizes) == [25, 25, 50]
    pipeline = search.best_estimator_
    scaler = pipeline.named_steps['scaler']
    model = pipeline.named_steps['model']
    assert scaler.n_samples_seen_ == len(x)
    np.testing.assert_allclose(pipeline.predict_proba(x), model.predict_proba(scaler.transform(x)))
