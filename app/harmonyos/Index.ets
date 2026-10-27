import { camera } from '@kit.CameraKit'
import { picker } from '@kit.CoreFileKit'
import { common } from '@kit.AbilityKit'
import { LgbmInference } from './LgbmInference'
import featureExtractor from './FeatureExtractor'

@Entry
@Component
struct Index {
  @State result: string = ''
  @State confidence: number = 0
  @State isAnalyzing: boolean = false
  private inference: LgbmInference = new LgbmInference()
  private context: common.UIAbilityContext = getContext(this) as common.UIAbilityContext

  // 调用系统相机拍摄检测图像
  async takePhoto() {
    // 实际项目中接入HarmonyOS Camera Kit完成三波段自动拍摄
    // 这里先提供相册上传通道完成功能闭环
    const photoPicker = new picker.PhotoViewPicker()
    const result = await photoPicker.select({
      MIMEType: picker.PhotoViewMIMETypes.IMAGE_TYPE,
      maxSelectNumber: 1
    })
    if (result.photoUris.length > 0) {
      this.analyzeImage(result.photoUris[0])
    }
  }

  // 执行图像分析
  async analyzeImage(uri: string) {
    this.isAnalyzing = true
    try {
      // 1. 从图像提取16维特征（对应lightGBM.py中的特征维度）
      const features = await featureExtractor.extractFromUri(uri, this.context)
      // 2. 调用本地模型推理
      const res = this.inference.classify(features)
      this.result = res.isMoldy ? '检测结果：存在黄曲霉污染风险' : '检测结果：花生状态正常'
      this.confidence = res.confidence
      // 3. 保存到历史记录
      this.saveToHistory(uri, res)
    } catch (e) {
      this.result = '检测失败，请重新拍摄'
      console.error(`Analyze error: ${JSON.stringify(e)}`)
    } finally {
      this.isAnalyzing = false
    }
  }

  // 保存检测历史到本地RDB数据库
  private saveToHistory(uri: string, res: any) {
    // 实际项目中通过@kit.ArkData RDB存储记录，包含时间戳、缩略图、结果、置信度
    console.info(`Save history: ${uri}, result=${res.isMoldy}, conf=${res.confidence}`)
  }

  build() {
    Column({ space: 24 }) {
      Text('坚果黄曲霉实时检测系统')
        .fontSize(28)
        .fontWeight(FontWeight.Bold)
        .margin({ top: 60 })

      Text('基于近红外三波段+LightGBM融合算法')
        .fontSize(14)
        .fontColor('#666')

      // 拍摄预览占位框
      Stack() {
        Image($r('app.media.camera_preview'))
          .width(280)
          .height(280)
          .borderRadius(16)
          .backgroundColor('#f5f5f5')
        Text('将样本置于镜头中心')
          .fontColor('#999')
      }
      .margin({ top: 20 })

      // 检测结果展示区
      if (this.result) {
        Text(this.result)
          .fontSize(20)
          .fontColor(this.result.includes('风险') ? '#e53935' : '#43a047')
          .fontWeight(FontWeight.Medium)
        Text(`置信度：${(this.confidence * 100).toFixed(1)}%`)
          .fontSize(16)
          .fontColor('#666')
      }

      if (this.isAnalyzing) {
        LoadingProgress()
          .width(60)
          .height(60)
      }

      // 操作按钮区
      Button('开始检测（拍摄/上传）')
        .width(280)
        .height(50)
        .fontSize(18)
        .onClick(() => {
          this.takePhoto()
        })

      Button('查看检测历史')
        .width(280)
        .height(44)
        .fontSize(16)
        .backgroundColor('#eeeeee')
        .fontColor('#333')
        .onClick(() => {
          router.pushUrl({ url: 'pages/History' })
        })

      Text('*检测结果仅作快速筛查参考，确诊请以实验室检测为准')
        .fontSize(12)
        .fontColor('#999')
        .margin({ top: 40 })
    }
    .width('100%')
    .height('100%')
    .alignItems(HorizontalAlign.Center)
  }
}
