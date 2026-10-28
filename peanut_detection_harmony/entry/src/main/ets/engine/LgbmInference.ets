import scalerParams from '../model/scaler_params.json'
import lgbmModel from '../model/lgbm_model.json'

interface TreeNode {
  leaf_value?: number
  split_feature?: number
  threshold?: number
  left_child?: TreeNode
  right_child?: TreeNode
}
interface TreeInfo { tree_structure: TreeNode }
interface TreeModel { tree_info: TreeInfo[] }
export interface ClassifyResult { isMoldy: boolean; confidence: number; moldyProb: number }

/**
 * LightGBM端侧纯ArkTS推理引擎
 * 直接遍历树结构完成推理，无原生依赖。
 */
export class LgbmInference {
  private means: number[] = scalerParams.mean_
  private scales: number[] = scalerParams.scale_
  private treeInfo: TreeInfo[] = (lgbmModel as TreeModel).tree_info

  constructor() {
    if (this.means.length === 0 || this.means.length !== this.scales.length ||
      this.means.some((v: number) => !Number.isFinite(v)) ||
      this.scales.some((v: number) => !Number.isFinite(v) || v <= 0) || this.treeInfo.length === 0) {
      throw new Error('模型参数无效')
    }
    console.info(`LgbmInference init: ${this.treeInfo.length} trees`)
  }

  private transform(features: number[]): number[] {
    if (features.length !== this.means.length || features.some((v: number) => !Number.isFinite(v))) {
      throw new Error('图片特征无效')
    }
    return features.map((v, i) => (v - this.means[i]) / this.scales[i])
  }

  private predictSingleTree(node: TreeNode, features: number[]): number {
    while (true) {
      if (node.leaf_value !== undefined) {
        return node.leaf_value
      }
      const splitFeature = node.split_feature as number
      const threshold = node.threshold as number
      const featureVal = features[splitFeature]
      node = (featureVal <= threshold ? node.left_child : node.right_child) as TreeNode
    }
  }

  public predictProba(features: number[]): number {
    const scaled = this.transform(features)
    let rawScore = 0
    for (const tree of this.treeInfo) {
      rawScore += this.predictSingleTree(tree.tree_structure, scaled)
    }
    return 1 / (1 + Math.exp(-rawScore))
  }

  public classify(features: number[], threshold: number = 0.5): ClassifyResult {
    if (!Number.isFinite(threshold) || threshold < 0 || threshold > 1) throw new Error('阈值无效')
    const prob = this.predictProba(features)
    return {
      isMoldy: prob >= threshold,
      confidence: Math.max(prob, 1 - prob),
      moldyProb: prob
    }
  }
}
