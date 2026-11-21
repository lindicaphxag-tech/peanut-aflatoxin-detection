// pages/history/history.js

// 把 "2026/11/21 20:12:33" 这类本地时间字符串解析成日期（不依赖 Date 的宽松解析）
function parseTime(str) {
  if (!str) return null
  const m = String(str).match(/(\d{4})[/-](\d{1,2})[/-](\d{1,2})/)
  if (!m) return null
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
}

// 最近 7 天（含今天）的检测量，按天聚合成"正常 / 霉变"两段
function buildTrend(list) {
  const days = []
  const today = new Date()
  const keyOf = (d) => `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`

  for (let i = 6; i >= 0; i--) {
    const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i)
    days.push({
      key: keyOf(d),
      label: `${d.getMonth() + 1}/${d.getDate()}`,
      isToday: i === 0,
      ok: 0,
      mold: 0,
      total: 0
    })
  }
  const index = {}
  days.forEach((d, i) => { index[d.key] = i })

  list.forEach((item) => {
    const d = parseTime(item.time)
    if (!d) return
    const i = index[keyOf(d)]
    if (i === undefined) return
    days[i].total += 1
    if (item.status === 'moldy') days[i].mold += 1
    else days[i].ok += 1
  })

  const max = Math.max(1, ...days.map((d) => d.total))
  // 归一化成百分比高度，交给 WXSS 用
  days.forEach((d) => {
    d.okPct = Math.round((d.ok / max) * 100)
    d.moldPct = Math.round((d.mold / max) * 100)
  })
  return { days, max }
}

Page({
  data: {
    list: [],
    viewList: [],
    groups: [],
    filter: 'all',
    filterIndex: 0,
    sort: 'time',
    totalCount: 0,
    safeCount: 0,
    moldCount: 0,
    safeRate: 0,
    trend: [],
    weekTotal: 0,
    weekMold: 0,
    showDetailModal: false,
    detailItem: null
  },

  // 应用筛选与排序，得到要渲染的列表
  applyView() {
    const all = this.data.list || []
    const f = this.data.filter
    const st = this.data.sort
    let v = all.filter((i) => {
      if (f === 'ok') return i.status !== 'moldy'
      if (f === 'risk') return i.status === 'moldy'
      return true
    })
    if (st === 'risk') {
      // 风险优先：霉变在前，同类按置信度从高到低
      v = v.slice().sort((a, b) => {
        const ra = a.status === 'moldy' ? 1 : 0
        const rb = b.status === 'moldy' ? 1 : 0
        if (ra !== rb) return rb - ra
        return (b.confidence || 0) - (a.confidence || 0)
      })
    }
    // 按日期分组，形成时间轴
    const map = {}
    v.forEach((it) => {
      const d = String(it.time || '').slice(0, 10) || '未知日期'
      if (!map[d]) map[d] = []
      map[d].push(it)
    })
    const groups = Object.keys(map)
      .sort((a, b) => (a < b ? 1 : -1))
      .map((d) => ({
        date: d,
        items: map[d],
        mold: map[d].filter((i) => i.status === 'moldy').length,
      }))

    this.setData({ viewList: v, groups })
  },

  onDelete(e) {
    const time = e.currentTarget.dataset.time
    const item = (this.data.list || []).find((i) => i.time === time)
    if (!item) return
    wx.showModal({
      title: '删除这条记录？',
      content: `${item.result || ''} · ${time}`,
      confirmText: '删除',
      confirmColor: '#ff7a63',
      success: (r) => {
        if (!r.confirm) return
        const rest = (this.data.list || []).filter((i) => i.time !== time)
        wx.setStorageSync('history', rest)
        this.setData({ list: rest })
        this.onShow()
        wx.showToast({ title: '已删除', icon: 'none' })
      },
    })
  },

  setFilter(e) {
    const f = e.currentTarget.dataset.f
    const idx = f === 'all' ? 0 : (f === 'ok' ? 1 : 2)
    this.setData({ filter: f, filterIndex: idx })
    this.applyView()
  },

  setSort(e) {
    this.setData({ sort: e.currentTarget.dataset.s })
    this.applyView()
  },

  onShow() {
    const list = wx.getStorageSync('history') || []
    const safeCount = list.filter((i) => i.status !== 'moldy').length
    const moldCount = list.length - safeCount
    const { days } = buildTrend(list)

    this.setData({
      list,
      totalCount: list.length,
      safeCount,
      moldCount,
      safeRate: list.length ? ((safeCount / list.length) * 100).toFixed(1) : 0,
      trend: days,
      weekTotal: days.reduce((s, d) => s + d.total, 0),
      weekMold: days.reduce((s, d) => s + d.mold, 0)
    })
    this.applyView()
  },

  // 图片加载失败（旧记录/文件被清理）时降级为占位块
  onImgError(e) {
    const idx = e.currentTarget.dataset.index
    const list = this.data.viewList.slice()
    if (list[idx]) {
      list[idx] = Object.assign({}, list[idx], { imgBroken: true })
      this.setData({ viewList: list })
    }
  },

  showDetail(e) {
    const item = e.currentTarget.dataset.item
    this.setData({ showDetailModal: true, detailItem: item })
  },

  hideDetail() {
    this.setData({ showDetailModal: false, detailItem: null })
  },

  noop() {},

  goDetect() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  // 生成 CSV 文本（不含 BOM，粘进 Excel 可直接分列）
  buildCsv(list) {
    const esc = (v) => {
      const str = v === undefined || v === null ? '' : String(v)
      return /[",\n]/.test(str) ? '"' + str.replace(/"/g, '""') + '"' : str
    }
    const head = ['时间', '判定结果', '类别下标', '置信度(%)', '三态结论', '状态']
    const rows = list.map((i) => [
      i.time,
      i.result || (i.status === 'moldy' ? '霉变' : '正常'),
      i.classIndex,
      i.confidence,
      i.decision || '',
      i.status === 'moldy' ? '霉变' : '正常',
    ])
    return [head].concat(rows).map((r) => r.map(esc).join(',')).join('\n')
  },

  exportCsv() {
    const list = this.data.list || []
    if (!list.length) {
      wx.showToast({ title: '还没有记录可导出', icon: 'none' })
      return ''
    }
    const csv = this.buildCsv(list)
    wx.setClipboardData({
      data: csv,
      success: () => {
        wx.showModal({
          title: '已复制到剪贴板',
          content: `共 ${list.length} 条记录，可直接粘贴到 Excel / WPS 表格。`,
          showCancel: false,
          confirmText: '好',
        })
      },
      fail: () => wx.showToast({ title: '复制失败，请重试', icon: 'none' }),
    })
    return csv
  },

  clearHistory() {
    wx.showModal({
      title: '确认清空',
      content: '确定要清空所有检测记录吗？',
      success: (res) => {
        if (res.confirm) {
          wx.removeStorageSync('history')
          this.onShow()
          wx.showToast({ title: '已清空', icon: 'success' })
        }
      }
    })
  }
})
