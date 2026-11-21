// pages/index/index.js
const CONFIG = require('../../config.js')

const API_BASE = CONFIG.API_BASE
const DETECT_URL = `${API_BASE}/api/detect`
const HEALTH_URL = `${API_BASE}/api/health`

// 后端返回的三个类别，顺序与模型输出一致
const CLASS_NAMES = ['正常', '发霉']

Page({
  data: {
    imagePath: '',
    result: '',
    classIndex: -1,
    confidence: 0,
    normalProb: 0,
    moldLightProb: 0,
    moldHeavyProb: 0,
    moldProb: 0,
    // 三态判定（端上根据三类概率计算）
    decision: '',
    decisionTone: '',
    decisionHint: '',
    margin: 0,
    confidenceShown: 0,
    showWhy: false,
    neighbors: [],
    anomalyScore: 0,
    refCount: 0,
    elapsedMs: 0,
    decisionRule: '',
    thThreshold: 0.6823,
    thrPct: 68.2,
    thMarginP: 0.15,
    moldyProb: 0,
    normalProb: 0,
    qcNote: '',
    showPoster: false,
    posterPath: '',
    lastResult: '',
    lastTime: '',
    lastConfidence: 0,
    lastTone: '',
    todayCount: 0,
    isDetecting: false,
    anaStep: 0,
    detectTime: '',
    showResult: false,
    batchResults: [],
    batchTotal: 0,
    batchOkCount: 0,
    batchMoldCount: 0,
    batchFailCount: 0,
    batchAvgConf: '0.0',
    // 后端连通状态：null=检测中 true=在线 false=离线
    serverOnline: null,
    modelName: 'MobileNetV3-Small(1ch)',
    apiBase: API_BASE
  },

  onLoad() {
    this.checkBackend()
  },

  // 探测后端是否在线，首页状态栏和检测按钮都依赖它
  checkBackend() {
    wx.request({
      url: HEALTH_URL,
      method: 'GET',
      timeout: 5000,
      success: (res) => {
        if (res.statusCode === 200 && res.data && res.data.status === 'ok') {
          this.setData({
            serverOnline: true,
            modelName: res.data.model || 'MobileNetV3-Small(1ch)'
          })
        } else {
          this.setData({ serverOnline: false })
        }
      },
      fail: () => {
        this.setData({ serverOnline: false })
      }
    })
  },

  retryBackend() {
    this.setData({ serverOnline: null })
    this.checkBackend()
    wx.showToast({ title: '正在重新连接后端', icon: 'none' })
  },

  // 从相册选择图片
  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      success: (res) => {
        this.applyPickedImage(res.tempFiles[0].tempFilePath)
      }
    })
  },

  // 拍照
  takePhoto() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      success: (res) => {
        this.applyPickedImage(res.tempFiles[0].tempFilePath)
      }
    })
  },

  // 解析后端返回，后端出错时给出可读提示
  parseResponse(res) {
    let data = null
    try {
      data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
    } catch (e) {
      data = null
    }
    if (res.statusCode !== 200 || !data || data.error) {
      const msg = (data && data.error) ? data.error : `服务返回异常（HTTP ${res.statusCode}）`
      throw new Error(msg)
    }
    return data
  },

  // 检测中的四步进度动画（纯前端，只为让等待过程可读）
  startSteps() {
    this.stopSteps()
    this.setData({ anaStep: 1 })
    this._stepTimers = [
      setTimeout(() => this.setData({ anaStep: 2 }), 420),
      setTimeout(() => this.setData({ anaStep: 3 }), 900),
      setTimeout(() => this.setData({ anaStep: 4 }), 1500),
    ]
  },

  stopSteps() {
    if (this._stepTimers) {
      this._stepTimers.forEach((t) => clearTimeout(t))
      this._stepTimers = null
    }
  },

  // 图片质量门控：缩到 64×64 取样，算平均亮度与拉普拉斯方差
  checkQuality(tempPath) {
    return new Promise((resolve) => {
      wx.createSelectorQuery().in(this).select('#qcCanvas').fields({ node: true, size: true }).exec((res) => {
        const node = res && res[0] && res[0].node
        if (!node) return resolve({ ok: true, brightness: 128, blur: 999, note: '' })
        const N = 64
        node.width = N
        node.height = N
        const ctx = node.getContext('2d')
        const img = node.createImage()
        img.onload = () => {
          try {
            ctx.clearRect(0, 0, N, N)
            ctx.drawImage(img, 0, 0, N, N)
            const data = ctx.getImageData(0, 0, N, N).data
            const gray = new Float32Array(N * N)
            let sum = 0
            for (let i = 0; i < N * N; i++) {
              const r = data[i * 4], g = data[i * 4 + 1], b = data[i * 4 + 2]
              const v = 0.299 * r + 0.587 * g + 0.114 * b
              gray[i] = v
              sum += v
            }
            const brightness = sum / (N * N)

            // 4 邻域拉普拉斯方差（衡量清晰度）
            let m = 0, m2 = 0, cnt = 0
            for (let y = 1; y < N - 1; y++) {
              for (let x = 1; x < N - 1; x++) {
                const i = y * N + x
                const lap = 4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - N] - gray[i + N]
                m += lap
                m2 += lap * lap
                cnt += 1
              }
            }
            m /= cnt
            const blur = m2 / cnt - m * m

            const issues = []
            if (brightness < 45) issues.push('偏暗')
            if (brightness > 225) issues.push('过曝')
            if (blur < 18) issues.push('偏糊')
            resolve({
              ok: issues.length === 0,
              brightness: Math.round(brightness),
              blur: Math.round(blur),
              note: issues.join('、'),
            })
          } catch (e) {
            resolve({ ok: true, brightness: 128, blur: 999, note: '' })
          }
        }
        img.onerror = () => resolve({ ok: true, brightness: 128, blur: 999, note: '' })
        img.src = tempPath
      })
    })
  },

  // 选完图后跑一次质检，不合格给"重拍/仍然使用"两个选择
  applyPickedImage(tempPath) {
    this.setData({ imagePath: tempPath, result: '', classIndex: -1, showResult: false, batchResults: [], showWhy: false })
    this.checkQuality(tempPath).then((q) => {
      this.setData({ qcNote: q.ok ? '' : q.note })
      if (q.ok) return
      wx.showModal({
        title: '图片质量可能影响结果',
        content: `检测到图片${q.note}（亮度 ${q.brightness}，清晰度 ${q.blur}）。建议重拍：让样本占满取景框、避免阴影和反光。`,
        confirmText: '仍然使用',
        cancelText: '重新选择',
        success: (r) => {
          if (!r.confirm) this.setData({ imagePath: '' })
        },
      })
    })
  },

  toggleWhy() {
    this.setData({ showWhy: !this.data.showWhy })
  },

  // 数字滚动：把结果从 0 平滑加到目标值（纯前端动画）
  countUp(target, key = 'confidenceShown', duration = 720) {
    if (this._cuTimer) clearInterval(this._cuTimer)
    const steps = 20
    const end = Number(target) || 0
    let i = 0
    this.setData({ [key]: 0 })
    this._cuTimer = setInterval(() => {
      i += 1
      const v = i >= steps ? end : Math.round((end * i / steps) * 10) / 10
      this.setData({ [key]: v })
      if (i >= steps) {
        clearInterval(this._cuTimer)
        this._cuTimer = null
      }
    }, Math.max(16, Math.round(duration / steps)))
  },

  // 端上三态判定：只看模型给出的三类概率，不引入额外模型
  // 规则（阈值写在这里，便于结题时说明口径）：
  //   clear  : 最高类概率 ≥ 90% 且与次高类差 ≥ 40 个点 → 可直接采信
  //   review : 最高类概率 ≥ 60%                        → 建议人工复检
  //   unknown: 其余（含最高类概率 < 60%）               → 无法判断，请重拍
  decide(data) {
    // 二分类判定：以 p(mold) 与阈值的关系定调，阈值附近为灰区
    const saved = wx.getStorageSync('judge_thresholds') || {}
    const THR = Number(saved.threshold) || Number(data.threshold) || 0.6823
    const MARGIN = Number(saved.marginP) || 0.15
    this.setData({ thThreshold: THR, thMarginP: MARGIN, thrPct: +(THR * 100).toFixed(1) })

    const p = Number(data.p_mold)
    const dist = Math.abs(p - THR)
    const isMold = p >= THR
    const pPct = +(p * 100).toFixed(1)
    const thrPct = +(THR * 100).toFixed(1)
    const distPct = +(dist * 100).toFixed(1)
    const marginPct = +(MARGIN * 100).toFixed(1)

    let tone = 'unknown'
    let label = '处于判定灰区，建议复检'
    let hint = 'p(mold) ' + pPct + '% 与阈值 ' + thrPct + '% 仅差 ' + distPct + ' 个百分点。'
    if (dist >= MARGIN) {
      tone = 'clear'
      label = '结果可信'
      hint = 'p(mold) ' + pPct + '%，距阈值 ' + distPct + ' 个百分点（≥' + marginPct + '）。'
    } else if (dist >= MARGIN / 2) {
      tone = 'review'
      label = '建议人工复检'
      hint = 'p(mold) ' + pPct + '%，距阈值仅 ' + distPct + ' 个百分点，建议换一张更清晰的图复核。'
    }

    let rule = ''
    if (tone === 'clear') {
      rule = `p(mold) ${pPct}% ${isMold ? '≥' : '<'} 阈值 ${thrPct}%，且相距 ${distPct} ≥ ${marginPct} → 判为「结果可信」`
    } else if (tone === 'review') {
      rule = `p(mold) ${pPct}% 与阈值 ${thrPct}% 相距 ${distPct}，落在灰区（<${marginPct}）→ 判为「建议人工复检」`
    } else {
      rule = `p(mold) ${pPct}% 与阈值 ${thrPct}% 相距仅 ${distPct} → 判为「灰区，建议复检」`
    }
    return { tone, label, hint, rule, margin: distPct, top: pPct, isMold }
  },

  // 单张检测
  detect() {
    if (!this.data.imagePath) {
      wx.showToast({ title: '请先选择图片', icon: 'none' })
      return
    }
    if (this.data.serverOnline === false) {
      wx.showToast({ title: '后端未连接，请先启动服务', icon: 'none', duration: 2500 })
      return
    }

    this.setData({ isDetecting: true, showResult: false })
    this.startSteps()

    wx.uploadFile({
      url: DETECT_URL,
      filePath: this.data.imagePath,
      name: 'image',
      timeout: CONFIG.REQUEST_TIMEOUT,
      success: (res) => {
        try {
          const data = this.parseResponse(res)
          const isMoldy = Number(data.class_index) > 0 || data.result === '发霉'
          const d = this.decide(data)
          this.setData({
            result: data.result || (data.class_index > 0 ? '发霉' : '正常'),
            classIndex: data.class_index,
            confidence: data.confidence,
            moldyProb: +(Number(data.p_mold) * 100).toFixed(1),
            normalProb: +(Number(1 - Number(data.p_mold)) * 100).toFixed(1),
            moldProb: +(Number(data.p_mold) * 100).toFixed(1),
            decision: d.label,
            decisionTone: d.tone,
            decisionRule: d.rule,
            showWhy: false,
            decisionHint: d.hint,
            margin: d.margin,
            detectTime: new Date().toLocaleString()
          })
          this.setData({
            neighbors: data.neighbors || [],
            anomalyScore: data.anomaly_score || 0,
            refCount: data.ref_count || 0,
            elapsedMs: data.elapsed_ms || 0,
          })
          this.countUp(data.confidence)
          setTimeout(() => this.setData({ showResult: true }), 100)
          this.saveHistory({
            status: isMoldy ? 'moldy' : 'normal',
            classIndex: data.class_index,
            result: data.result || CLASS_NAMES[data.class_index] || '',
            confidence: data.confidence,
            decision: d.label,
            imagePath: this.data.imagePath
          })
          this.setData({ serverOnline: true })
          this.loadLast()
        } catch (e) {
          wx.showToast({ title: e.message || '检测失败', icon: 'none', duration: 3000 })
        }
      },
      fail: (err) => {
        console.error('上传失败:', err)
        const timedOut = (err.errMsg || '').indexOf('timeout') >= 0
        this.setData({ serverOnline: false })
        wx.showToast({
          title: timedOut ? '请求超时，请确认后端已启动' : '网络错误，请检查后端地址',
          icon: 'none',
          duration: 3000
        })
      },
      complete: () => {
        this.stopSteps()
        this.setData({ isDetecting: false })
      }
    })
  },

  // 批量检测（最多 9 张）
  batchDetect() {
    if (this.data.serverOnline === false) {
      wx.showToast({ title: '后端未连接，请先启动服务', icon: 'none', duration: 2500 })
      return
    }
    wx.chooseMedia({
      count: 9,
      mediaType: ['image'],
      success: (res) => {
        const files = res.tempFiles
        this.setData({ isDetecting: true, batchResults: [], result: '', classIndex: -1, showResult: false })
        this.startSteps()

        let completed = 0
        let failed = 0
        const results = []

        files.forEach((file, index) => {
          wx.uploadFile({
            url: DETECT_URL,
            filePath: file.tempFilePath,
            name: 'image',
            timeout: CONFIG.REQUEST_TIMEOUT,
            success: (uploadRes) => {
              try {
                const data = this.parseResponse(uploadRes)
                results[index] = {
                  imagePath: file.tempFilePath,
                  result: data.result,
                  classIndex: data.class_index,
                  confidence: data.confidence,
                  failed: false
                }
                this.saveHistory({
                  status: data.class_index > 0 ? 'moldy' : 'normal',
                  classIndex: data.class_index,
                  result: data.result,
                  confidence: data.confidence,
                  imagePath: file.tempFilePath
                })
              } catch (e) {
                failed++
                results[index] = {
                  imagePath: file.tempFilePath,
                  result: '检测失败',
                  classIndex: -1,
                  confidence: 0,
                  failed: true
                }
              }
            },
            fail: () => {
              failed++
              results[index] = {
                imagePath: file.tempFilePath,
                result: '检测失败',
                classIndex: -1,
                confidence: 0,
                failed: true
              }
            },
            complete: () => {
              completed++
              if (completed === files.length) {
                const ok = results.filter((r) => r && !r.failed)
                // 风险优先：霉变在前，同类按置信度从高到低
                ok.sort((a, b) => {
                  if (b.classIndex !== a.classIndex) return b.classIndex - a.classIndex
                  return b.confidence - a.confidence
                })
                const mold = ok.filter((r) => r.classIndex > 0).length
                const avg = ok.length
                  ? (ok.reduce((s2, r) => s2 + r.confidence, 0) / ok.length).toFixed(1)
                  : '0.0'
                this.stopSteps()
                this.setData({
                  isDetecting: false,
                  batchResults: results.filter((r) => r),
                  batchTotal: files.length,
                  batchOkCount: ok.length - mold,
                  batchMoldCount: mold,
                  batchFailCount: files.length - ok.length,
                  batchAvgConf: avg
                })
                this.loadLast()
                wx.showToast({
                  title: failed ? `完成，${failed} 张失败` : `完成 ${files.length} 张检测`,
                  icon: failed ? 'none' : 'success'
                })
              }
            }
          })
        })
      }
    })
  },

  // 把临时图片复制到用户目录，返回持久路径（失败则原样返回）
  persistImage(tempPath) {
    return new Promise((resolve) => {
      if (!tempPath) return resolve('')
      // 已经是持久路径就不用再复制
      if (tempPath.indexOf(wx.env.USER_DATA_PATH) === 0) return resolve(tempPath)
      const fs = wx.getFileSystemManager()
      const ext = (tempPath.match(/\.(jpg|jpeg|png|webp)$/i) || ['.jpg'])[0]
      const dest = `${wx.env.USER_DATA_PATH}/h_${Date.now()}_${Math.floor(Math.random() * 1e4)}${ext}`
      fs.copyFile({
        srcPath: tempPath,
        destPath: dest,
        success: () => resolve(dest),
        fail: () => resolve(tempPath),
      })
    })
  },

  saveHistory(data) {
    const record = Object.assign({}, data, { time: new Date().toLocaleString() })
    let history = wx.getStorageSync('history') || []
    history.unshift(record)
    history = history.slice(0, 100)
    wx.setStorageSync('history', history)

    // 异步把图片转成持久路径，再回写同一条记录
    if (record.imagePath) {
      this.persistImage(record.imagePath).then((saved) => {
        if (!saved || saved === record.imagePath) return
        const list = wx.getStorageSync('history') || []
        const idx = list.findIndex((i) => i.time === record.time && i.confidence === record.confidence)
        if (idx >= 0) {
          list[idx].imagePath = saved
          wx.setStorageSync('history', list)
        }
      })
    }
  },

  goHistory() {
    wx.switchTab({ url: '/pages/history/history' })
  },

  noop() {},

  closePoster() {
    this.setData({ showPoster: false })
  },

  // 把当前判定画成一张 600×960 的海报（canvas 2d）
  makePoster() {
    if (!this.data.result) return
    this.setData({ showPoster: true, posterPath: '' })

    const d = this.data
    const color = d.classIndex === 0 ? '#58c9a3' : (d.classIndex === 1 ? '#f0b429' : '#ff7a63')
    const sub = d.decisionTone === 'unknown'
      ? '信心不足，建议重拍后再判'
      : (d.classIndex === 0 ? '未检出可见霉变' : '检出可见霉变')

    wx.createSelectorQuery().in(this).select('#posterCanvas').fields({ node: true, size: true }).exec((res) => {
      const node = res && res[0] && res[0].node
      if (!node) {
        wx.showToast({ title: '画布初始化失败', icon: 'none' })
        return
      }
      const W = 600
      const H = 780
      let dpr = 2
      try {
        dpr = wx.getSystemInfoSync().pixelRatio || 2
      } catch (e) {}
      const canvas = node
      canvas.width = W * dpr
      canvas.height = H * dpr
      const ctx = canvas.getContext('2d')
      ctx.scale(dpr, dpr)

      // 背景
      const bg = ctx.createLinearGradient(0, 0, 0, H)
      bg.addColorStop(0, '#191510')
      bg.addColorStop(0.55, '#12100d')
      bg.addColorStop(1, '#0c0a08')
      ctx.fillStyle = bg
      ctx.fillRect(0, 0, W, H)

      const glow = ctx.createRadialGradient(120, 60, 20, 120, 60, 380)
      glow.addColorStop(0, 'rgba(224,187,122,0.22)')
      glow.addColorStop(1, 'rgba(224,187,122,0)')
      ctx.fillStyle = glow
      ctx.fillRect(0, 0, W, H)

      // 品牌
      ctx.fillStyle = '#e0bb7a'
      ctx.fillRect(40, 48, 42, 42)
      ctx.fillStyle = '#241a10'
      ctx.fillRect(54, 57, 14, 24)
      ctx.fillStyle = '#e6d6bd'
      ctx.font = 'bold 22px sans-serif'
      ctx.fillText('NUT·SCAN', 96, 72)
      ctx.fillStyle = '#a2937f'
      ctx.font = '15px sans-serif'
      ctx.fillText('花生霉变视觉筛查', 96, 96)

      // 判定
      ctx.fillStyle = '#8c7f6d'
      ctx.font = '16px sans-serif'
      ctx.fillText('判定结果', 40, 190)
      ctx.fillStyle = color
      ctx.font = 'bold 62px sans-serif'
      ctx.fillText(d.result, 40, 258)
      ctx.fillStyle = '#a2937f'
      ctx.font = '19px sans-serif'
      ctx.fillText(sub, 40, 296)

      // 置信度环
      const cx = 470
      const cy = 216
      const r = 62
      ctx.lineWidth = 12
      ctx.strokeStyle = 'rgba(255,255,255,0.08)'
      ctx.beginPath()
      ctx.arc(cx, cy, r, 0, Math.PI * 2)
      ctx.stroke()
      ctx.strokeStyle = color
      ctx.beginPath()
      ctx.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * (Number(d.confidence) / 100))
      ctx.stroke()
      ctx.textAlign = 'center'
      ctx.fillStyle = '#f7eddc'
      ctx.font = 'bold 34px sans-serif'
      ctx.fillText(d.confidence + '%', cx, cy + 12)
      ctx.textAlign = 'left'

      // 海报上的概率行（二分类）
      const rows = [
        { k: '正常', v: d.normalProb, c: '#58c9a3' },
        { k: '发霉概率', v: d.moldyProb, c: '#f0b429' },
        
      ]
      let y = 360
      rows.forEach((row) => {
        ctx.fillStyle = '#8c7f6d'
        ctx.font = '17px sans-serif'
        ctx.fillText(row.k, 40, y + 6)
        const x0 = 170
        const wid = 320
        ctx.fillStyle = 'rgba(255,255,255,0.07)'
        ctx.fillRect(x0, y - 7, wid, 12)
        ctx.fillStyle = row.c
        ctx.fillRect(x0, y - 7, Math.max(2, wid * (Number(row.v) / 100)), 12)
        ctx.fillStyle = '#e8dbc6'
        ctx.font = '17px sans-serif'
        ctx.textAlign = 'right'
        ctx.fillText(row.v + '%', 560, y + 6)
        ctx.textAlign = 'left'
        y += 52
      })

      // 结论条
      const toneColor = d.decisionTone === 'clear' ? '#58c9a3' : (d.decisionTone === 'review' ? '#f0b429' : '#ff7a63')
      ctx.fillStyle = 'rgba(255,255,255,0.05)'
      ctx.fillRect(40, 512, 520, 96)
      ctx.fillStyle = toneColor
      ctx.fillRect(40, 512, 6, 96)
      ctx.fillStyle = toneColor
      ctx.font = 'bold 24px sans-serif'
      ctx.fillText(d.decision || '—', 66, 550)
      ctx.fillStyle = '#a2937f'
      ctx.font = '15px sans-serif'
      const hint = d.decisionHint || ''
      const line1 = hint.slice(0, 34)
      const line2 = hint.slice(34, 68)
      ctx.fillText(line1, 66, 576)
      if (line2) ctx.fillText(line2, 66, 598)

      // 免责
      ctx.fillStyle = '#6f6557'
      ctx.font = '14px sans-serif'
      ctx.fillText('判定依据为图像可见霉变表型，不是黄曲霉毒素含量测定。', 40, 650)
      ctx.fillText('毒素定量需按 GB 2761 用液相色谱等方法完成。', 40, 672)

      // 页脚
      ctx.fillStyle = '#4e463b'
      ctx.font = '14px sans-serif'
      ctx.fillText('新苗项目 · ' + (d.detectTime || ''), 40, 716)
      ctx.fillStyle = '#3c352c'
      ctx.fillText('科研演示，不作为食品安全结论', 40, 740)

      wx.canvasToTempFilePath({
        canvas,
        x: 0,
        y: 0,
        width: W,
        height: H,
        destWidth: W * 2,
        destHeight: H * 2,
        success: (r) => this.setData({ posterPath: r.tempFilePath }),
        fail: (e) => {
          console.error('canvasToTempFilePath 失败', e)
          wx.showToast({ title: '海报生成失败', icon: 'none' })
        },
      }, this)
    })
  },

  savePoster() {
    if (!this.data.posterPath) {
      wx.showToast({ title: '海报还没生成好', icon: 'none' })
      return
    }
    wx.saveImageToPhotosAlbum({
      filePath: this.data.posterPath,
      success: () => wx.showToast({ title: '已保存到相册', icon: 'success' }),
      fail: (e) => {
        const msg = (e && e.errMsg) || ''
        if (msg.indexOf('auth') >= 0 || msg.indexOf('deny') >= 0) {
          wx.showModal({
            title: '需要相册权限',
            content: '请在设置里允许保存到相册，再试一次。',
            confirmText: '去设置',
            success: (r) => { if (r.confirm) wx.openSetting() },
          })
        } else {
          wx.showToast({ title: '保存失败', icon: 'none' })
        }
      },
    })
  },

  onShareAppMessage() {
    const title = this.data.result
      ? `坚果检测结果：${this.data.result}，置信度 ${this.data.confidence}%`
      : '花生黄曲霉视觉检测小程序'
    return { title, path: '/pages/index/index' }
  },

  // 从本地记录里取最近一条，以及今天的检测次数
  loadLast() {
    const list = wx.getStorageSync('history') || []
    if (!list.length) {
      this.setData({ lastResult: '', todayCount: 0 })
      return
    }
    const last = list[0]
    const today = new Date()
    const pad = (n) => (n < 10 ? '0' + n : '' + n)
    const todayKey = `${today.getFullYear()}/${pad(today.getMonth() + 1)}/${pad(today.getDate())}`
    const todayCount = list.filter((i) => String(i.time || '').indexOf(todayKey) === 0).length

    this.setData({
      lastResult: last.result || (last.status === 'moldy' ? '霉变' : '正常'),
      lastTime: last.time || '',
      lastConfidence: last.confidence || 0,
      lastTone: last.status === 'moldy' ? 't-risk' : 't-ok',
      todayCount
    })
  },

  goHistory() {
    wx.switchTab({ url: '/pages/history/history' })
  },

  onShow() {
    this.loadLast()
    // 从后台切回来时刷新一次连通状态
    if (this.data.serverOnline === false) {
      this.checkBackend()
    }
  },

  onPullDownRefresh() {
    this.checkBackend()
    wx.stopPullDownRefresh()
  }
})
