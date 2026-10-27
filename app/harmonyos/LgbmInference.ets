import scalerParams from '../model/scaler_params.json'
import lgbmModel from '../model/lgbm_model.json'

/**
 * LightGBM端侧纯ArkTS推理引擎
 * 直接遍历树结构完成推理，无原生依赖，适配HarmonyOS API 12+
 */
export class LgbmInference {
  private means: number[] = scalerParams.mean_
  private scales: number[] = scalerParams.scale_
  private treeInfo: any[] = (lgbmModel as any).tree_info || []
  private numClass: number = (lgbmModel as any).num_class || 2

  constructor() {
    console.info(`LgbmInference init: ${this.treeInfo.length} trees, ${this.numClass} classes`)
  }

  /**
   * 对输入特征向量做Z-score标准化
   * @param features 原始16维特征向量
   */
  private transform(features: number[]): number[] {
    if (features.length !== this.means.length) {
      throw new Error(`Feature dim mismatch: expect ${this.means.length}, got ${features.length}`)
    }
    return features.map((v, i) => (v - this.means[i]) / this.scales[i])
  }

  /**
   * 单棵树推理，返回叶子节点分数
   */
  private predictSingleTree(node: any, features: number[]): number {
    while (true) {
      if (node.leaf_value !== undefined) {
        return node.leaf_value
      }
      const splitFeature = node.split_feature
      const threshold = node.threshold
      const featureVal = features[splitFeature]
      if (featureVal <= threshold) {
        node = node.left_child
      } else {
        node = node.right_child
      }
    }
  }

  /**
   * 输入特征向量，返回霉变概率(0~1)
   */
  public predictProba(features: number[]): number {
    const scaled = this.transform(features)
    let rawScore = 0
    for (const tree of this.treeInfo) {
      rawScore += this.predictSingleTree(tree.tree_structure, scaled)
    }
    // Sigmoid转概率（二分类）
    return 1 / (1 + Math.exp(-rawScore))
  }

  /**
   * 便捷方法：直接返回分类结果和置信度
   */
  public classify(features: number[], threshold: number = 0.5): {
    isMoldy: boolean,
    confidence: number,
    moldyProb: number
  } {
    const prob = this.predictProba(features)
    return {
      isMoldy: prob >= threshold,
      confidence: Math.max(prob, 1 - prob),
      moldyProb: prob
    }
  }
}
