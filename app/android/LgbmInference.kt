package com.hdu.peanutaflatoxin.ml

import org.json.JSONObject
import kotlin.math.exp

/**
 * Android端LightGBM纯Kotlin推理引擎
 * 直接遍历树结构完成推理，适配Android 8.0+
 */
class LgbmInference(context: android.content.Context) {
    private val means: DoubleArray
    private val scales: DoubleArray
    private val treeRoots: List<JSONObject>

    init {
        // 从assets加载模型文件
        val scalerJson = context.assets.open("scaler_params.json").bufferedReader().use { it.readText() }
        val scalerObj = JSONObject(scalerJson)
        val meanArr = scalerObj.getJSONArray("mean_")
        val scaleArr = scalerObj.getJSONArray("scale_")
        means = DoubleArray(meanArr.length()) { meanArr.getDouble(it) }
        scales = DoubleArray(scaleArr.length()) { scaleArr.getDouble(it) }

        val modelJson = context.assets.open("lgbm_model.json").bufferedReader().use { it.readText() }
        val modelObj = JSONObject(modelJson)
        val treeInfo = modelObj.getJSONArray("tree_info")
        treeRoots = List(treeInfo.length()) { i ->
            treeInfo.getJSONObject(i).getJSONObject("tree_structure")
        }
    }

    private fun transform(features: DoubleArray): DoubleArray {
        require(features.size == means.size) { "Feature dim mismatch: expect ${means.size}, got ${features.size}" }
        return DoubleArray(features.size) { (features[it] - means[it]) / scales[it] }
    }

    private fun predictSingleTree(node: JSONObject, features: DoubleArray): Double {
        var current = node
        while (true) {
            if (current.has("leaf_value")) {
                return current.getDouble("leaf_value")
            }
            val splitFeature = current.getInt("split_feature")
            val threshold = current.getDouble("threshold")
            val featureVal = features[splitFeature]
            current = if (featureVal <= threshold) {
                current.getJSONObject("left_child")
            } else {
                current.getJSONObject("right_child")
            }
        }
    }

    fun predictProba(features: DoubleArray): Double {
        val scaled = transform(features)
        var rawScore = 0.0
        for (treeRoot in treeRoots) {
            rawScore += predictSingleTree(treeRoot, scaled)
        }
        return 1.0 / (1.0 + exp(-rawScore))
    }

    data class ClassifyResult(
        val isMoldy: Boolean,
        val confidence: Double,
        val moldyProb: Double
    )

    fun classify(features: DoubleArray, threshold: Double = 0.5): ClassifyResult {
        val prob = predictProba(features)
        return ClassifyResult(
            isMoldy = prob >= threshold,
            confidence = maxOf(prob, 1 - prob),
            moldyProb = prob
        )
    }
}
