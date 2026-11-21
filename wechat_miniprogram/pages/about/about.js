// pages/about/about.js
const METRICS = require('../../data/metrics.js')

const DEFAULT_TH = { threshold: 0.6823, marginP: 0.15 }

Page({
  data: {
    m: METRICS,
    th: DEFAULT_TH,
    thrPct: 68.2,
    marginPct: 15.0,
    dirty: false
  },

  onShow() {
    const saved = wx.getStorageSync('judge_thresholds') || {}
    const th = {
      threshold: Number(saved.threshold) || DEFAULT_TH.threshold,
      marginP: Number(saved.marginP) || DEFAULT_TH.marginP
    }
    this.setData({
      th,
      thrPct: +(th.threshold * 100).toFixed(1),
      marginPct: +(th.marginP * 100).toFixed(1)
    })
  },

  // 滑杆给的是整数百分比
  onThreshold(e) {
    const v = Number(e.detail.value) / 100
    this.setData({ 'th.threshold': v, thrPct: +(v * 100).toFixed(1), dirty: true })
  },
  onMarginP(e) {
    const v = Number(e.detail.value) / 100
    this.setData({ 'th.marginP': v, marginPct: +(v * 100).toFixed(1), dirty: true })
  },

  saveTh() {
    const t = this.data.th
    const threshold = Number(t.threshold)
    const marginP = Number(t.marginP)
    if (threshold <= 0.05 || threshold >= 0.95) {
      wx.showToast({ title: '阈值应在 5%–95% 之间', icon: 'none' })
      return
    }
    if (marginP <= 0 || marginP >= 0.4) {
      wx.showToast({ title: '灰区宽度应在 0–40% 之间', icon: 'none' })
      return
    }
    wx.setStorageSync('judge_thresholds', { threshold, marginP })
    this.setData({ dirty: false })
    wx.showToast({ title: '已保存，下次检测生效', icon: 'success' })
  },

  resetTh() {
    wx.removeStorageSync('judge_thresholds')
    this.setData({
      th: DEFAULT_TH,
      thrPct: +(DEFAULT_TH.threshold * 100).toFixed(1),
      marginPct: +(DEFAULT_TH.marginP * 100).toFixed(1),
      dirty: false
    })
    wx.showToast({ title: '已恢复默认', icon: 'none' })
  }
})
