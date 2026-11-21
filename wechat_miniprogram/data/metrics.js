// data/metrics.js —— 指标口径：近红外单通道二分类（验证集/测试集，交付包 reference 记录）
// 来源：peanut_delivery/reference/test_metrics.json
module.exports = {
  route: 'nir',
  routeName: '近红外单通道 · 二分类',
  model: 'MobileNetV3-Small (1ch)',
  threshold: 0.6823,
  accuracy: 96.8,        // 测试集 157 张 @ 阈值 0.6823
  precision: 97.3,
  recall: 96.1,
  specificity: 97.5,
  auc: 99.5,
  groupAcc: 98.3,       // 按花生组投票（117 组）
  groupRecall: 96.4,
  groupSpecificity: 100.0,
  total: 157,
  groups: 117,
  note: '指标来自交付包 reference 记录；参考测试集已被反复查看，指标偏乐观。',
}
