# 公开光谱数据实验

此实验是独立的五分类光谱基线，不评估手机图片模型，不输出黄曲霉毒素浓度。

数据源：[Peanut-seed](https://github.com/wuqingsongwj/Peanut-seed)；相关[论文](https://doi.org/10.3389/fpls.2022.1047479)。下载仓库中的 `peanut.xlsx`，确认 SHA-256 为 `5ae0b3ae5175e5adebfb5091045cacb02f05c9e50593ac0a7cf4fadd06f28866`。

在安装 `requirements.txt` 后运行：

```bash
python experiments/public_spectra_baseline.py path/to/peanut.xlsx --output results.json
```

工作表含 2,200 行、236 个数值输入列和一个数字类别列。论文描述 235 个光谱波段，文件中的 236 个输入列的含义仍需作者确认；五个数字标签没有在该文件中明确映射到类名。共有 120 个重复光谱，程序以精确相同的光谱为组，使用固定随机种子进行分层五折分组划分，保留第一折测试。

数据未提供采集批次、来源或设备的分组标识，因此不能排除其他形式的数据泄漏，不能证明跨批次泛化。实验结果不适用于手机照片或食品安全判断。
