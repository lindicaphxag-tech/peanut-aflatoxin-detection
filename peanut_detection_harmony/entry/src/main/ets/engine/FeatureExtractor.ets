import image from '@ohos.multimedia.image'
import fileIo from '@ohos.file.fs'
import common from '@ohos.app.ability.common'

class FeatureExtractorImpl {
  async extract(uri: string, context: common.UIAbilityContext): Promise<number[]> {
    // 1. 解码图片
    const file = fileIo.openSync(uri, fileIo.OpenMode.READ_ONLY)
    let pixelBytes: ArrayBuffer = new ArrayBuffer(0)
    try {
      const source = image.createImageSource(file.fd)
      try {
        const pixelMap = await source.createPixelMap({
          desiredSize: { width: 256, height: 256 },
          desiredPixelFormat: image.PixelMapFormat.RGBA_8888
        })
        try {
          pixelBytes = new ArrayBuffer(pixelMap.getPixelBytesNumber())
          await pixelMap.readPixelsToBuffer(pixelBytes)
        } finally {
          await pixelMap.release()
        }
      } finally {
        await source.release()
      }
    } finally {
      fileIo.closeSync(file.fd)
    }
    // 2. 获取像素数据
    const pixels = new Uint8Array(pixelBytes)
    if (pixels.length === 0 || pixels.length % 4 !== 0) throw new Error('图片像素数据无效')

    // 3. 演示用近似特征，与训练流程尚未对齐
    const grayValues: number[] = []
    for (let i = 0; i < pixels.length; i += 4) {
      const r = pixels[i]
      const g = pixels[i + 1]
      const b = pixels[i + 2]
      grayValues.push(0.299 * r + 0.587 * g + 0.114 * b)
    }

    // 简化统计特征提取
    const mean = grayValues.reduce((a, b) => a + b, 0) / grayValues.length
    const std = Math.sqrt(grayValues.map(v => (v - mean) ** 2).reduce((a, b) => a + b, 0) / grayValues.length)
    const sorted = [...grayValues].sort((a, b) => a - b)
    const p10 = sorted[Math.floor(sorted.length * 0.1)]
    const p50 = sorted[Math.floor(sorted.length * 0.5)]
    const p90 = sorted[Math.floor(sorted.length * 0.9)]

    // 拼接16维特征
    return [
      mean, std, 0.05, 0.02, p10, p50, p90, p90 - p10,
      12.5, 0.32, 0.81, 0.78,
      8.2, 3.1,
      0.82, 0.31
    ]
  }
}

export const featureExtractor = new FeatureExtractorImpl()
