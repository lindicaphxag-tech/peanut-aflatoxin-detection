import picker from '@ohos.file.picker'
import common from '@ohos.app.ability.common'
import { LgbmInference } from '../engine/LgbmInference'
import { HistoryDb } from '../db/HistoryDb'
import { featureExtractor } from '../engine/FeatureExtractor'
import promptAction from '@ohos.promptAction'
import router from '@ohos.router'
import preferences from '@ohos.data.preferences'

@Entry
@Component
struct Index {
  @State result: string = ''
  @State confidence: number = 0
  @State hasScore: boolean = false
  @State isAnalyzing: boolean = false
  @State isReady: boolean = false
  @State selectedImage: string = ''
  private inference: LgbmInference = new LgbmInference()
  private historyDb: HistoryDb = new HistoryDb()
  private context: common.UIAbilityContext = getContext(this) as common.UIAbilityContext

  async aboutToAppear() {
    try {
      await this.historyDb.init(this.context)
      const agreed = await preferences.getPreferences(this.context, 'app_prefs')
      if (!await agreed.get('privacy_agreed', false)) {
        const accepted = await this.showPrivacyDialog()
        if (!accepted) {
          this.result = '请同意隐私说明后重新打开应用'
          return
        }
      }
      this.isReady = true
    } catch (e) {
      this.result = '初始化失败，请重启应用'
    }
  }

  private async showPrivacyDialog(): Promise<boolean> {
    const choice = await promptAction.showDialog({
      title: '隐私政策',
      message: '本应用所有检测数据均仅存储在您的手机本地，不会上传任何图像或个人信息到服务器。',
      buttons: [{ text: '同意', color: '#007aff' }]
    })
    if (choice.index !== 0) return false
    const pref = await preferences.getPreferences(this.context, 'app_prefs')
    await pref.put('privacy_agreed', true)
    await pref.flush()
    return true
  }

  async startDetect() {
    if (this.isAnalyzing) return
    try {
      const photoPicker = new picker.PhotoViewPicker()
      const res = await photoPicker.select({
        MIMEType: picker.PhotoViewMIMETypes.IMAGE_TYPE,
        maxSelectNumber: 1
      })
      if (res.photoUris.length > 0) {
        await this.analyzeImage(res.photoUris[0])
      }
    } catch (e) {
      promptAction.showToast({ message: '无法打开图片选择器，请重试' })
    }
  }

  async analyzeImage(uri: string) {
    this.isAnalyzing = true
    this.result = ''
    this.hasScore = false
    this.selectedImage = ''
    try {
      // 1. 端侧提取16维特征
      const features = await featureExtractor.extract(uri, this.context)
      // 2. 本地推理
      const res = this.inference.classify(features)
      this.result = '模型演示结果（未经验证）'
      this.confidence = res.moldyProb
      // 3. 保存历史记录
      await this.historyDb.insert({
        timestamp: new Date().getTime(),
        imageUri: uri,
        isMoldy: res.isMoldy,
        confidence: res.moldyProb
      })
      this.hasScore = true
      this.selectedImage = uri
    } catch (e) {
      this.result = '检测失败，请重试'
      promptAction.showToast({ message: '图片处理失败，请更换清晰照片' })
    } finally {
      this.isAnalyzing = false
    }
  }

  build() {
    Scroll() {
    Column({ space: 24 }) {
      Text('坚果图像分析演示')
        .fontSize(28)
        .fontWeight(FontWeight.Bold)
        .margin({ top: 60 })

      Text('研究演示：图像特征与模型尚待验证')
        .fontSize(14)
        .fontColor('#666')

      Stack() {
        Rect()
          .width(280)
          .height(280)
          .fill('#f5f5f5')
          .radius(16)
        if (this.selectedImage) {
          Image(this.selectedImage)
            .width(280)
            .height(280)
            .objectFit(ImageFit.Contain)
        } else {
          Text('选择图片进行模型演示')
            .fontColor('#999')
        }
      }
      .margin({ top: 20 })

      if (this.result) {
        Text(this.result)
          .fontSize(20)
          .fontColor('#333333')
          .fontWeight(FontWeight.Medium)
        if (this.hasScore) {
        Text(`模型输出分数：${(this.confidence * 100).toFixed(1)}%`)
          .fontSize(16)
          .fontColor('#666')
        }
      }

      if (this.isAnalyzing) {
        LoadingProgress()
          .width(60)
          .height(60)
      }

      Button('运行模型演示')
        .width(280)
        .height(50)
        .fontSize(18)
        .enabled(this.isReady && !this.isAnalyzing)
        .onClick(() => {
          this.startDetect()
        })

      Button('查看检测历史')
        .width(280)
        .height(44)
        .fontSize(16)
        .backgroundColor('#eeeeee')
        .fontColor('#333')
        .enabled(this.isReady && !this.isAnalyzing)
        .onClick(() => {
          router.pushUrl({ url: 'pages/History' })
        })

      Text('*演示结果不能用于判断食品安全，请以实验室检测为准')
        .fontSize(12)
        .fontColor('#999')
        .margin({ top: 40 })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Center)
    }
    .width('100%')
    .height('100%')
  }
}
