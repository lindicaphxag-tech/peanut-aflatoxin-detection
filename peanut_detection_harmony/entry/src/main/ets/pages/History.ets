import common from '@ohos.app.ability.common'
import router from '@ohos.router'
import { HistoryDb, HistoryRecord } from '../db/HistoryDb'

@Entry
@Component
struct History {
  @State records: HistoryRecord[] = []
  @State error: string = ''
  private db: HistoryDb = new HistoryDb()

  async aboutToAppear() {
    try {
      await this.db.init(getContext(this) as common.UIAbilityContext)
      this.records = await this.db.getAll()
    } catch (e) {
      this.error = '无法读取历史记录'
    }
  }

  build() {
    Column({ space: 16 }) {
      Button('返回').onClick(() => router.back())
      Text('检测历史').fontSize(24).fontWeight(FontWeight.Bold)
      if (this.error) {
        Text(this.error)
      } else if (this.records.length === 0) {
        Text('暂无历史记录')
      }
      List({ space: 12 }) {
        ForEach(this.records, (record: HistoryRecord) => {
          ListItem() {
            Column({ space: 4 }) {
              Text(new Date(record.timestamp).toLocaleString())
              Text('模型演示结果（未经验证）')
              Text(`模型分数：${(record.confidence * 100).toFixed(1)}%`)
            }.alignItems(HorizontalAlign.Start).padding(12)
          }
        }, (record: HistoryRecord) => record.id.toString())
      }.layoutWeight(1).width('100%')
    }.width('100%').height('100%').padding(16)
  }
}
