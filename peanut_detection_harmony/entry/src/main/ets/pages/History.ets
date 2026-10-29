import common from '@ohos.app.ability.common'
import router from '@ohos.router'
import picker from '@ohos.file.picker'
import fileIo from '@ohos.file.fs'
import util from '@ohos.util'
import promptAction from '@ohos.promptAction'
import { HistoryDb, HistoryRecord } from '../db/HistoryDb'
import { serializeHistory, writeAll } from '../db/HistoryExport'

@Entry
@Component
struct History {
  @State records: HistoryRecord[] = []
  @State error: string = ''
  @State isReady: boolean = false
  @State isExporting: boolean = false
  private db: HistoryDb = new HistoryDb()

  async aboutToAppear() {
    try {
      await this.db.init(getContext(this) as common.UIAbilityContext)
      this.records = await this.db.getAll()
      this.isReady = true
    } catch (e) {
      this.error = '无法读取历史记录'
    }
  }

  async exportHistory() {
    if (!this.isReady || this.isExporting) return
    this.isExporting = true
    try {
      const records = await this.db.getAll()
      const text = serializeHistory(records)
      const options = new picker.DocumentSaveOptions()
      options.newFileNames = ['peanut-demo-history.json']
      const uris = await new picker.DocumentViewPicker().save(options)
      if (uris.length === 0) return
      const bytes = new util.TextEncoder().encodeInto(text)
      const file = await fileIo.open(uris[0], fileIo.OpenMode.WRITE_ONLY | fileIo.OpenMode.TRUNC)
      try {
        await writeAll(bytes, (data: ArrayBuffer): Promise<number> => fileIo.write(file.fd, data))
      } finally {
        await fileIo.close(file.fd)
      }
      promptAction.showToast({ message: '历史记录已导出' })
    } catch (e) {
      promptAction.showToast({ message: '导出失败，请检查保存位置后重试' })
    } finally {
      this.isExporting = false
    }
  }

  build() {
    Column({ space: 16 }) {
      Button('返回').onClick(() => router.back())
      Text('检测历史').fontSize(24).fontWeight(FontWeight.Bold)
      Button(this.isExporting ? '正在导出' : '导出记录')
        .enabled(this.isReady && !this.isExporting)
        .onClick(() => { this.exportHistory() })
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
