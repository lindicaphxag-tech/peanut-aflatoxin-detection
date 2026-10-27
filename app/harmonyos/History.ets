interface HistoryRecord {
  id: number
  timestamp: string
  thumbnail: string
  isMoldy: boolean
  confidence: number
}

@Entry
@Component
struct History {
  @State records: HistoryRecord[] = []

  aboutToAppear() {
    // 实际从RDB数据库加载历史记录
    this.records = [
      { id: 1, timestamp: '2026-10-27 14:32', thumbnail: '', isMoldy: false, confidence: 0.92 },
      { id: 2, timestamp: '2026-10-27 11:18', thumbnail: '', isMoldy: true, confidence: 0.87 },
      { id: 3, timestamp: '2026-10-26 19:45', thumbnail: '', isMoldy: false, confidence: 0.95 }
    ]
  }

  build() {
    Column() {
      // 标题栏
      Row() {
        Button('返回')
          .fontSize(14)
          .backgroundColor('transparent')
          .fontColor('#333')
        Text('检测历史')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .layoutWeight(1)
          .textAlign(TextAlign.Center)
        Text('')
          .width(60)
      }
      .width('100%')
      .padding(16)
      .backgroundColor('#f8f8f8')

      // 历史记录列表
      List({ space: 12 }) {
        ForEach(this.records, (item: HistoryRecord) => {
          ListItem() {
            Row({ space: 16 }) {
              // 缩略图占位
              Image($r('app.media.placeholder'))
                .width(60)
                .height(60)
                .borderRadius(8)
                .backgroundColor('#eee')

              Column({ space: 4 }) {
                Text(item.timestamp)
                  .fontSize(16)
                  .fontColor('#333')
                Text(item.isMoldy ? '存在霉变风险' : '状态正常')
                  .fontSize(14)
                  .fontColor(item.isMoldy ? '#e53935' : '#43a047')
                Text(`置信度：${(item.confidence * 100).toFixed(1)}%`)
                  .fontSize(12)
                  .fontColor('#999')
              }
              .alignItems(HorizontalAlign.Start)
              .layoutWeight(1)
            }
            .width('100%')
            .padding(12)
            .backgroundColor('#fff')
            .borderRadius(12)
          }
        })
      }
      .layoutWeight(1)
      .padding(16)
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#f5f5f5')
  }
}
